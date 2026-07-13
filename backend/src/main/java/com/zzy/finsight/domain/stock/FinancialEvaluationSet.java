package com.zzy.finsight.domain.stock;


import java.util.List;

public record FinancialEvaluationSet(
        String name,
        List<String> metrics,
        List<FinancialEvaluationCase> cases
) {
    public FinancialEvaluationSet {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
        cases = cases == null ? List.of() : List.copyOf(cases);
    }
}
