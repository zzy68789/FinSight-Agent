package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示一次持久化的白名单工具调用。
 * @param id 主键标识。
 * @param taskId 任务标识。
 * @param turnId Agent轮次标识。
 * @param callId 工具调用标识。
 * @param toolName 工具名称。
 * @param argumentsHash 参数摘要。
 * @param argumentsJson 参数JSON。
 * @param resultJson 结果JSON。
 * @param status 调用状态。
 * @param attemptNo 尝试次数。
 * @param durationMs 调用耗时毫秒数。
 * @param errorCode 错误分类。
 * @param errorMessage 错误说明。
 * @param startedAt 开始时间。
 * @param completedAt 完成时间。
 */
public record AgentToolCallRecord(
        long id,
        long taskId,
        long turnId,
        String callId,
        String toolName,
        String argumentsHash,
        String argumentsJson,
        String resultJson,
        String status,
        int attemptNo,
        long durationMs,
        String errorCode,
        String errorMessage,
        LocalDateTime startedAt,
        LocalDateTime completedAt
) {
}
