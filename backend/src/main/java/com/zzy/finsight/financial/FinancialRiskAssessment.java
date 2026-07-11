package com.zzy.finsight.financial;

import java.math.BigDecimal;
import java.util.List;

public record FinancialRiskAssessment(
        BigDecimal finalScore,
        String riskLevel,
        List<FinancialRiskDimension> dimensions,
        List<String> warnings
) {
    public FinancialRiskAssessment {
        dimensions = dimensions == null ? List.of() : List.copyOf(dimensions);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
