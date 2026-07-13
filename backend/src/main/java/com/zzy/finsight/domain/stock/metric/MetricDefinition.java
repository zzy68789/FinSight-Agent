package com.zzy.finsight.domain.stock.metric;


import java.util.List;

/**
 * 表示金融指标的公式、版本和输入依赖。
 * @param code 指标编码。
 * @param displayName 展示名称。
 * @param formula 指标计算公式。
 * @param formulaVersion 公式版本。
 * @param dependencies 指标依赖项。
 */
public record MetricDefinition(
        String code,
        String displayName,
        String formula,
        String formulaVersion,
        List<String> dependencies
) {
    public MetricDefinition {
        dependencies = dependencies == null ? List.of() : List.copyOf(dependencies);
    }
}
