package com.zzy.finsight.domain.stock;


/**
 * 表示一个金融风险评分维度。
 * @param name 风险维度名称。
 * @param score 评分。
 * @param weight 风险维度权重。
 * @param reason 状态原因。
 * @param evidenceRef 关联证据标识。
 */
public record FinancialRiskDimension(
        String name,
        int score,
        int weight,
        String reason,
        String evidenceRef
) {
}
