package com.zzy.finsight.domain;

import java.time.LocalDateTime;

/**
 * 表示可恢复工作流任务的执行状态。
 * @param id 主键标识。
 * @param ownerId 所属用户标识。
 * @param threadId 会话标识。
 * @param status 当前状态。
 * @param stage 当前执行阶段。
 * @param attemptCount 累计执行次数。
 * @param requestPayload 任务请求参数。
 * @param lastError 最近一次错误信息。
 * @param heartbeatAt 任务最近心跳时间。
 * @param leaseOwner 任务租约持有者。
 * @param leaseUntil 任务租约到期时间。
 * @param updatedAt 更新时间。
 */
public record WorkflowTaskExecutionRecord(
        long id,
        long ownerId,
        String threadId,
        String status,
        String stage,
        int attemptCount,
        String requestPayload,
        String lastError,
        LocalDateTime heartbeatAt,
        String leaseOwner,
        LocalDateTime leaseUntil,
        LocalDateTime updatedAt
) {
}
