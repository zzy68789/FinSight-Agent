package com.zzy.finsight.agent.planning;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 提供给 Planner 的固定大小状态投影，避免把完整 AgentState 和历史参数值重复注入 Prompt。
 * @param question 当前研究问题。
 * @param researchIntent 当前研究意图。
 * @param intentInstruction 当前研究意图对应的 Planner 重点。
 * @param plan 当前计划的必要字段。
 * @param turnNo 当前轮次。
 * @param toolCallCount 已用工具次数。
 * @param completedTools 已完成工具。
 * @param recentToolCalls 最近工具名称与参数键。
 * @param recentObservations 最近短观察。
 * @param subjectResolved 是否已解析证券主体。
 * @param effectiveEvidenceCount 当前有效证据数量。
 * @param metricsReady 指标是否齐备。
 * @param riskReady 风险结果是否齐备。
 * @param lastReviewReason 最近门禁反馈。
 * @param evidenceRecovery 当前补证据指令。
 */
public record PlannerContextProjection(
        String question,
        String researchIntent,
        String intentInstruction,
        Map<String, Object> plan,
        int turnNo,
        int toolCallCount,
        List<String> completedTools,
        List<Map<String, Object>> recentToolCalls,
        List<String> recentObservations,
        boolean subjectResolved,
        long effectiveEvidenceCount,
        boolean metricsReady,
        boolean riskReady,
        String lastReviewReason,
        Object evidenceRecovery
) {
    private static final int RECENT_LIMIT = 5;

    public PlannerContextProjection {
        question = question == null ? "" : question;
        researchIntent = researchIntent == null ? "COMPREHENSIVE" : researchIntent;
        intentInstruction = intentInstruction == null ? "" : intentInstruction;
        plan = plan == null ? Map.of() : Map.copyOf(plan);
        completedTools = completedTools == null ? List.of() : List.copyOf(completedTools);
        recentToolCalls = recentToolCalls == null ? List.of() : List.copyOf(recentToolCalls);
        recentObservations = recentObservations == null ? List.of() : List.copyOf(recentObservations);
        lastReviewReason = lastReviewReason == null ? "" : lastReviewReason;
    }

    /** 从完整运行状态提取固定大小、面向决策的上下文。 */
    public static PlannerContextProjection from(AgentState state) {
        ResearchPlan currentPlan = state.getPlan();
        Map<String, Object> plan = new LinkedHashMap<>();
        if (currentPlan != null) {
            plan.put("goal", currentPlan.goal());
            plan.put("requiredEvidence", currentPlan.requiredEvidence());
            plan.put("completedItems", currentPlan.completedItems());
            plan.put("unresolvedQuestions", currentPlan.unresolvedQuestions());
        }
        List<Map<String, Object>> calls = tail(state.getToolInvocationHistory(), RECENT_LIMIT).stream()
                .map(invocation -> Map.<String, Object>of(
                        "toolName", invocation.toolName(),
                        "argumentKeys", invocation.arguments().keySet().stream().sorted().toList()
                ))
                .toList();
        long evidenceCount = state.getSnapshot() == null ? 0L
                : state.getSnapshot().evidenceItems().stream().filter(FinancialEvidenceItem::effective).count();
        return new PlannerContextProjection(
                state.getRequest() == null ? "" : state.getRequest().getResearchQuestion(),
                state.getRequest() == null ? "COMPREHENSIVE" : state.getRequest().getResearchIntent().name(),
                state.getRequest() == null ? "" : state.getRequest().getResearchIntent().plannerInstruction(),
                plan,
                state.getTurnNo(),
                state.getToolCallCount(),
                state.getCompletedTools().stream().sorted().toList(),
                calls,
                tail(state.getObservations(), RECENT_LIMIT),
                state.getSubject() != null,
                evidenceCount,
                !state.getMetrics().isEmpty(),
                state.getRiskAssessment() != null,
                state.getLastReviewReason(),
                state.getEvidenceRecoveryDirective()
        );
    }

    private static <T> List<T> tail(List<T> values, int limit) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream().skip(Math.max(0, values.size() - limit)).toList();
    }
}
