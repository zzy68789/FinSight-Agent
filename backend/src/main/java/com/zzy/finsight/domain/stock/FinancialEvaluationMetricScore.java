package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;

/**
 * 表示单项金融评测指标得分。
 * @param metricName 指标名称。
 * @param score 评分。
 * @param threshold 评测通过阈值。
 * @param status 当前状态。
 * @param reason 状态原因。
 * @param category 指标分类。
 * @param gateLevel 门禁级别。
 * @param direction 指标优化方向。
 */
public record FinancialEvaluationMetricScore(
        String metricName,
        BigDecimal score,
        BigDecimal threshold,
        String status,
        String reason,
        Category category,
        GateLevel gateLevel,
        Direction direction
) {
    /** 保留旧调用方式，并默认创建规则类硬门禁指标。 */
    public FinancialEvaluationMetricScore(
            String metricName,
            BigDecimal score,
            BigDecimal threshold,
            String status,
            String reason
    ) {
        this(metricName, score, threshold, status, reason, Category.RULE, GateLevel.HARD, Direction.HIGHER_BETTER);
    }

    /** 指标所属评测层。 */
    public enum Category {
        RULE,
        RETRIEVAL,
        JUDGE,
        PERFORMANCE
    }

    /** 指标是否参与最终阻断。 */
    public enum GateLevel {
        HARD,
        ADVISORY
    }

    /** 指标数值的优化方向。 */
    public enum Direction {
        HIGHER_BETTER,
        LOWER_BETTER
    }
}
