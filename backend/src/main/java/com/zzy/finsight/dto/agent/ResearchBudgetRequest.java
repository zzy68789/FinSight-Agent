package com.zzy.finsight.dto.agent;

/**
 * 表示单次 Research Agent 运行预算。
 * @param maxTurns 最大 Agent 决策轮次。
 * @param maxToolCalls 最大工具调用次数。
 * @param timeoutSeconds 整体运行超时秒数。
 */
public record ResearchBudgetRequest(
        Integer maxTurns,
        Integer maxToolCalls,
        Integer timeoutSeconds
) {
}
