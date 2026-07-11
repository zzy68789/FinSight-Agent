package com.zzy.finsight.financial;

public record FinancialComplianceIssue(
        String severity,
        String category,
        String description,
        String suggestion
) {
}
