package com.zzy.finsight.domain.stock;


public record FinancialComplianceIssue(
        String severity,
        String category,
        String description,
        String suggestion
) {
}
