package com.zzy.finsight.financial;

import java.math.BigDecimal;
import java.util.List;

public record FinancialComplianceReviewResult(
        String status,
        BigDecimal score,
        List<FinancialComplianceIssue> issues
) {
    public FinancialComplianceReviewResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
