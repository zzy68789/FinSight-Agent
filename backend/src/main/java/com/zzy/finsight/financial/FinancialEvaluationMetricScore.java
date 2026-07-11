package com.zzy.finsight.financial;

import java.math.BigDecimal;

public record FinancialEvaluationMetricScore(
        String metricName,
        BigDecimal score,
        BigDecimal threshold,
        String status,
        String reason
) {
}
