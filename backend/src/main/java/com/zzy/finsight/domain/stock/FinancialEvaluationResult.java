package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;
import java.util.List;

/**
 * 表示金融报告自动评测结果。
 * @param ticker 证券代码。
 * @param companyName 公司名称。
 * @param overallScore 综合评测分数。
 * @param status 当前状态。
 * @param metricScores 各项评测分数。
 * @param failedReasons 评测失败原因列表。
 */
public record FinancialEvaluationResult(
        String ticker,
        String companyName,
        BigDecimal overallScore,
        String status,
        List<FinancialEvaluationMetricScore> metricScores,
        List<String> failedReasons
) {
    public FinancialEvaluationResult {
        metricScores = metricScores == null ? List.of() : List.copyOf(metricScores);
        failedReasons = failedReasons == null ? List.of() : List.copyOf(failedReasons);
    }
}
