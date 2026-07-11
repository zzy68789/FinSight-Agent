package com.zzy.finsight.dto;

import java.time.LocalDateTime;

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
