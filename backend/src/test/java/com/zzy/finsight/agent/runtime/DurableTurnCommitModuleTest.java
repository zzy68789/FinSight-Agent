package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.AgentStateStore;
import com.zzy.finsight.agent.event.AgentEventDraft;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.PlannerOutput;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.agent.tool.ToolPayload;
import com.zzy.finsight.domain.AgentPlannerCallRecord;
import com.zzy.finsight.domain.AgentTurnRecord;
import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.AgentPlannerCallMapper;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.mapper.ReportMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DurableTurnCommitModuleTest {

    @Test
    void commitsToolJournalTurnCheckpointAndLeaseThroughOneModule() {
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        FinancialSnapshotMapper snapshotMapper = mock(FinancialSnapshotMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        AgentStateStore stateStore = mock(AgentStateStore.class);
        DurableTurnCommitModule module = new DurableTurnCommitModule(
                runtimeMapper,
                snapshotMapper,
                taskMapper,
                stateStore,
                mock(FinancialReportFingerprinter.class),
                mock(AgentEventOutboxMapper.class),
                mock(AgentStepLogMapper.class),
                mock(ReportMapper.class),
                mock(AgentPlannerCallMapper.class),
                objectMapper()
        );
        AgentState state = state();
        LeaseToken lease = new LeaseToken(11L, "runner", 3L);
        ToolResult result = ToolResult.success(
                "完成", new ToolPayload.Coverage(java.math.BigDecimal.ONE, 3, List.of(), true)
        );
        when(taskMapper.findActiveLeaseEpoch(11L, "runner")).thenReturn(java.util.Optional.of(3L));
        when(runtimeMapper.completeTurn(31L, "完成", "SUCCESS", 8L)).thenReturn(1);
        when(taskMapper.updateAgentProgressFenced(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(true);

        module.commitTurn(new AgentTurnCommit(
                7L,
                31L,
                state,
                "完成",
                "SUCCESS",
                8L,
                List.of(new CommittedToolCall(
                        41L, new ToolInvocation("check_evidence_coverage", Map.of()), result, 5L
                ))
        ), lease);

        InOrder ordered = inOrder(runtimeMapper, stateStore, taskMapper);
        ordered.verify(runtimeMapper).completeToolCall(
                anyLong(), any(), anyString(), anyLong(), anyString(), any(), any()
        );
        ordered.verify(runtimeMapper).completeTurn(31L, "完成", "SUCCESS", 8L);
        ordered.verify(stateStore).save(state);
        ordered.verify(taskMapper).updateAgentProgressFenced(any(), anyString(), anyInt(), anyInt(), any());
    }

    @Test
    void rejectsCommitWhenLeaseEpochHasBeenFenced() {
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        DurableTurnCommitModule module = new DurableTurnCommitModule(
                runtimeMapper,
                mock(FinancialSnapshotMapper.class),
                taskMapper,
                mock(AgentStateStore.class),
                mock(FinancialReportFingerprinter.class),
                mock(AgentEventOutboxMapper.class),
                mock(AgentStepLogMapper.class),
                mock(ReportMapper.class),
                mock(AgentPlannerCallMapper.class),
                objectMapper()
        );
        when(taskMapper.findActiveLeaseEpoch(11L, "stale-runner"))
                .thenReturn(java.util.Optional.of(2L));
        when(runtimeMapper.completeTurn(anyLong(), anyString(), anyString(), anyLong())).thenReturn(1);
        when(taskMapper.updateAgentProgressFenced(any(), anyString(), anyInt(), anyInt(), any()))
                .thenReturn(false);

        assertThatThrownBy(() -> module.commitTurn(
                new AgentTurnCommit(7L, 31L, state(), "", "SUCCESS", 1L, List.of()),
                new LeaseToken(11L, "stale-runner", 2L)
        ))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("LEASE_FENCED");
    }

    @Test
    void fencesTurnAndToolJournalBeforeAnyWrite() {
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        DurableTurnCommitModule module = module(runtimeMapper, taskMapper, mock(AgentStateStore.class));
        when(taskMapper.findActiveLeaseEpoch(11L, "stale-runner"))
                .thenReturn(java.util.Optional.of(4L));
        LeaseToken stale = new LeaseToken(11L, "stale-runner", 3L);

        assertThatThrownBy(() -> module.openTurn(
                state(), stale, output(AgentAction.synthesize("生成报告")), true
        )).hasMessageContaining("LEASE_FENCED");
        assertThatThrownBy(() -> module.journalToolStart(
                state(), stale, 31L, "call-1", "resolve_security", "hash", Map.of(), 1
        )).hasMessageContaining("LEASE_FENCED");

        org.mockito.Mockito.verifyNoInteractions(runtimeMapper);
    }

    @Test
    void usesLeaseFencedInsertForTurnAndToolJournal() {
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        AgentPlannerCallMapper plannerMapper = mock(AgentPlannerCallMapper.class);
        DurableTurnCommitModule module = new DurableTurnCommitModule(
                runtimeMapper,
                mock(FinancialSnapshotMapper.class),
                taskMapper,
                mock(AgentStateStore.class),
                mock(FinancialReportFingerprinter.class),
                mock(AgentEventOutboxMapper.class),
                mock(AgentStepLogMapper.class),
                mock(ReportMapper.class),
                plannerMapper,
                objectMapper()
        );
        LeaseToken lease = new LeaseToken(11L, "runner", 3L);
        when(taskMapper.findActiveLeaseEpoch(11L, "runner")).thenReturn(java.util.Optional.of(3L));
        when(taskMapper.lockActiveLeaseEpoch(11L, "runner", 3L))
                .thenReturn(java.util.Optional.of(3L));
        when(runtimeMapper.findTurn(11L, 2)).thenReturn(java.util.Optional.empty());
        when(runtimeMapper.saveTurnFenced(
                eq(lease), eq(2), anyString(), anyString(), any(), anyInt(), anyInt(), anyLong()
        )).thenReturn(31L);
        when(runtimeMapper.startToolCallFenced(
                eq(lease), eq(31L), anyString(), anyString(), anyString(), any(), eq(1)
        )).thenReturn(41L);

        long turnId = module.openTurn(state(), lease, output(AgentAction.synthesize("生成报告")), true);
        long callId = module.journalToolStart(
                state(), lease, turnId, "call-1", "resolve_security", "hash", Map.of(), 1
        );

        assertThat(turnId).isEqualTo(31L);
        assertThat(callId).isEqualTo(41L);
        verify(runtimeMapper).saveTurnFenced(
                eq(lease), eq(2), anyString(), eq("SYNTHESIZE"), any(), eq(1), eq(1), eq(1L)
        );
        verify(plannerMapper).saveForTurn(eq(11L), eq(31L), any(), eq(true));
        verify(runtimeMapper).startToolCallFenced(
                eq(lease), eq(31L), eq("call-1"), eq("resolve_security"), eq("hash"), eq(Map.of()), eq(1)
        );
    }

    @Test
    void atomicallyCompletesReportSnapshotCheckpointTaskAndOutbox() {
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        FinancialSnapshotMapper snapshotMapper = mock(FinancialSnapshotMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        AgentStateStore stateStore = mock(AgentStateStore.class);
        AgentEventOutboxMapper eventMapper = mock(AgentEventOutboxMapper.class);
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        ReportMapper reportMapper = mock(ReportMapper.class);
        DurableTurnCommitModule module = new DurableTurnCommitModule(
                runtimeMapper, snapshotMapper, taskMapper, stateStore,
                mock(FinancialReportFingerprinter.class), eventMapper, stepLogMapper, reportMapper,
                mock(AgentPlannerCallMapper.class), objectMapper()
        );
        AgentState state = state();
        state.setSnapshotId(21L);
        state.setSnapshot(new com.zzy.finsight.domain.stock.FinancialSnapshot(
                new com.zzy.finsight.domain.stock.StockSubject(
                        "600519", "SH", "600519.SH", "贵州茅台", "食品饮料"
                ),
                "2026-08-12", "hybrid", List.of(), java.time.LocalDateTime.now()
        ));
        LeaseToken lease = new LeaseToken(11L, "runner", 3L);
        when(taskMapper.findActiveLeaseEpoch(11L, "runner")).thenReturn(java.util.Optional.of(3L));
        when(runtimeMapper.completeTurn(31L, "通过门禁", "SUCCESS", 8L)).thenReturn(1);
        when(reportMapper.save(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString(), any()
        )).thenReturn(99L);
        when(taskMapper.nextEventSequence(lease)).thenReturn(7L);
        when(eventMapper.insert(any())).thenReturn(51L);
        when(taskMapper.finishAgentFenced(lease, "COMPLETED", "COMPLETED", null)).thenReturn(true);

        CompletedRunCommitResult result = module.commitCompletedRun(
                new AgentTurnCommit(7L, 31L, state, "通过门禁", "SUCCESS", 8L, List.of()),
                lease,
                new FinalReportCommit("# 报告", "PASS", "snapshot-hash", "context-hash", null),
                new AgentEventDraft("run_completed", Map.of("taskId", 11L), 0L, "SUCCESS", "")
        );

        assertThat(result.reportId()).isEqualTo(99L);
        assertThat(result.events()).singleElement().satisfies(event -> {
            assertThat(event.sequence()).isEqualTo(7L);
            assertThat(event.payload()).containsEntry("reportId", 99L).containsEntry("finalReport", "# 报告");
        });
        InOrder ordered = inOrder(runtimeMapper, reportMapper, snapshotMapper, stateStore, eventMapper, taskMapper);
        ordered.verify(runtimeMapper).completeTurn(31L, "通过门禁", "SUCCESS", 8L);
        ordered.verify(reportMapper).save(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString(), any()
        );
        ordered.verify(snapshotMapper).updateSnapshot(
                eq(21L), any(), eq("snapshot-hash"), eq("FROZEN"), any()
        );
        ordered.verify(stateStore).save(state);
        ordered.verify(eventMapper).insert(any());
        ordered.verify(taskMapper).finishAgentFenced(lease, "COMPLETED", "COMPLETED", null);
    }

    private DurableTurnCommitModule module(
            AgentRuntimeMapper runtimeMapper,
            ResearchTaskMapper taskMapper,
            AgentStateStore stateStore
    ) {
        return new DurableTurnCommitModule(
                runtimeMapper,
                mock(FinancialSnapshotMapper.class),
                taskMapper,
                stateStore,
                mock(FinancialReportFingerprinter.class),
                mock(AgentEventOutboxMapper.class),
                mock(AgentStepLogMapper.class),
                mock(ReportMapper.class),
                mock(AgentPlannerCallMapper.class),
                objectMapper()
        );
    }

    @Test
    void resumesPersistedPendingTurnWithoutChangingPlannerAction() throws Exception {
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        AgentPlannerCallMapper plannerMapper = mock(AgentPlannerCallMapper.class);
        com.fasterxml.jackson.databind.ObjectMapper objectMapper = objectMapper();
        DurableTurnCommitModule module = new DurableTurnCommitModule(
                runtimeMapper,
                mock(FinancialSnapshotMapper.class),
                taskMapper,
                mock(AgentStateStore.class),
                mock(FinancialReportFingerprinter.class),
                mock(AgentEventOutboxMapper.class),
                mock(AgentStepLogMapper.class),
                mock(ReportMapper.class),
                plannerMapper,
                objectMapper
        );
        AgentState state = state();
        LeaseToken lease = new LeaseToken(11L, "runner", 3L);
        AgentAction persisted = new AgentAction(
                com.zzy.finsight.agent.planning.AgentActionType.CALL_TOOL,
                List.of(new ToolInvocation("search_public_evidence", Map.of("query", "毛利率变化"))),
                "补充原因证据"
        );
        AgentTurnRecord turn = new AgentTurnRecord(
                31L, 11L, 3, "PLANNING", "CALL_TOOL", objectMapper.writeValueAsString(persisted),
                "", "PLANNED", 10, 5, 20L, java.time.LocalDateTime.now()
        );
        AgentPlannerCallRecord decision = new AgentPlannerCallRecord(
                51L, 11L, 31L, "NEXT_ACTION", "FAST", "fast-model",
                10, 5, 20L, 1, true, true, false, "", java.time.LocalDateTime.now()
        );
        when(taskMapper.findActiveLeaseEpoch(11L, "runner")).thenReturn(java.util.Optional.of(3L));
        when(runtimeMapper.findTurn(11L, 3)).thenReturn(java.util.Optional.of(turn));
        when(plannerMapper.findByTurnId(31L)).thenReturn(java.util.Optional.of(decision));

        PendingTurnDecision pending = module.resumePendingTurn(state, lease).orElseThrow();

        assertThat(pending.turnId()).isEqualTo(31L);
        assertThat(pending.turnNo()).isEqualTo(3);
        assertThat(pending.output().value()).isEqualTo(persisted);
        assertThat(pending.output().actualModel()).isEqualTo("fast-model");
        assertThat(pending.routeCorrect()).isTrue();
    }

    private PlannerOutput<AgentAction> output(AgentAction action) {
        return new PlannerOutput<>(
                action, false, "", 1, 1, 1L,
                "NEXT_ACTION", "FAST", "fast-model", 1, true
        );
    }

    private com.fasterxml.jackson.databind.ObjectMapper objectMapper() {
        return new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();
    }

    private AgentState state() {
        AgentState state = new AgentState();
        state.setTaskId(11L);
        state.setThreadId("thread-1");
        state.setContextHash("context-hash");
        state.setTurnNo(2);
        return state;
    }
}
