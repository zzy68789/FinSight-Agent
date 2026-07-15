package com.zzy.finsight.component.evaluation;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * 表示当前评测结果与版本化基线的比较结果。
 * @param status 比较状态。
 * @param deltas 当前值减去基线值的差异。
 * @param failures 阻断回归原因。
 * @param warnings 非阻断告警。
 */
public record EvaluationBaselineComparison(
        String status,
        Map<String, BigDecimal> deltas,
        List<String> failures,
        List<String> warnings
) {
    public EvaluationBaselineComparison {
        deltas = deltas == null ? Map.of() : Map.copyOf(deltas);
        failures = failures == null ? List.of() : List.copyOf(failures);
        warnings = warnings == null ? List.of() : List.copyOf(warnings);
    }
}
