package com.zzy.drai.domain;

import java.time.LocalDateTime;

public record AgentStepLogRecord(
        long id,
        long taskId,
        String stepName,
        String inputSnapshot,
        String outputSnapshot,
        String status,
        String errorMessage,
        LocalDateTime createdAt
) {
}
