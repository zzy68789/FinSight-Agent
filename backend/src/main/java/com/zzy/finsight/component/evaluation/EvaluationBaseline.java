package com.zzy.finsight.component.evaluation;

import java.math.BigDecimal;
import java.util.Map;

/**
 * 表示随代码版本维护的评测聚合指标基线。
 * @param version 基线版本。
 * @param metrics 聚合指标值。
 */
public record EvaluationBaseline(String version, Map<String, BigDecimal> metrics) {
    public EvaluationBaseline {
        version = version == null ? "" : version;
        metrics = metrics == null ? Map.of() : Map.copyOf(metrics);
    }
}
