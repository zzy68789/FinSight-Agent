package com.zzy.drai.financial;

import java.math.BigDecimal;
import java.util.List;

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
