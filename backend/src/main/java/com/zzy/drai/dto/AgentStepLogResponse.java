package com.zzy.drai.dto;

import java.time.LocalDateTime;

public record AgentStepLogResponse(
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
