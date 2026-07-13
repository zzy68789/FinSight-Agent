package com.zzy.finsight.dto;

import java.time.LocalDateTime;

/**
 * 表示对外返回的 Agent 步骤日志。
 * @param id 主键标识。
 * @param taskId 关联任务标识。
 * @param stepName 步骤名称。
 * @param inputSnapshot 步骤输入快照。
 * @param outputSnapshot 步骤输出快照。
 * @param status 当前状态。
 * @param errorMessage 错误信息。
 * @param attemptNo 当前执行轮次。
 * @param durationMs 执行耗时，单位毫秒。
 * @param createdAt 创建时间。
 */
public record AgentStepLogResponse(
        long id,
        long taskId,
        String stepName,
        String inputSnapshot,
        String outputSnapshot,
        String status,
        String errorMessage,
        int attemptNo,
        long durationMs,
        LocalDateTime createdAt
) {
    public AgentStepLogResponse(
            long id,
            long taskId,
            String stepName,
            String inputSnapshot,
            String outputSnapshot,
            String status,
            String errorMessage,
            LocalDateTime createdAt
    ) {
        this(id, taskId, stepName, inputSnapshot, outputSnapshot, status, errorMessage, 1, 0L, createdAt);
    }
}
