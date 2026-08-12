package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.event.AgentEventDraft;
import com.zzy.finsight.agent.event.AgentEventOutboxPublisher;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.AgentActionType;
import com.zzy.finsight.agent.planning.PlannerOutput;
import com.zzy.finsight.agent.planning.ResearchPlanner;
import com.zzy.finsight.agent.planning.PlannerTelemetryModule;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.quality.QualityGateDecision;
import com.zzy.finsight.agent.quality.QualityGateDecisionEngine;
import com.zzy.finsight.agent.quality.QualityGateRoute;
import com.zzy.finsight.agent.tool.ResearchTool;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.agent.tool.PreparedToolCall;
import com.zzy.finsight.agent.tool.ToolContext;
import com.zzy.finsight.agent.tool.ToolPolicyGuard;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.agent.tool.ToolStateReducer;
import com.zzy.finsight.component.review.CitationReviewer;
import com.zzy.finsight.component.review.FinancialComplianceReviewer;
import com.zzy.finsight.component.review.FinancialEvaluator;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.domain.ReusableReportRecord;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.service.ReportService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 执行 plan、act、observe、replan 和 stop 通用循环，不包含写死的金融步骤顺序。
 */
@Component
public class ResearchAgentRuntime {
    public static final String POLICY_VERSION = "research-agent-runtime-v3-durable-progress-routing";
    public static final String TOOLSET_VERSION = "financial-research-tools-v2-typed-outcome";

    private final ResearchPlanner planner;
    private final ResearchToolRegistry toolRegistry;
    private final ToolPolicyGuard toolPolicyGuard;
    private final AgentBudgetGuard budgetGuard;
    private final DurableTurnCommitModule turnCommitModule;
    private final AgentEventOutboxPublisher eventPublisher;
    private final PlannerTelemetryModule plannerTelemetry;
    private final ToolStateReducer toolStateReducer;
    private final ProgressFingerprint progressFingerprint;
    private final TaskRuntimeStateService runtimeStateService;
    private final InvestmentReportWriter reportWriter;
    private final CitationReviewer citationReviewer;
    private final FinancialComplianceReviewer complianceReviewer;
    private final FinancialEvaluator evaluator;
    private final QualityGateDecisionEngine qualityGateDecisionEngine;
    private final FinancialReportFingerprinter fingerprinter;
    private final ReportService reportService;
    private final ExecutorService toolExecutor;

    public ResearchAgentRuntime(
            ResearchPlanner planner,
            ResearchToolRegistry toolRegistry,
            ToolPolicyGuard toolPolicyGuard,
            AgentBudgetGuard budgetGuard,
            DurableTurnCommitModule turnCommitModule,
            AgentEventOutboxPublisher eventPublisher,
            PlannerTelemetryModule plannerTelemetry,
            ToolStateReducer toolStateReducer,
            ProgressFingerprint progressFingerprint,
            TaskRuntimeStateService runtimeStateService,
            InvestmentReportWriter reportWriter,
            CitationReviewer citationReviewer,
            FinancialComplianceReviewer complianceReviewer,
            FinancialEvaluator evaluator,
            QualityGateDecisionEngine qualityGateDecisionEngine,
            FinancialReportFingerprinter fingerprinter,
            ReportService reportService,
            @Qualifier("financialProviderExecutor") ExecutorService toolExecutor
    ) {
        this.planner = planner;
        this.toolRegistry = toolRegistry;
        this.toolPolicyGuard = toolPolicyGuard;
        this.budgetGuard = budgetGuard;
        this.turnCommitModule = turnCommitModule;
        this.eventPublisher = eventPublisher;
        this.plannerTelemetry = plannerTelemetry;
        this.toolStateReducer = toolStateReducer;
        this.progressFingerprint = progressFingerprint;
        this.runtimeStateService = runtimeStateService;
        this.reportWriter = reportWriter;
        this.citationReviewer = citationReviewer;
        this.complianceReviewer = complianceReviewer;
        this.evaluator = evaluator;
        this.qualityGateDecisionEngine = qualityGateDecisionEngine;
        this.fingerprinter = fingerprinter;
        this.reportService = reportService;
        this.toolExecutor = toolExecutor;
    }

    /** 执行或恢复单个 Agent 任务，直到通过门禁或以稳定原因停止。 */
    public RuntimeOutcome execute(
            long ownerId,
            AgentState state,
            AgentBudget budget,
            LeaseToken lease,
            AgentEventListener listener
    ) {
        AgentEventListener events = listener == null ? AgentEventListener.noop() : listener;
        state.setRuntimeLease(lease);
        long deadlineNanos = System.nanoTime() + budget.timeout().toNanos();
        publish(state, events, "run_created", mapOf(
                "researchQuestion", state.getRequest().getResearchQuestion(),
                "ticker", state.getRequest().getTicker(),
                "asOfDate", state.getRequest().getAsOfDate(),
                "researchDepth", state.getRequest().getResearchDepth(),
                "budget", budget,
                "resumed", state.getTurnNo() > 0
        ), 0L, "SUCCESS", "");
        if (state.getPlan() == null) {
            state.setPhase("PLANNING");
            PlannerOutput<com.zzy.finsight.agent.planning.ResearchPlan> planOutput = planner.createPlan(
                    state.getRequest(), toolRegistry
            );
            state.setPlan(planOutput.value());
            state.setPlannerDegraded(planOutput.degraded());
            plannerTelemetry.record(state.getTaskId(), planOutput, true);
            publish(state, events, "plan_created", mapOf(
                    "plan", state.getPlan(),
                    "plannerMode", state.getPlan().plannerMode(),
                    "degraded", planOutput.degraded(),
                    "degradedReason", planOutput.degradedReason()
            ), planOutput.durationMs(), planOutput.degraded() ? "DEGRADED" : "SUCCESS", planOutput.degradedReason());
            turnCommitModule.checkpointAndRenew(state, lease);
        }

        while (!budgetGuard.exhausted(state, budget) && System.nanoTime() < deadlineNanos) {
            state.setPhase("PLANNING");
            PlannerOutput<AgentAction> actionOutput = planner.nextAction(state, toolRegistry);
            state.setPlannerDegraded(state.isPlannerDegraded() || actionOutput.degraded());
            AgentAction action = actionOutput.value();
            plannerTelemetry.record(
                    state.getTaskId(),
                    actionOutput,
                    routeCorrect(action, state, budget)
            );
            state.setTurnNo(state.getTurnNo() + 1);
            long turnStartedAt = System.nanoTime();
            long turnId = turnCommitModule.openTurn(
                    state, lease, action,
                    actionOutput.inputTokens(), actionOutput.outputTokens(), actionOutput.durationMs()
            );

            try {
                List<CommittedToolCall> committedTools = List.of();
                String observation;
                String turnStatus = "SUCCESS";
                SynthesisOutcome synthesis = null;
                try {
                    toolPolicyGuard.validate(
                            action,
                            state,
                            budget.maxToolCalls() - state.getToolCallCount(),
                            budget.maxParallelTools()
                    );
                } catch (IllegalArgumentException exception) {
                    commitTurn(ownerId, turnId, state, exception.getMessage(), "FAILED", turnStartedAt, List.of(), lease);
                    return stop(state, events, "FAILED_INVALID_ACTION", exception.getMessage(), lease);
                }
                if (action.type() == AgentActionType.CALL_TOOL
                        || action.type() == AgentActionType.CALL_TOOLS_PARALLEL) {
                    state.setPhase("RUNNING_TOOLS");
                    ToolBatchOutcome tools = executeTools(
                            ownerId, state, action, turnId, budget, events
                    );
                    observation = tools.observation();
                    committedTools = tools.committedCalls();
                    commitTurn(
                            ownerId,
                            turnId,
                            state,
                            observation,
                            turnStatus,
                            turnStartedAt,
                            committedTools,
                            lease,
                            events,
                            completedToolEventDrafts(state, tools.completedExecutions())
                    );
                    if (state.getConsecutiveNoNewEvidenceTurns() >= 2) {
                        return stop(
                                state,
                                events,
                                "INSUFFICIENT_EVIDENCE",
                                "连续 2 轮证据采集未产生新增有效证据",
                                lease
                        );
                    }
                } else if (action.type() == AgentActionType.REPLAN) {
                    observation = executeReplan(state, budget, events);
                    commitTurn(ownerId, turnId, state, observation, turnStatus, turnStartedAt, List.of(), lease);
                } else if (action.type() == AgentActionType.SYNTHESIZE) {
                    if (!readyForSynthesis(state)) {
                        state.setLastReviewReason("EVIDENCE_INSUFFICIENT：主体、有效证据、指标或风险结果尚未齐备");
                        observation = executeReplan(state, budget, events);
                        turnStatus = "DEGRADED";
                        commitTurn(ownerId, turnId, state, observation, turnStatus, turnStartedAt, List.of(), lease);
                    } else {
                        state.setPhase("SYNTHESIZING");
                        publish(state, events, "synthesis_started", mapOf(
                                "reason", action.reason(),
                                "evidenceCount", state.getSnapshot().evidenceItems().size()
                        ), 0L, "SUCCESS", "");
                        synthesis = synthesize(ownerId, state, budget, events);
                        turnStatus = synthesis.completed() ? "SUCCESS" : "DEGRADED";
                        if (synthesis.completed()) {
                            return complete(
                                    ownerId,
                                    turnId,
                                    state,
                                    events,
                                    synthesis,
                                    turnStatus,
                                    turnStartedAt,
                                    lease
                            );
                        }
                        commitTurn(
                                ownerId, turnId, state, synthesis.reason(), turnStatus,
                                turnStartedAt, List.of(), lease
                        );
                        if (!synthesis.continueResearch()) {
                            return stop(state, events, synthesis.stopStatus(), synthesis.reason(), lease);
                        }
                    }
                } else {
                    commitTurn(ownerId, turnId, state, action.reason(), "DEGRADED", turnStartedAt, List.of(), lease);
                    return stop(state, events, "INSUFFICIENT_EVIDENCE", action.reason(), lease);
                }
                if (state.getConsecutiveNoProgressTurns() >= budget.maxStagnantTurns()) {
                    return stop(
                            state,
                            events,
                            "INSUFFICIENT_EVIDENCE",
                            "NO_PROGRESS：连续已提交 turn 的计划、证据、指标和门禁路由均未变化",
                            lease
                    );
                }
            } catch (IllegalStateException exception) {
                if (exception.getMessage() != null && exception.getMessage().startsWith("BUDGET_EXHAUSTED")) {
                    commitTurn(ownerId, turnId, state, exception.getMessage(), "DEGRADED", turnStartedAt, List.of(), lease);
                    return stop(state, events, "INSUFFICIENT_EVIDENCE", exception.getMessage(), lease);
                }
                throw exception;
            } catch (RuntimeException exception) {
                throw exception;
            }
        }
        String reason = System.nanoTime() >= deadlineNanos
                ? "BUDGET_EXHAUSTED：整体运行超时"
                : "BUDGET_EXHAUSTED：轮次或工具调用预算已耗尽";
        return stop(state, events, "INSUFFICIENT_EVIDENCE", reason, lease);
    }

    private ToolBatchOutcome executeTools(
            long ownerId,
            AgentState state,
            AgentAction action,
            long turnId,
            AgentBudget budget,
            AgentEventListener events
    ) {
        List<PendingToolExecution> pending = new ArrayList<>();
        for (ToolInvocation invocation : action.toolCalls()) {
            pending.add(startTool(ownerId, state, turnId, invocation, 1, events));
        }
        List<String> summaries = new ArrayList<>();
        boolean evidenceToolCalled = false;
        boolean recoveryBatch = state.hasPendingEvidenceRecovery();
        long newEffectiveEvidence = 0L;
        List<CompletedToolExecution> completedExecutions = new ArrayList<>();
        for (PendingToolExecution execution : pending) {
            ToolAttemptOutcome attemptOutcome = awaitTool(
                    ownerId, state, turnId, execution, budget, events
            );
            CompletedToolExecution completed = attemptOutcome.finalAttempt();
            completedExecutions.addAll(attemptOutcome.attempts());
            summaries.add(completed.result().summary());
            boolean producesEvidence = toolRegistry.require(
                    completed.invocation().toolName()
            ).producesEvidence();
            boolean recoveryAttempt = producesEvidence && recoveryBatch;
            long toolNewEffectiveEvidence = completed.result().evidenceItems().stream()
                    .filter(FinancialEvidenceItem::effective)
                    .count();
            if (producesEvidence) {
                evidenceToolCalled = true;
                newEffectiveEvidence += toolNewEffectiveEvidence;
            }
            if (recoveryAttempt) {
                state.recordEvidenceRecoveryAttempt(
                        completed.invocation(), toolNewEffectiveEvidence
                );
            }
            state.recordObservation(
                    completed.invocation(),
                    completed.callHash(),
                    completed.result().summary()
            );
        }
        if (evidenceToolCalled) {
            state.setConsecutiveNoNewEvidenceTurns(
                    newEffectiveEvidence > 0L ? 0 : state.getConsecutiveNoNewEvidenceTurns() + 1
            );
        }
        List<CommittedToolCall> committedCalls = completedExecutions.stream()
                .map(completed -> new CommittedToolCall(
                        completed.databaseId(), completed.invocation(), completed.result(), completed.durationMs()
                ))
                .toList();
        return new ToolBatchOutcome(String.join("；", summaries), committedCalls, completedExecutions);
    }

    private PendingToolExecution startTool(
            long ownerId,
            AgentState state,
            long turnId,
            ToolInvocation invocation,
            int attemptNo,
            AgentEventListener events
    ) {
        PreparedToolCall prepared = toolRegistry.prepare(invocation);
        ResearchTool<?> tool = prepared.tool();
        String callHash = toolPolicyGuard.callHash(invocation);
        String callId = UUID.randomUUID().toString();
        long databaseId = turnCommitModule.journalToolStart(
                state, state.getRuntimeLease(), turnId, callId,
                tool.name(), callHash, invocation.arguments(), attemptNo
        );
        state.setToolCallCount(state.getToolCallCount() + 1);
        publish(state, events, "tool_started", mapOf(
                "callId", callId,
                "toolName", tool.name(),
                "arguments", invocation.arguments(),
                "attempt", attemptNo
        ), 0L, "RUNNING", "");
        long startedAt = System.nanoTime();
        Future<ToolResult> future = toolExecutor.submit(
                () -> toolRegistry.execute(
                        prepared,
                        ToolContext.from(ownerId, state)
                )
        );
        return new PendingToolExecution(
                invocation, tool, callHash, callId, databaseId, attemptNo, startedAt, future
        );
    }

    private ToolAttemptOutcome awaitTool(
            long ownerId,
            AgentState state,
            long turnId,
            PendingToolExecution execution,
            AgentBudget budget,
            AgentEventListener events
    ) {
        ToolResult result;
        try {
            result = execution.future().get(budget.toolTimeout().toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException exception) {
            execution.future().cancel(true);
            result = ToolResult.failure("工具调用超时", "TOOL_TIMEOUT", execution.tool().idempotent());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            execution.future().cancel(true);
            result = ToolResult.failure("工具调用被中断", "TOOL_INTERRUPTED", false);
        } catch (ExecutionException exception) {
            Throwable cause = exception.getCause() == null ? exception : exception.getCause();
            result = ToolResult.failure(
                    cause.getMessage() == null ? "工具调用失败" : cause.getMessage(),
                    "TOOL_CALL_FAILED",
                    execution.tool().idempotent()
            );
        }
        long durationMs = elapsedMs(execution.startedAt());
        if ("FAILED".equals(result.status())
                && result.retryable()
                && execution.attemptNo() < 2
                && state.getToolCallCount() < budget.maxToolCalls()) {
            try {
                TimeUnit.SECONDS.sleep(1);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                CompletedToolExecution interrupted = completed(execution, result, durationMs);
                return new ToolAttemptOutcome(List.of(interrupted), interrupted);
            }
            PendingToolExecution retry = startTool(
                    ownerId, state, turnId, execution.invocation(), execution.attemptNo() + 1, events
            );
            CompletedToolExecution failedAttempt = completed(execution, result, durationMs);
            ToolAttemptOutcome retried = awaitTool(ownerId, state, turnId, retry, budget, events);
            List<CompletedToolExecution> attempts = new ArrayList<>();
            attempts.add(failedAttempt);
            attempts.addAll(retried.attempts());
            return new ToolAttemptOutcome(attempts, retried.finalAttempt());
        }
        ToolResult normalized = toolStateReducer.apply(state, execution.tool().name(), durationMs, result);
        CompletedToolExecution completed = completed(execution, normalized, durationMs);
        return new ToolAttemptOutcome(List.of(completed), completed);
    }

    private CompletedToolExecution completed(
            PendingToolExecution execution,
            ToolResult result,
            long durationMs
    ) {
        return new CompletedToolExecution(
                execution.invocation(), execution.callHash(), execution.callId(), execution.databaseId(),
                result, durationMs
        );
    }

    private String executeReplan(AgentState state, AgentBudget budget, AgentEventListener events) {
        if (state.getReplanCount() >= budget.maxReplans()) {
            throw new IllegalStateException("BUDGET_EXHAUSTED：重新规划次数已耗尽");
        }
        state.setReplanCount(state.getReplanCount() + 1);
        state.setPhase("REPLANNING");
        PlannerOutput<com.zzy.finsight.agent.planning.ResearchPlan> output = planner.replan(state, toolRegistry);
        state.setPlan(output.value());
        state.setPlannerDegraded(state.isPlannerDegraded() || output.degraded());
        plannerTelemetry.record(state.getTaskId(), output, true);
        publish(state, events, "replanned", mapOf(
                "plan", state.getPlan(),
                "replanCount", state.getReplanCount(),
                "degraded", output.degraded(),
                "degradedReason", output.degradedReason()
        ), output.durationMs(), output.degraded() ? "DEGRADED" : "SUCCESS", output.degradedReason());
        return "已根据当前证据和审查反馈重新规划";
    }

    private SynthesisOutcome synthesize(
            long ownerId,
            AgentState state,
            AgentBudget budget,
            AgentEventListener events
    ) {
        String dataHash = fingerprinter.dataSnapshotHash(state.getSnapshot());
        String contextHash = fingerprinter.generationContextHash(
                dataHash,
                state.getRequest().getResearchQuestion(),
                state.getRequest().getAsOfDate().toString(),
                state.getRequest().getTimeHorizon(),
                state.getRequest().getResearchDepth(),
                ResearchPlanner.PLANNER_VERSION,
                TOOLSET_VERSION,
                POLICY_VERSION
        );
        Optional<ReusableReportRecord> reusable = reportService.findReusable(ownerId, contextHash)
                .filter(report -> "PASS".equals(report.reviewStatus()));
        if (reusable.isPresent()) {
            ReusableReportRecord report = reusable.orElseThrow();
            ReviewBundle review = review(report.content(), state);
            if (review.passed()) {
                state.setFinalReport(report.content());
                applyReview(state, review);
                publishReview(state, events, review, true);
                return new SynthesisOutcome(
                        true,
                        false,
                        "COMPLETED",
                        "复用报告经当前门禁验证通过",
                        new FinalReportCommit(report.content(), "", dataHash, contextHash, report.id())
                );
            }
        }

        CitationReviewResult previousReview = writerFeedback(state);
        for (int attempt = 1; attempt <= budget.maxReportRewrites() + 1; attempt++) {
            BullBearResearchResult cases = state.getBullBearResearch() == null
                    ? BullBearResearchResult.empty() : state.getBullBearResearch();
            String report = reportWriter.write(
                    state.getRequest().getResearchQuestion(),
                    state.getSnapshot(),
                    state.getMetrics(),
                    state.getRiskAssessment(),
                    cases,
                    previousReview
            );
            state.setFinalReport(report);
            state.setReportRewriteCount(Math.max(0, attempt - 1));
            ReviewBundle review = review(report, state);
            applyReview(state, review);
            publish(state, events, "synthesis_completed", mapOf(
                    "attempt", attempt,
                    "finalReport", report,
                    "generationMode", InvestmentReportWriter.generationMode(report),
                    "fallbackReason", InvestmentReportWriter.fallbackReason(report)
            ), 0L, "SUCCESS", "");
            publishReview(state, events, review, false);
            if (review.passed()) {
                return new SynthesisOutcome(
                        true,
                        false,
                        "COMPLETED",
                        "全部确定性门禁通过",
                        new FinalReportCommit(report, "", dataHash, contextHash, null)
                );
            }
            previousReview = CitationReviewResult.fail(review.reason());
            state.setLastReviewReason(review.reason());
            QualityGateRoute route = review.decision().route();
            publishQualityGateRoute(state, events, review.decision(), attempt);
            if (route == QualityGateRoute.COLLECT_MORE_EVIDENCE) {
                if (state.getEvidenceRecoveryCount() >= budget.maxEvidenceRecoveries()) {
                    return new SynthesisOutcome(
                            false,
                            false,
                            "INSUFFICIENT_EVIDENCE",
                            "EVIDENCE_RECOVERY_EXHAUSTED：补证据轮次已达到上限",
                            null
                    );
                }
                if (canReplan(state, budget, 1)) {
                    state.beginEvidenceRecovery(review.decision());
                    executeReplan(state, budget, events);
                    return new SynthesisOutcome(false, true, "RUNNING", review.reason(), null);
                }
                return new SynthesisOutcome(
                        false, false, "INSUFFICIENT_EVIDENCE", review.reason(), null
                );
            }
            if (route == QualityGateRoute.RECALCULATE_DETERMINISTIC_RESULTS) {
                if (canReplan(state, budget, 2)) {
                    prepareDeterministicRecalculation(state);
                    executeReplan(state, budget, events);
                    return new SynthesisOutcome(false, true, "RUNNING", review.reason(), null);
                }
                return new SynthesisOutcome(false, false, "FAILED", review.reason(), null);
            }
            if (route == QualityGateRoute.STOP_FAILED) {
                return new SynthesisOutcome(false, false, "FAILED", review.reason(), null);
            }
        }
        if (!state.getFinalReport().isBlank()) {
            reportService.saveLatest(
                    ownerId, state.getThreadId(), state.getTaskId(), state.getFinalReport(), "FAIL",
                    state.getLastReviewReason(), state.getSnapshotId(), dataHash, contextHash, null
            );
        }
        return new SynthesisOutcome(false, false, "FAILED", state.getLastReviewReason(), null);
    }

    private ReviewBundle review(String report, AgentState state) {
        state.setPhase("REVIEWING");
        CitationReviewResult citation = citationReviewer.review(
                report, state.getSnapshot(), state.getMetrics()
        );
        FinancialComplianceReviewResult compliance = complianceReviewer.review(report, citation);
        FinancialEvaluationResult evaluation = evaluator.evaluateOnline(
                report, state.getSnapshot(), state.getMetrics()
        );
        QualityGateDecision decision = qualityGateDecisionEngine.decide(
                citation, compliance, evaluation, state.getSnapshot()
        );
        return new ReviewBundle(citation, compliance, evaluation, decision);
    }

    private void applyReview(AgentState state, ReviewBundle review) {
        state.setCitationReview(review.citation());
        state.setComplianceReview(review.compliance());
        state.setEvaluation(review.evaluation());
        state.setQualityGateDecision(review.decision());
        state.setLastReviewReason(review.reason());
    }

    private CitationReviewResult writerFeedback(AgentState state) {
        QualityGateDecision decision = state.getQualityGateDecision();
        if (decision != null && !decision.passed()) {
            return CitationReviewResult.fail(decision.summary());
        }
        if (state.getCitationReview() != null) {
            return state.getCitationReview();
        }
        return state.getLastReviewReason().isBlank()
                ? null : CitationReviewResult.fail(state.getLastReviewReason());
    }

    private void publishReview(
            AgentState state,
            AgentEventListener events,
            ReviewBundle review,
            boolean reused
    ) {
        publish(state, events, "review_completed", mapOf(
                "reviewStatus", review.decision().status(),
                "critique", review.citation().reason(),
                "compliance", review.compliance(),
                "evaluation", review.evaluation(),
                "qualityGateDecision", review.decision(),
                "reused", reused
        ), 0L, review.passed() ? "SUCCESS" : "DEGRADED", review.reason());
    }

    private void publishQualityGateRoute(
            AgentState state,
            AgentEventListener events,
            QualityGateDecision decision,
            int attempt
    ) {
        publish(state, events, "quality_gate_routed", mapOf(
                "attempt", attempt,
                "route", decision.route(),
                "issues", decision.issues(),
                "summary", decision.summary()
        ), 0L, "DEGRADED", decision.summary());
    }

    private boolean canReplan(AgentState state, AgentBudget budget, int requiredToolCalls) {
        return state.getReplanCount() < budget.maxReplans()
                && state.getToolCallCount() + requiredToolCalls <= budget.maxToolCalls();
    }

    /** 清除可能受错误数字影响的派生结果，并仅释放两个确定性工具的重复调用锁。 */
    private void prepareDeterministicRecalculation(AgentState state) {
        state.setMetrics(List.of());
        state.setRiskAssessment(null);
        for (String toolName : List.of("calculate_financial_metrics", "assess_financial_risk")) {
            ToolInvocation invocation = new ToolInvocation(toolName, Map.of());
            state.allowDeterministicReexecution(toolName, toolPolicyGuard.callHash(invocation));
        }
    }

    private RuntimeOutcome complete(
            long ownerId,
            long turnId,
            AgentState state,
            AgentEventListener events,
            SynthesisOutcome synthesis,
            String turnStatus,
            long turnStartedAt,
            LeaseToken lease
    ) {
        state.setPhase("COMPLETED");
        state.setStopReason("COMPLETED");
        progressFingerprint.update(state);
        CompletedRunCommitResult committed = turnCommitModule.commitCompletedRun(
                new AgentTurnCommit(
                        ownerId,
                        turnId,
                        state,
                        synthesis.reason(),
                        turnStatus,
                        elapsedMs(turnStartedAt),
                        List.of()
                ),
                lease,
                synthesis.finalReport(),
                new AgentEventDraft("run_completed", mapOf(
                        "taskId", state.getTaskId(),
                        "turnCount", state.getTurnNo(),
                        "toolCallCount", state.getToolCallCount(),
                        "plannerDegraded", state.isPlannerDegraded()
                ), 0L, "SUCCESS", "")
        );
        runtimeStateService.markStatus(state.getTaskId(), "COMPLETED");
        committed.events().forEach(event -> eventPublisher.publish(event, events));
        safeDone(events);
        return new RuntimeOutcome("COMPLETED", state.getStopReason(), committed.reportId());
    }

    private RuntimeOutcome stop(
            AgentState state,
            AgentEventListener events,
            String status,
            String reason,
            LeaseToken lease
    ) {
        state.setPhase(status);
        state.setStopReason(reason);
        AgentEvent event = turnCommitModule.finishTaskWithEvent(
                state,
                lease,
                status,
                reason,
                null,
                new AgentEventDraft("run_stopped", mapOf(
                        "taskId", state.getTaskId(),
                        "status", status,
                        "reason", reason,
                        "turnCount", state.getTurnNo(),
                        "toolCallCount", state.getToolCallCount()
                ), 0L, "DEGRADED", reason)
        );
        runtimeStateService.markStatus(state.getTaskId(), status);
        eventPublisher.publish(event, events);
        safeDone(events);
        return new RuntimeOutcome(status, reason, 0L);
    }

    private void commitTurn(
            long ownerId,
            long turnId,
            AgentState state,
            String observation,
            String status,
            long startedAt,
            List<CommittedToolCall> toolCalls,
            LeaseToken lease
    ) {
        progressFingerprint.update(state);
        turnCommitModule.commitTurn(new AgentTurnCommit(
                ownerId, turnId, state, observation, status, elapsedMs(startedAt), toolCalls
        ), lease);
    }

    private void commitTurn(
            long ownerId,
            long turnId,
            AgentState state,
            String observation,
            String status,
            long startedAt,
            List<CommittedToolCall> toolCalls,
            LeaseToken lease,
            AgentEventListener listener,
            List<AgentEventDraft> drafts
    ) {
        progressFingerprint.update(state);
        List<AgentEvent> committedEvents = turnCommitModule.commitTurn(new AgentTurnCommit(
                ownerId, turnId, state, observation, status, elapsedMs(startedAt), toolCalls, drafts
        ), lease);
        committedEvents.forEach(event -> eventPublisher.publish(event, listener));
    }

    private List<AgentEventDraft> completedToolEventDrafts(
            AgentState state,
            List<CompletedToolExecution> completedTools
    ) {
        List<AgentEventDraft> drafts = new ArrayList<>();
        for (CompletedToolExecution completed : completedTools) {
            drafts.add(new AgentEventDraft("tool_completed", mapOf(
                    "callId", completed.callId(),
                    "toolName", completed.invocation().toolName(),
                    "result", completed.result().payload(),
                    "summary", completed.result().summary(),
                    "errorCode", completed.result().errorCode(),
                    "retryable", completed.result().retryable()
            ), completed.durationMs(), completed.result().status(), completed.result().errorCode()));
            if (toolRegistry.require(completed.invocation().toolName()).producesEvidence()
                    && state.getEvidenceRecoveryDirective() != null) {
                long added = completed.result().evidenceItems().stream()
                        .filter(FinancialEvidenceItem::effective)
                        .count();
                drafts.add(new AgentEventDraft("evidence_recovery_progress", mapOf(
                        "directive", state.getEvidenceRecoveryDirective(),
                        "toolName", completed.invocation().toolName(),
                        "arguments", completed.invocation().arguments(),
                        "newEffectiveEvidenceCount", added
                ), completed.durationMs(),
                        state.hasPendingEvidenceRecovery() ? "DEGRADED" : "SUCCESS",
                        state.hasPendingEvidenceRecovery() ? "本次调用未产生新增有效证据" : ""));
            }
        }
        return List.copyOf(drafts);
    }

    private boolean readyForSynthesis(AgentState state) {
        long effective = state.getSnapshot() == null ? 0L : state.getSnapshot().evidenceItems().stream()
                .filter(FinancialEvidenceItem::effective)
                .count();
        return state.getSubject() != null
                && effective >= 3
                && !state.getMetrics().isEmpty()
                && state.getRiskAssessment() != null;
    }

    /** 在执行前用确定性策略标记 Planner 路由是否可接受，供性能基线统计。 */
    private boolean routeCorrect(AgentAction action, AgentState state, AgentBudget budget) {
        try {
            toolPolicyGuard.validate(
                    action,
                    state,
                    budget.maxToolCalls() - state.getToolCallCount(),
                    budget.maxParallelTools()
            );
            if (action.type() == AgentActionType.SYNTHESIZE) {
                return readyForSynthesis(state);
            }
            if (action.type() == AgentActionType.REPLAN) {
                return state.getReplanCount() < budget.maxReplans();
            }
            return true;
        } catch (RuntimeException exception) {
            return false;
        }
    }

    private void publish(
            AgentState state,
            AgentEventListener listener,
            String eventType,
            Map<String, Object> payload,
            long durationMs,
            String status,
            String errorMessage
    ) {
        AgentEvent event = turnCommitModule.appendEvent(
                state,
                state.getRuntimeLease(),
                new AgentEventDraft(eventType, payload, durationMs, status, errorMessage)
        );
        eventPublisher.publish(event, listener);
    }

    private void safeDone(AgentEventListener listener) {
        try {
            listener.onDone();
        } catch (RuntimeException ignored) {
            // SSE 断开不影响任务最终状态。
        }
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int index = 0; index < values.length; index += 2) {
            result.put(String.valueOf(values[index]), values[index + 1]);
        }
        return result;
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    /**
     * 表示 Runtime 的最终状态。
     * @param status 任务状态。
     * @param reason 停止原因。
     * @param reportId 最终报告标识，未生成时为零。
     */
    public record RuntimeOutcome(String status, String reason, long reportId) {
    }

    private record PendingToolExecution(
            ToolInvocation invocation,
            ResearchTool<?> tool,
            String callHash,
            String callId,
            long databaseId,
            int attemptNo,
            long startedAt,
            Future<ToolResult> future
    ) {
    }

    private record CompletedToolExecution(
            ToolInvocation invocation,
            String callHash,
            String callId,
            long databaseId,
            ToolResult result,
            long durationMs
    ) {
    }

    private record ToolBatchOutcome(
            String observation,
            List<CommittedToolCall> committedCalls,
            List<CompletedToolExecution> completedExecutions
    ) {
    }

    private record ToolAttemptOutcome(
            List<CompletedToolExecution> attempts,
            CompletedToolExecution finalAttempt
    ) {
    }

    private record SynthesisOutcome(
            boolean completed,
            boolean continueResearch,
            String stopStatus,
            String reason,
            FinalReportCommit finalReport
    ) {
    }

    private record ReviewBundle(
            CitationReviewResult citation,
            FinancialComplianceReviewResult compliance,
            FinancialEvaluationResult evaluation,
            QualityGateDecision decision
    ) {
        private boolean passed() {
            return decision.passed();
        }

        private String reason() {
            return decision.summary();
        }
    }
}
