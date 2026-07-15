package com.zzy.finsight.component.evaluation;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 比较核心检索、Judge 和性能指标与历史基线的变化。
 */
@Component
public class EvaluationBaselineComparator {
    private static final BigDecimal QUALITY_REGRESSION_LIMIT = new BigDecimal("0.05");
    private static final BigDecimal PERFORMANCE_RATIO_LIMIT = new BigDecimal("1.20");
    private static final Set<String> CORE_RETRIEVAL_METRICS = Set.of("recall_at_3", "mrr", "ndcg_at_3");

    /** 按分层门禁策略比较当前聚合指标与版本化基线。 */
    public EvaluationBaselineComparison compare(
            Map<String, BigDecimal> current,
            Map<String, BigDecimal> baseline
    ) {
        Map<String, BigDecimal> currentValues = current == null ? Map.of() : current;
        Map<String, BigDecimal> baselineValues = baseline == null ? Map.of() : baseline;
        Map<String, BigDecimal> deltas = new LinkedHashMap<>();
        List<String> failures = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        for (Map.Entry<String, BigDecimal> entry : baselineValues.entrySet()) {
            String metric = entry.getKey();
            BigDecimal baselineValue = entry.getValue();
            BigDecimal currentValue = currentValues.get(metric);
            if (baselineValue == null || currentValue == null) {
                continue;
            }
            BigDecimal delta = currentValue.subtract(baselineValue);
            deltas.put(metric, delta);
            if (CORE_RETRIEVAL_METRICS.contains(metric) && delta.compareTo(QUALITY_REGRESSION_LIMIT.negate()) < 0) {
                failures.add(metric + " 相对基线下降 " + delta.abs());
            } else if (metric.startsWith("judge_") && delta.compareTo(QUALITY_REGRESSION_LIMIT.negate()) < 0) {
                warnings.add(metric + " 相对基线下降 " + delta.abs());
            } else if (isPerformanceMetric(metric)
                    && baselineValue.compareTo(BigDecimal.ZERO) > 0
                    && currentValue.compareTo(baselineValue.multiply(PERFORMANCE_RATIO_LIMIT)) > 0) {
                warnings.add(metric + " 相对基线上升超过 20%");
            }
        }
        String status = !failures.isEmpty() ? "FAIL" : warnings.isEmpty() ? "PASS" : "WARN";
        return new EvaluationBaselineComparison(status, deltas, failures, warnings);
    }

    private boolean isPerformanceMetric(String metric) {
        return "p95_duration_ms".equals(metric) || "average_total_tokens".equals(metric);
    }
}
