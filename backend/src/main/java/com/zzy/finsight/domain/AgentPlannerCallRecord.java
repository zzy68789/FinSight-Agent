package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示一次持久化 Planner 决策及其模型、结构和成本元数据。
 * @param id 记录标识。
 * @param taskId 任务标识。
 * @param decisionType 决策类型。
 * @param requestedModel 请求模型档位。
 * @param actualModel 实际模型名称。
 * @param inputTokens 输入Token数。
 * @param outputTokens 输出Token数。
 * @param durationMs 耗时毫秒数。
 * @param structureAttempts 结构化尝试次数。
 * @param structuredValid 是否结构合法。
 * @param routeCorrect 是否通过确定性路由校验。
 * @param degraded 是否使用降级决策。
 * @param degradedReason 降级原因。
 * @param createdAt 创建时间。
 */
public record AgentPlannerCallRecord(
        long id,
        long taskId,
        String decisionType,
        String requestedModel,
        String actualModel,
        int inputTokens,
        int outputTokens,
        long durationMs,
        int structureAttempts,
        boolean structuredValid,
        boolean routeCorrect,
        boolean degraded,
        String degradedReason,
        LocalDateTime createdAt
) {
}
