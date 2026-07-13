package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;

/**
 * 表示单项金融评测指标得分。
 * @param metricName 指标名称。
 * @param score 评分。
 * @param threshold 评测通过阈值。
 * @param status 当前状态。
 * @param reason 状态原因。
 */
public record FinancialEvaluationMetricScore(
        String metricName,
        BigDecimal score,
        BigDecimal threshold,
        String status,
        String reason
) {
}
