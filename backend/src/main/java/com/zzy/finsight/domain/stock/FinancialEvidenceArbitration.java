package com.zzy.finsight.domain.stock;

import java.math.BigDecimal;
import java.util.List;

/**
 * 表示同一指标同一报告期的多来源证据仲裁结论。
 *
 * @param metricName 指标名称。
 * @param reportPeriod 报告期。
 * @param status 仲裁状态。
 * @param selectedSourceType 胜出来源类型，未决冲突时为空。
 * @param selectedSourceName 胜出来源名称，未决冲突时为空。
 * @param selectedValue 胜出的标准化数值，未决冲突时为空。
 * @param candidates 参与仲裁的候选来源。
 * @param reason 采用或拒绝来源的确定性原因。
 * @param policyVersion 仲裁策略版本。
 */
public record FinancialEvidenceArbitration(
        String metricName,
        String reportPeriod,
        Status status,
        String selectedSourceType,
        String selectedSourceName,
        BigDecimal selectedValue,
        List<FinancialEvidenceArbitrationCandidate> candidates,
        String reason,
        String policyVersion
) {
    public FinancialEvidenceArbitration {
        candidates = candidates == null ? List.of() : List.copyOf(candidates);
        reason = reason == null ? "" : reason;
        policyVersion = policyVersion == null ? "" : policyVersion;
    }

    /** 同一指标多来源数值的仲裁状态。 */
    public enum Status {
        /** 各来源数值处于允许误差内，已选定规范来源。 */
        CONSISTENT,
        /** 来源数值冲突，但可由唯一的更高优先级来源解决。 */
        RESOLVED,
        /** 最高优先级来源仍存在冲突，必须补证据或人工核对。 */
        CONFLICT
    }
}
