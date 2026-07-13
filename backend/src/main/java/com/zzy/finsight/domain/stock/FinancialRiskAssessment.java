package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;
import java.util.List;

/**
 * 表示金融风险综合评分与提示。
 * @param finalScore 综合风险分数。
 * @param riskLevel 风险等级。
 * @param dimensions 风险维度列表。
 * @param warnings 风险提示列表。
 */
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
