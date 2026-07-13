package com.zzy.finsight.domain.stock;


/**
 * 表示报告中的一项金融合规问题。
 * @param severity 问题严重程度。
 * @param category 问题分类。
 * @param description 问题描述。
 * @param suggestion 修正建议。
 */
public record FinancialComplianceIssue(
        String severity,
        String category,
        String description,
        String suggestion
) {
}
