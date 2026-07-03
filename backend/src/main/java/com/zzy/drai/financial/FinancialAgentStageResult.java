package com.zzy.drai.financial;

public record FinancialAgentStageResult(
        String stageName,
        String status,
        long durationMs,
        int evidenceCount,
        String message
) {
}
