package com.zzy.finsight.financial;

public record FinancialAgentStageResult(
        String stageName,
        String status,
        long durationMs,
        int evidenceCount,
        String message
) {
}
