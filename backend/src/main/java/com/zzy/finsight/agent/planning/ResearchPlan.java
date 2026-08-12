package com.zzy.finsight.agent.planning;

import java.util.List;

/**
 * 表示 Agent 可持续修订的研究计划。
 * @param goal 本次研究目标。
 * @param hypotheses 待验证的研究假设。
 * @param requiredEvidence 完成研究所需的证据类型。
 * @param completedItems 已完成的研究项。
 * @param unresolvedQuestions 尚未解决的问题。
 * @param plannerMode 计划来源模式，如 LLM 或 DETERMINISTIC_FALLBACK。
 * @param version Planner 策略版本。
 */
public record ResearchPlan(
        String goal,
        List<String> hypotheses,
        List<String> requiredEvidence,
        List<String> completedItems,
        List<String> unresolvedQuestions,
        String plannerMode,
        String version
) {
    public ResearchPlan {
        goal = goal == null ? "" : goal;
        hypotheses = copy(hypotheses);
        requiredEvidence = copy(requiredEvidence);
        completedItems = copy(completedItems);
        unresolvedQuestions = copy(unresolvedQuestions);
        plannerMode = plannerMode == null || plannerMode.isBlank() ? "LLM" : plannerMode;
        version = version == null || version.isBlank() ? "research-planner-v1" : version;
    }

    private static List<String> copy(List<String> values) {
        return values == null ? List.of() : List.copyOf(values);
    }
}
