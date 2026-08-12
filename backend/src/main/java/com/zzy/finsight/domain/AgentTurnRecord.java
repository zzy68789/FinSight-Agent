package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示一次持久化的 Agent 决策轮次。
 * @param id 主键标识。
 * @param taskId 任务标识。
 * @param turnNo 决策轮次。
 * @param phase 当前阶段。
 * @param actionType 动作类型。
 * @param actionJson 动作JSON。
 * @param observationSummary 观察摘要。
 * @param status 轮次状态。
 * @param inputTokens 输入Token数。
 * @param outputTokens 输出Token数。
 * @param durationMs 执行耗时毫秒数。
 * @param createdAt 创建时间。
 */
public record AgentTurnRecord(
        long id,
        long taskId,
        int turnNo,
        String phase,
        String actionType,
        String actionJson,
        String observationSummary,
        String status,
        int inputTokens,
        int outputTokens,
        long durationMs,
        LocalDateTime createdAt
) {
}
