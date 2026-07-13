package com.zzy.finsight.domain.stock;


/**
 * 表示金融数据源或工作阶段的执行结果。
 * @param stageName 阶段名称。
 * @param status 当前状态。
 * @param durationMs 执行耗时，单位毫秒。
 * @param evidenceCount 采集到的证据数量。
 * @param message 提示信息。
 */
public record FinancialAgentStageResult(
        String stageName,
        String status,
        long durationMs,
        int evidenceCount,
        String message
) {
}
