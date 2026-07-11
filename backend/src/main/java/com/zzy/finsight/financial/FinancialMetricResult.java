package com.zzy.finsight.financial;

import java.math.BigDecimal;
import java.util.List;

public record FinancialMetricResult(
        String metricName,
        BigDecimal value,
        String displayValue,
        String formula,
        String formulaVersion,
        String status,
        String reason,
        List<String> evidenceRefs
) {
    public FinancialMetricResult {
        formulaVersion = formulaVersion == null || formulaVersion.isBlank() ? "v1" : formulaVersion;
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public FinancialMetricResult(
            String metricName,
            BigDecimal value,
            String displayValue,
            String formula,
            String status,
            String reason,
            List<String> evidenceRefs
    ) {
        this(metricName, value, displayValue, formula, "v1", status, reason, evidenceRefs);
    }
}
