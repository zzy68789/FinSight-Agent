package com.zzy.finsight.agent.runtime;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.event.AgentEventDraft;
import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.event.AgentEventOutboxPublisher;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.EvidenceMemory;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.AgentActionType;
import com.zzy.finsight.agent.planning.PlannerOutput;
import com.zzy.finsight.agent.planning.PlannerTelemetryModule;
import com.zzy.finsight.agent.planning.ResearchPlan;
import com.zzy.finsight.agent.planning.ResearchPlanner;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.agent.tool.RawToolArguments;
import com.zzy.finsight.agent.tool.ResearchTool;
import com.zzy.finsight.agent.tool.CheckEvidenceCoverageTool;
import com.zzy.finsight.agent.tool.ToolContext;
import com.zzy.finsight.agent.tool.ToolPolicyGuard;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.agent.tool.ToolPayload;
import com.zzy.finsight.agent.tool.ToolStateReducer;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.quality.QualityGateDecisionEngine;
import com.zzy.finsight.agent.quality.QualityGateRoute;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.component.marketdata.FinancialEvidenceValidator;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
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
import java.time.LocalDateTime;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;
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
        DurableTurnCommitModule commitModule = mock(DurableTurnCommitModule.class);
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
        when(commitModule.openTurn(any(), any(), any(), anyInt(), anyInt(), anyLong())).thenReturn(31L);

        ResearchAgentRuntime runtime = new ResearchAgentRuntime(
                planner,
                registry,
                mock(ToolPolicyGuard.class),
                new AgentBudgetGuard(
                        8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
                ),
                commitModule,
                mock(AgentEventOutboxPublisher.class),
                mock(PlannerTelemetryModule.class),
                new ToolStateReducer(new EvidenceMemory(new FinancialEvidenceValidator())),
                new ProgressFingerprint(
                        new ObjectMapper().findAndRegisterModules(),
                        mock(FinancialReportFingerprinter.class)
                ),
                runtimeStateService,
                mock(InvestmentReportWriter.class),
                mock(CitationReviewer.class),
                mock(FinancialComplianceReviewer.class),
                mock(FinancialEvaluator.class),
                new QualityGateDecisionEngine(),
                mock(FinancialReportFingerprinter.class),
                mock(ReportService.class),
                toolExecutor
        );
        AgentState state = state();
        AtomicBoolean done = new AtomicBoolean();

        ResearchAgentRuntime.RuntimeOutcome outcome = runtime.execute(
                7L,
                state,
                new AgentBudget(8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                new LeaseToken(11L, "runner-1", 1L),
                listener(done)
        );

        assertThat(outcome.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(outcome.reason()).contains("公开证据不足");
        assertThat(state.getTurnNo()).isEqualTo(1);
        assertThat(done).isTrue();
        verify(commitModule).commitTurn(any(AgentTurnCommit.class), any(LeaseToken.class));
        verify(commitModule).finishTaskWithEvent(
                eq(state), any(LeaseToken.class), eq("INSUFFICIENT_EVIDENCE"),
                eq("公开证据不足，停止生成结论"), eq(null), any(AgentEventDraft.class)
        );
        verify(runtimeStateService).markStatus(11L, "INSUFFICIENT_EVIDENCE");
    }

    @Test
    void stopsAfterTwoEvidenceTurnsProduceNoNewEffectiveEvidence() {
        ResearchPlanner planner = mock(ResearchPlanner.class);
        ResearchTool<RawToolArguments> noEvidenceTool = new ResearchTool<>() {
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
            public ToolResult execute(ToolContext context, RawToolArguments arguments) {
                return ToolResult.success("未发现新增有效证据");
            }
        };
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(noEvidenceTool));
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        DurableTurnCommitModule commitModule = mock(DurableTurnCommitModule.class);
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
        when(commitModule.openTurn(any(), any(), any(), anyInt(), anyInt(), anyLong())).thenReturn(31L, 32L);
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
                        8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
                ),
                commitModule,
                mock(AgentEventOutboxPublisher.class),
                mock(PlannerTelemetryModule.class),
                new ToolStateReducer(new EvidenceMemory(new FinancialEvidenceValidator())),
                new ProgressFingerprint(
                        new ObjectMapper().findAndRegisterModules(),
                        mock(FinancialReportFingerprinter.class)
                ),
                runtimeStateService,
                mock(InvestmentReportWriter.class),
                mock(CitationReviewer.class),
                mock(FinancialComplianceReviewer.class),
                mock(FinancialEvaluator.class),
                new QualityGateDecisionEngine(),
                mock(FinancialReportFingerprinter.class),
                mock(ReportService.class),
                toolExecutor
        );

        ResearchAgentRuntime.RuntimeOutcome outcome = runtime.execute(
                7L,
                state(),
                new AgentBudget(8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                new LeaseToken(11L, "runner-1", 1L),
                AgentEventListener.noop()
        );

        assertThat(outcome.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(outcome.reason()).contains("连续 2 轮");
        verify(commitModule).finishTaskWithEvent(
                any(), any(), eq("INSUFFICIENT_EVIDENCE"),
                eq("连续 2 轮证据采集未产生新增有效证据"), eq(null), any(AgentEventDraft.class)
        );
    }

    @Test
    void recalculatesDeterministicResultsWhenNumericGateFails() {
        ResearchPlanner planner = mock(ResearchPlanner.class);
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of());
        ToolPolicyGuard policyGuard = new ToolPolicyGuard(registry, new ObjectMapper());
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        DurableTurnCommitModule commitModule = mock(DurableTurnCommitModule.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
        InvestmentReportWriter reportWriter = mock(InvestmentReportWriter.class);
        CitationReviewer citationReviewer = mock(CitationReviewer.class);
        FinancialComplianceReviewer complianceReviewer = mock(FinancialComplianceReviewer.class);
        FinancialEvaluator evaluator = mock(FinancialEvaluator.class);
        FinancialReportFingerprinter fingerprinter = mock(FinancialReportFingerprinter.class);
        ReportService reportService = mock(ReportService.class);
        ResearchPlan plan = new ResearchPlan(
                "核验盈利质量", List.of(), List.of("财务证据"), List.of(), List.of(), "LLM", "v1"
        );
        when(planner.createPlan(any(), any())).thenReturn(
                new PlannerOutput<>(plan, false, "", 1, 1, 1L)
        );
        when(planner.nextAction(any(), any())).thenReturn(
                new PlannerOutput<>(AgentAction.synthesize("生成报告"), false, "", 1, 1, 1L),
                new PlannerOutput<>(
                        new AgentAction(
                                AgentActionType.STOP_INSUFFICIENT_EVIDENCE,
                                List.of(),
                                "测试在重算前停止"
                        ),
                        false,
                        "",
                        1,
                        1,
                        1L
                )
        );
        when(planner.replan(any(), any())).thenReturn(
                new PlannerOutput<>(plan, false, "", 1, 1, 1L)
        );
        when(runtimeMapper.saveTurn(
                anyLong(), anyInt(), anyString(), anyString(), any(), anyString(), anyString(),
                anyInt(), anyInt(), anyLong()
        )).thenReturn(31L, 32L);
        when(commitModule.openTurn(any(), any(), any(), anyInt(), anyInt(), anyLong())).thenReturn(31L, 32L);
        when(taskMapper.updateAgentProgress(
                anyLong(), anyString(), anyInt(), anyInt(), anyString(), any()
        )).thenReturn(true);
        when(reportWriter.write(anyString(), any(), any(), any(), any(), any()))
                .thenReturn("# 测试报告\n\n仅作研究辅助，不构成投资建议");
        when(citationReviewer.review(anyString(), any(), any())).thenReturn(CitationReviewResult.pass());
        when(complianceReviewer.review(anyString(), any())).thenReturn(
                new FinancialComplianceReviewResult("PASS", new BigDecimal("100.00"), List.of())
        );
        when(evaluator.evaluateOnline(anyString(), any(), any())).thenReturn(numericEvaluationFailure());
        when(fingerprinter.dataSnapshotHash(any())).thenReturn("snapshot-hash");
        when(fingerprinter.generationContextHash(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()
        )).thenReturn("generation-hash");
        when(reportService.findReusable(anyLong(), anyString())).thenReturn(Optional.empty());

        ResearchAgentRuntime runtime = new ResearchAgentRuntime(
                planner,
                registry,
                policyGuard,
                new AgentBudgetGuard(
                        8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
                ),
                commitModule,
                mock(AgentEventOutboxPublisher.class),
                mock(PlannerTelemetryModule.class),
                new ToolStateReducer(new EvidenceMemory(new FinancialEvidenceValidator())),
                new ProgressFingerprint(new ObjectMapper().findAndRegisterModules(), fingerprinter),
                runtimeStateService,
                reportWriter,
                citationReviewer,
                complianceReviewer,
                evaluator,
                new QualityGateDecisionEngine(),
                fingerprinter,
                reportService,
                toolExecutor
        );
        AgentState state = readyState(policyGuard);

        ResearchAgentRuntime.RuntimeOutcome outcome = runtime.execute(
                7L,
                state,
                new AgentBudget(8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                new LeaseToken(11L, "runner-1", 1L),
                AgentEventListener.noop()
        );

        assertThat(outcome.status()).isEqualTo("INSUFFICIENT_EVIDENCE");
        assertThat(state.getQualityGateDecision().route())
                .isEqualTo(QualityGateRoute.RECALCULATE_DETERMINISTIC_RESULTS);
        assertThat(state.getMetrics()).isEmpty();
        assertThat(state.getRiskAssessment()).isNull();
        assertThat(state.getReplanCount()).isEqualTo(1);
        assertThat(state.getCompletedTools()).doesNotContain(
                "calculate_financial_metrics", "assess_financial_risk"
        );
        verify(planner).replan(eq(state), eq(registry));
    }

    @Test
    void completesEvidenceRecoveryWithNewQueryObservationAndDerivedRecalculation() {
        EvidenceMemory evidenceMemory = new EvidenceMemory(new FinancialEvidenceValidator());
        AtomicReference<ToolInvocation> recoveryInvocation = new AtomicReference<>();
        ResearchTool recoveryTool = recoveryEvidenceTool(recoveryInvocation);
        ResearchTool calculateTool = stateMutationTool(
                "calculate_financial_metrics",
                new ToolPayload.Metrics(List.of(new FinancialMetricResult(
                        "ROE",
                        new BigDecimal("13.00"),
                        "13.00%",
                        "净利润/净资产",
                        "OK",
                        "",
                        List.of("RECOVERY_EVIDENCE")
                )))
        );
        ResearchTool riskTool = stateMutationTool(
                "assess_financial_risk",
                new ToolPayload.Risk(new FinancialRiskAssessment(
                        new BigDecimal("18.00"), "LOW", List.of(), List.of()
                ))
        );
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(
                recoveryTool, calculateTool, riskTool, new CheckEvidenceCoverageTool()
        ));
        ToolPolicyGuard policyGuard = new ToolPolicyGuard(registry, new ObjectMapper());
        ResearchPlanner planner = new ResearchPlanner(
                (prompt, modelType) -> {
                    throw new IllegalStateException("LLM is not configured");
                },
                new ObjectMapper().findAndRegisterModules()
        );
        AgentRuntimeMapper runtimeMapper = mock(AgentRuntimeMapper.class);
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        DurableTurnCommitModule commitModule = mock(DurableTurnCommitModule.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        FinancialSnapshotMapper snapshotMapper = mock(FinancialSnapshotMapper.class);
        TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
        InvestmentReportWriter reportWriter = mock(InvestmentReportWriter.class);
        CitationReviewer citationReviewer = mock(CitationReviewer.class);
        FinancialComplianceReviewer complianceReviewer = mock(FinancialComplianceReviewer.class);
        FinancialEvaluator evaluator = mock(FinancialEvaluator.class);
        FinancialReportFingerprinter fingerprinter = mock(FinancialReportFingerprinter.class);
        ReportService reportService = mock(ReportService.class);
        when(runtimeMapper.saveTurn(
                anyLong(), anyInt(), anyString(), anyString(), any(), anyString(), anyString(),
                anyInt(), anyInt(), anyLong()
        )).thenReturn(31L);
        when(commitModule.openTurn(any(), any(), any(), anyInt(), anyInt(), anyLong())).thenReturn(31L);
        when(commitModule.journalToolStart(any(), any(), anyLong(), anyString(), anyString(), anyString(), any(), anyInt()))
                .thenReturn(41L);
        when(runtimeMapper.startToolCall(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), any(), anyInt()
        )).thenReturn(41L);
        when(taskMapper.updateAgentProgress(
                anyLong(), anyString(), anyInt(), anyInt(), anyString(), any()
        )).thenReturn(true);
        when(reportWriter.write(anyString(), any(), any(), any(), any(), any()))
                .thenReturn("# 测试报告\n\n仅作研究辅助，不构成投资建议");
        when(citationReviewer.review(anyString(), any(), any())).thenReturn(
                CitationReviewResult.fail("EVIDENCE_CONFLICT: 同一指标存在来源冲突"),
                CitationReviewResult.pass()
        );
        when(complianceReviewer.review(anyString(), any())).thenReturn(
                new FinancialComplianceReviewResult("PASS", new BigDecimal("100.00"), List.of())
        );
        when(evaluator.evaluateOnline(anyString(), any(), any())).thenReturn(evaluationPass());
        when(fingerprinter.dataSnapshotHash(any())).thenReturn("snapshot-hash");
        when(fingerprinter.generationContextHash(
                anyString(), anyString(), anyString(), anyString(), anyString(),
                anyString(), anyString(), anyString()
        )).thenReturn("generation-hash");
        when(reportService.findReusable(anyLong(), anyString())).thenReturn(Optional.empty());
        when(reportService.saveLatest(
                anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString(), any()
        )).thenReturn(99L);
        when(commitModule.commitCompletedRun(
                any(AgentTurnCommit.class), any(LeaseToken.class), any(FinalReportCommit.class),
                any(AgentEventDraft.class)
        )).thenReturn(new CompletedRunCommitResult(99L, List.of()));

        ResearchAgentRuntime runtime = new ResearchAgentRuntime(
                planner,
                registry,
                policyGuard,
                new AgentBudgetGuard(
                        8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(180), Duration.ofSeconds(30)
                ),
                commitModule,
                mock(AgentEventOutboxPublisher.class),
                mock(PlannerTelemetryModule.class),
                new ToolStateReducer(evidenceMemory),
                new ProgressFingerprint(new ObjectMapper().findAndRegisterModules(), fingerprinter),
                runtimeStateService,
                reportWriter,
                citationReviewer,
                complianceReviewer,
                evaluator,
                new QualityGateDecisionEngine(),
                fingerprinter,
                reportService,
                toolExecutor
        );
        AgentState state = readyState(policyGuard);
        state.setSnapshotId(21L);
        ToolInvocation originalSearch = new ToolInvocation(
                "search_public_evidence", Map.of("query", "原始公告影响查询")
        );
        List<String> completedTools = List.of(
                "get_company_profile",
                "get_financial_statements",
                "get_market_snapshot",
                "retrieve_uploaded_reports",
                "search_public_evidence",
                "calculate_financial_metrics",
                "assess_financial_risk",
                "check_evidence_coverage"
        );
        state.setCompletedTools(Set.copyOf(completedTools));
        state.setToolInvocationHistory(List.of(originalSearch));
        state.setExecutedCallHashes(Set.of(
                policyGuard.callHash(originalSearch),
                policyGuard.callHash(new ToolInvocation("calculate_financial_metrics", Map.of())),
                policyGuard.callHash(new ToolInvocation("assess_financial_risk", Map.of())),
                policyGuard.callHash(new ToolInvocation("check_evidence_coverage", Map.of()))
        ));
        ResearchAgentRuntime.RuntimeOutcome outcome = runtime.execute(
                7L,
                state,
                new AgentBudget(8, 12, 3, 2, 3, 4, 2, Duration.ofSeconds(10), Duration.ofSeconds(1)),
                new LeaseToken(11L, "runner-1", 1L),
                AgentEventListener.noop()
        );

        assertThat(outcome.status()).isEqualTo("COMPLETED");
        assertThat(outcome.reportId()).isEqualTo(99L);
        assertThat(state.getEvidenceRecoveryDirective().status()).isEqualTo("SATISFIED");
        assertThat(state.getEvidenceRecoveryDirective().addedEffectiveEvidenceCount()).isEqualTo(1L);
        assertThat(state.getSnapshot().evidenceItems()).hasSize(4);
        assertThat(state.getMetrics()).extracting(FinancialMetricResult::displayValue)
                .containsExactly("13.00%");
        assertThat(state.getRiskAssessment()).isNotNull();
        assertThat(recoveryInvocation.get()).isNotNull();
        assertThat(recoveryInvocation.get().arguments().get("query"))
                .isNotEqualTo(originalSearch.arguments().get("query"));
        verify(reportWriter, times(2)).write(anyString(), any(), any(), any(), any(), any());
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

    private AgentState readyState(ToolPolicyGuard policyGuard) {
        AgentState state = state();
        StockSubject subject = new StockSubject(
                "600519", "SH", "600519.SH", "贵州茅台", "食品饮料"
        );
        List<FinancialEvidenceItem> evidence = java.util.stream.IntStream.range(0, 3)
                .mapToObj(index -> new FinancialEvidenceItem(
                        "PUBLIC_MARKET",
                        "测试来源" + index,
                        "",
                        null,
                        "latest",
                        "METRIC_" + index,
                        null,
                        null,
                        "测试证据" + index,
                        new BigDecimal("0.90"),
                        LocalDateTime.of(2026, 8, 12, 10, 0),
                        ""
                ))
                .toList();
        state.setSubject(subject);
        state.setSnapshot(new FinancialSnapshot(
                subject, "latest", "hybrid", evidence, LocalDateTime.of(2026, 8, 12, 10, 0)
        ));
        state.setMetrics(List.of(new FinancialMetricResult(
                "ROE", new BigDecimal("12.00"), "12.00%", "净利润/净资产", "OK", "", List.of("METRIC_0")
        )));
        state.setRiskAssessment(new FinancialRiskAssessment(
                new BigDecimal("20.00"), "LOW", List.of(), List.of()
        ));
        List<String> deterministicTools = List.of(
                "calculate_financial_metrics", "assess_financial_risk"
        );
        state.setCompletedTools(Set.copyOf(deterministicTools));
        state.setExecutedCallHashes(deterministicTools.stream()
                .map(toolName -> policyGuard.callHash(new ToolInvocation(toolName, Map.of())))
                .collect(java.util.stream.Collectors.toSet()));
        return state;
    }

    private FinancialEvaluationResult numericEvaluationFailure() {
        FinancialEvaluationMetricScore metric = new FinancialEvaluationMetricScore(
                "numeric_consistency_rate",
                BigDecimal.ZERO,
                new BigDecimal("0.95"),
                "FAIL",
                "正文数字与确定性指标不一致",
                FinancialEvaluationMetricScore.Category.RULE,
                FinancialEvaluationMetricScore.GateLevel.HARD,
                FinancialEvaluationMetricScore.Direction.HIGHER_BETTER
        );
        return new FinancialEvaluationResult(
                "600519", "贵州茅台", BigDecimal.ZERO, "FAIL", List.of(metric), List.of(metric.reason())
        );
    }

    private FinancialEvaluationResult evaluationPass() {
        return new FinancialEvaluationResult(
                "600519", "贵州茅台", BigDecimal.ONE, "PASS", List.of(), List.of()
        );
    }

    private ResearchTool<?> recoveryEvidenceTool(AtomicReference<ToolInvocation> invocationReference) {
        return new ResearchTool<RawToolArguments>() {
            @Override
            public String name() {
                return "search_public_evidence";
            }

            @Override
            public String description() {
                return "使用新查询补充测试证据";
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
            public ToolResult execute(ToolContext context, RawToolArguments arguments) {
                invocationReference.set(new ToolInvocation(name(), arguments.values()));
                FinancialEvidenceItem evidence = new FinancialEvidenceItem(
                        "PUBLIC_RESEARCH",
                        "补充公告",
                        "https://example.com/recovery",
                        null,
                        "20260812",
                        "RECOVERY_EVIDENCE",
                        null,
                        null,
                        "补充公告提供了可交叉验证的新事实证据，正文长度满足证据质量要求。",
                        new BigDecimal("0.90"),
                        LocalDateTime.of(2026, 8, 12, 12, 0),
                        ""
                );
                List<FinancialEvidenceItem> added = List.of(evidence);
                return new ToolResult(
                        "SUCCESS",
                        "新增补充证据",
                        new ToolPayload.Evidence(
                                name(), "", added, null, List.of(), null, added.size(), added.size()
                        ),
                        added,
                        "",
                        false
                );
            }
        };
    }

    private ResearchTool<?> stateMutationTool(String name, ToolPayload payload) {
        return new ResearchTool<RawToolArguments>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "测试确定性派生工具";
            }

            @Override
            public ToolResult execute(ToolContext context, RawToolArguments arguments) {
                return ToolResult.success(name + " 完成", payload);
            }
        };
    }

    private AgentEventListener listener(AtomicBoolean done) {
        return new AgentEventListener() {
            @Override
            public void onEvent(AgentEvent event) {
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
