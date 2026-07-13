package com.zzy.finsight.domain.stock;


import java.util.List;

/**
 * 表示金融报告评测样例集合。
 * @param name 评测集名称。
 * @param metrics 评测指标名称列表。
 * @param cases 评测用例列表。
 */
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
