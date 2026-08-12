package com.zzy.finsight.domain.stock;

import java.math.BigDecimal;

/**
 * 表示一次证据仲裁中的候选来源及其确定性决策。
 *
 * @param sourceType 数据源类型。
 * @param sourceName 数据源名称。
 * @param normalizedValue 标准化后的候选数值。
 * @param sourcePriority 来源优先级，数值越大越优先。
 * @param confidence 证据置信度。
 * @param decision 候选来源的仲裁决策。
 */
public record FinancialEvidenceArbitrationCandidate(
        String sourceType,
        String sourceName,
        BigDecimal normalizedValue,
        int sourcePriority,
        BigDecimal confidence,
        Decision decision
) {
    /** 候选来源在本轮仲裁中的处理结果。 */
    public enum Decision {
        /** 被选为指标计算的唯一确定性输入。 */
        SELECTED,
        /** 数值与胜出来源一致，仅作为旁证保留。 */
        CORROBORATING,
        /** 数值冲突且来源优先级较低，未被采用。 */
        REJECTED,
        /** 最高优先级来源之间仍冲突，无法安全采用。 */
        CONFLICT
    }
}
