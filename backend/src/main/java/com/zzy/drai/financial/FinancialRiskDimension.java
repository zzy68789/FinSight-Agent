package com.zzy.drai.financial;

public record FinancialRiskDimension(
        String name,
        int score,
        int weight,
        String reason,
        String evidenceRef
) {
}
