package com.zzy.finsight.dto.stock;


import java.time.LocalDateTime;

/**
 * 表示股票报告单个阶段的执行轨迹。
 * @param stage 当前执行阶段。
 * @param attemptNo 当前执行轮次。
 * @param status 当前状态。
 * @param durationMs 执行耗时，单位毫秒。
 * @param errorMessage 错误信息。
 * @param createdAt 创建时间。
 */
public record StockReportStageTrace(
        String stage,
        int attemptNo,
        String status,
        long durationMs,
        String errorMessage,
        LocalDateTime createdAt
) {
}
