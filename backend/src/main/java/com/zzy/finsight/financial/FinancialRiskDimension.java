package com.zzy.finsight.financial;

public record FinancialRiskDimension(
        String name,
        int score,
        int weight,
        String reason,
        String evidenceRef
) {
}
