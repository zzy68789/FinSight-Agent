package com.zzy.finsight.financial;

import java.util.List;

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
