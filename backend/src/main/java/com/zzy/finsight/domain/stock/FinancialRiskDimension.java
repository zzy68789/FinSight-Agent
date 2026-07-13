package com.zzy.finsight.domain.stock;


public record FinancialRiskDimension(
        String name,
        int score,
        int weight,
        String reason,
        String evidenceRef
) {
}
