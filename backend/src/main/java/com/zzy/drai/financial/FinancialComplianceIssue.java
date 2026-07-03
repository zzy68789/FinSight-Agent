package com.zzy.drai.financial;

public record FinancialComplianceIssue(
        String severity,
        String category,
        String description,
        String suggestion
) {
}
