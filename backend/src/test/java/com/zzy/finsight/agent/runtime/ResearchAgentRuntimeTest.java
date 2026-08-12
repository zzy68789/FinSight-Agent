package com.zzy.finsight.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.AgentActionType;
import com.zzy.finsight.agent.planning.PlannerOutput;
import com.zzy.finsight.agent.planning.ResearchPlan;
import com.zzy.finsight.agent.planning.ResearchPlanner;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.agent.tool.ResearchTool;
import com.zzy.finsight.agent.tool.ToolContext;
import com.zzy.finsight.agent.tool.ToolPolicyGuard;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.mapper.AgentRuntimeMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.ReportService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchAgentRuntimeTest {
    private final ExecutorService toolExecutor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        toolExecutor.shutdownNow();
    }

    @Test
    void persistsExplicitStopWhenPlannerJudgesEvidenceInsufficient() {
        ResearchPlanner planner = mock(ResearchPlanner.class);
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of());
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
        ResearchPlan plan = new ResearchPlan(
                "判断盈利质量",
                List.of("利润由主营业务驱动"),
                List.of("财务报表"),
                List.of(),
                List.of("缺少财务报表"),
                "LLM",
                ResearchPlanner.PLANNER_VERSION
        );
        when(planner.createPlan(any(), any())).thenReturn(
                new PlannerOutput<>(plan, false, "", 20, 10, 3L)
        );
        when(planner.nextAction(any(), any())).thenReturn(
                new PlannerOutput<>(
                        new AgentAction(
                                AgentActionType.STOP_INSUFFICIENT_EVIDENCE,
                                List.of(),
                                "公开证据不足，停止生成结论"
                        ),
                        false,
                        "",
                        10,
                        5,
                        2L
                )
        );
        when(runtimeMapper.saveTurn(
                anyLong(), anyInt(), anyString(), anyString(), any(), anyString(), anyString(),
                anyInt(), anyInt(), anyLong()
        )).thenReturn(31L);

        ResearchAgentRuntime runtime = new ResearchAgentRuntime(
                planner,
                registry,
                mock(ToolPolicyGuard.class),
                new AgentBudgetGuard(
                        8, 12, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
                ),
                runtimeMapper,
                stepLogMapper,
                checkpointMapper,
                taskMapper,
                mock(FinancialSnapshotMapper.class),
                runtimeStateService,
                mock(InvestmentReportWriter.class),
                mock(CitationReviewer.class),
                mock(FinancialComplianceReviewer.class),
                mock(FinancialEvaluator.class),
                mock(FinancialReportFingerprinter.class),
                mock(ReportService.class),
                toolExecutor
        );
        AgentState state = state();
        AtomicBoolean done = new AtomicBoolean();

        ResearchAgentRuntime.RuntimeOutcome outcome = runtime.execute(
                7L,
                state,
                new AgentBudget(8, 12, 3, 4, 2, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                "runner-1",
                listener(done)
        );

        assertThat(outcome.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(outcome.reason()).contains("公开证据不足");
        assertThat(state.getTurnNo()).isEqualTo(1);
        assertThat(done).isTrue();
        verify(runtimeMapper).completeTurn(
                eq(31L), eq("公开证据不足，停止生成结论"), eq("DEGRADED"), anyLong()
        );
        verify(taskMapper).finishAgent(
                11L,
                "INSUFFICIENT_EVIDENCE",
                "公开证据不足，停止生成结论",
                null
        );
        verify(runtimeStateService).markStatus(11L, "INSUFFICIENT_EVIDENCE");
    }

    @Test
    void stopsAfterTwoEvidenceTurnsProduceNoNewEffectiveEvidence() {
        ResearchPlanner planner = mock(ResearchPlanner.class);
        ResearchTool noEvidenceTool = new ResearchTool() {
            @Override
            public String name() {
                return "search_public_evidence";
            }

            @Override
            public String description() {
                return "测试无新增证据工具";
            }

            @Override
            public Set<String> allowedArguments() {
                return Set.of("query");
            }

            @Override
            public boolean producesEvidence() {
                return true;
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                return ToolResult.success("未发现新增有效证据", Map.of());
            }
        };
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(noEvidenceTool));
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
        when(planner.createPlan(any(), any())).thenReturn(new PlannerOutput<>(
                new ResearchPlan("补充公告证据", List.of(), List.of("公告"), List.of(), List.of(), "LLM", "v1"),
                false, "", 1, 1, 1L
        ));
        when(planner.nextAction(any(), any())).thenReturn(
                toolAction("第一次检索"),
                toolAction("第二次检索")
        );
        when(runtimeMapper.saveTurn(
                anyLong(), anyInt(), anyString(), anyString(), any(), anyString(), anyString(),
                anyInt(), anyInt(), anyLong()
        )).thenReturn(31L, 32L);
        when(runtimeMapper.startToolCall(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), any(), anyInt()
        )).thenReturn(41L, 42L);
        when(taskMapper.updateAgentProgress(
                anyLong(), anyString(), anyInt(), anyInt(), anyString(), any()
        )).thenReturn(true);
        ResearchAgentRuntime runtime = new ResearchAgentRuntime(
                planner,
                registry,
                new ToolPolicyGuard(registry, new ObjectMapper()),
                new AgentBudgetGuard(
                        8, 12, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
                ),
                runtimeMapper,
                stepLogMapper,
                checkpointMapper,
                taskMapper,
                mock(FinancialSnapshotMapper.class),
                runtimeStateService,
                mock(InvestmentReportWriter.class),
                mock(CitationReviewer.class),
                mock(FinancialComplianceReviewer.class),
                mock(FinancialEvaluator.class),
                mock(FinancialReportFingerprinter.class),
                mock(ReportService.class),
                toolExecutor
        );

        ResearchAgentRuntime.RuntimeOutcome outcome = runtime.execute(
                7L,
                state(),
                new AgentBudget(8, 12, 3, 4, 2, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                "runner-1",
                AgentEventListener.noop()
        );

        assertThat(outcome.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(outcome.reason()).contains("连续 2 轮");
        verify(taskMapper).finishAgent(
                11L,
                "INSUFFICIENT_EVIDENCE",
                "连续 2 轮证据采集未产生新增有效证据",
                null
        );
    }

    private PlannerOutput<AgentAction> toolAction(String query) {
        return new PlannerOutput<>(
                new AgentAction(
                        AgentActionType.CALL_TOOL,
                        List.of(new ToolInvocation("search_public_evidence", Map.of("query", query))),
                        "补充证据"
                ),
                false,
                "",
                1,
                1,
                1L
        );
    }

    private AgentState state() {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion("分析盈利质量");
        AgentState state = new AgentState();
        state.setTaskId(11L);
        state.setThreadId("agent-thread");
        state.setRequest(request);
        state.setContextHash("request-context");
        return state;
    }

    private AgentEventListener listener(AtomicBoolean done) {
        return new AgentEventListener() {
            @Override
            public void onEvent(String eventType, Object data) {
            }

            @Override
            public void onDone() {
                done.set(true);
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };
    }
}
