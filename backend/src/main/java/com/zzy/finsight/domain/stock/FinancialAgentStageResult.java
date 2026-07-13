package com.zzy.finsight.domain.stock;


public record FinancialAgentStageResult(
        String stageName,
        String status,
        long durationMs,
        int evidenceCount,
        String message
) {
}
