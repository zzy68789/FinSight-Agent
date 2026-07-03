package com.zzy.drai.financial;

import java.math.BigDecimal;
import java.util.List;

public record FinancialMetricResult(
        String metricName,
        BigDecimal value,
        String displayValue,
        String formula,
        String status,
        String reason,
        List<String> evidenceRefs
) {
    public FinancialMetricResult {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
