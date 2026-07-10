package com.zzy.drai.domain;

import java.time.LocalDateTime;

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
