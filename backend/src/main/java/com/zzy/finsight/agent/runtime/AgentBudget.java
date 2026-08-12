package com.zzy.finsight.agent.runtime;

import java.time.Duration;

/**
 * 表示服务端最终生效的 Agent 运行预算。
 * @param maxTurns 最大决策轮次。
 * @param maxToolCalls 最大工具调用次数。
 * @param maxReplans 最大重新规划次数。
 * @param maxEvidenceRecoveries 最大门禁补证据轮次。
 * @param maxStagnantTurns 最大连续无实质进展轮次。
 * @param maxParallelTools 单轮最大并行工具数。
 * @param maxReportRewrites 最大报告重写次数。
 * @param timeout 整体运行超时。
 * @param toolTimeout 单个工具超时。
 */
public record AgentBudget(
        int maxTurns,
        int maxToolCalls,
        int maxReplans,
        int maxEvidenceRecoveries,
        int maxStagnantTurns,
        int maxParallelTools,
        int maxReportRewrites,
        Duration timeout,
        Duration toolTimeout
) {
}
