package com.zzy.finsight.dto.stock;


import java.time.LocalDateTime;

public record StockReportStageTrace(
        String stage,
        int attemptNo,
        String status,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
