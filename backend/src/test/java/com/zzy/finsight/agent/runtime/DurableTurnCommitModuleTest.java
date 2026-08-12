package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.AgentStateStore;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
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
                runtimeMapper, snapshotMapper, taskMapper, stateStore, mock(FinancialReportFingerprinter.class)
        );
        AgentState state = state();
        LeaseToken lease = new LeaseToken(11L, "runner", 3L);
        ToolResult result = ToolResult.success("完成", Map.of("ready", true));
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
                mock(FinancialReportFingerprinter.class)
        );
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

    private AgentState state() {
        AgentState state = new AgentState();
        state.setTaskId(11L);
        state.setThreadId("thread-1");
        state.setContextHash("context-hash");
        state.setTurnNo(2);
        return state;
    }
}
