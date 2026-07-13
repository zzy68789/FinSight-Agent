package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;
import java.util.List;

/**
 * 表示金融合规审查结果。
 * @param status 当前状态。
 * @param score 评分。
 * @param issues 合规问题列表。
 */
public record FinancialComplianceReviewResult(
        String status,
        BigDecimal score,
        List<FinancialComplianceIssue> issues
) {
    public FinancialComplianceReviewResult {
        issues = issues == null ? List.of() : List.copyOf(issues);
    }
}
