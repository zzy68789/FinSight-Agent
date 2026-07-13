package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;
import java.util.List;

/**
 * 表示一个金融指标的计算结果。
 * @param metricName 指标名称。
 * @param value 指标数值。
 * @param displayValue 格式化展示值。
 * @param formula 指标计算公式。
 * @param formulaVersion 公式版本。
 * @param status 当前状态。
 * @param reason 状态原因。
 * @param evidenceRefs 关联证据标识列表。
 */
public record FinancialMetricResult(
        String metricName,
        BigDecimal value,
        String displayValue,
        String formula,
        String formulaVersion,
        String status,
        String reason,
        List<String> evidenceRefs
) {
    public FinancialMetricResult {
        formulaVersion = formulaVersion == null || formulaVersion.isBlank() ? "v1" : formulaVersion;
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }

    public FinancialMetricResult(
            String metricName,
            BigDecimal value,
            String displayValue,
            String formula,
            String status,
            String reason,
            List<String> evidenceRefs
    ) {
        this(metricName, value, displayValue, formula, "v1", status, reason, evidenceRefs);
    }
}
