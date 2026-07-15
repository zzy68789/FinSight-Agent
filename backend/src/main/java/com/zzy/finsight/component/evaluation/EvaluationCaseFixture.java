package com.zzy.finsight.component.evaluation;

import java.util.List;

/**
 * 表示一个自包含的冻结金融报告评测样例。
 * @param caseId 样例标识。
 * @param scenario 场景标签。
 * @param assetType 资产类型。
 * @param ticker 证券代码。
 * @param exchange 交易所。
 * @param companyName 证券名称。
 * @param industry 行业。
 * @param reportPeriod 报告期。
 * @param metricName 冻结指标名称。
 * @param metricDisplayValue 冻结指标展示值。
 * @param evidenceExcerpt 冻结证据正文。
 * @param evidenceIssueCode 证据问题编码。
 * @param requiredKeypoints 必须覆盖的关键点。
 * @param forbiddenClaims 禁止出现的结论。
 * @param expectedStatus 线上规则预期状态。
 * @param expectedMetricMatch 报告数值是否应落在允许误差内。
 * @param report 冻结报告正文。
 */
public record EvaluationCaseFixture(
        String caseId,
        String scenario,
        String assetType,
        String ticker,
        String exchange,
        String companyName,
        String industry,
        String reportPeriod,
        String metricName,
        String metricDisplayValue,
        String evidenceExcerpt,
        String evidenceIssueCode,
        List<String> requiredKeypoints,
        List<String> forbiddenClaims,
        String expectedStatus,
        Boolean expectedMetricMatch,
        String report
) {
    public EvaluationCaseFixture {
        requiredKeypoints = requiredKeypoints == null ? List.of() : List.copyOf(requiredKeypoints);
        forbiddenClaims = forbiddenClaims == null ? List.of() : List.copyOf(forbiddenClaims);
        expectedMetricMatch = expectedMetricMatch == null ? Boolean.TRUE : expectedMetricMatch;
    }
}
