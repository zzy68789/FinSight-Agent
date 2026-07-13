package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.FinancialEvaluationCase;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationSet;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialEvaluatorTest {

    private final FinancialEvaluator service = new FinancialEvaluator(new ObjectMapper());

    @Test
    void loadsDefaultFinancialEvaluationSetFromClasspath() {
        FinancialEvaluationSet evalSet = service.loadDefaultSet();

        assertThat(evalSet.name()).isEqualTo("A股投研报告评测集 v1");
        assertThat(evalSet.metrics()).containsExactly(
                "claim_support_rate",
                "unsupported_claim_rate",
                "contradiction_rate",
                "numeric_consistency_rate",
                "citation_hit_rate",
                "keypoint_coverage"
        );
        assertThat(evalSet.cases()).hasSize(10);
        assertThat(evalSet.cases())
                .filteredOn(item -> "600519".equals(item.ticker()))
                .singleElement()
                .satisfies(item -> {
                    assertThat(item.name()).isEqualTo("贵州茅台");
                    assertThat(item.requiredKeypoints()).contains("公司概况", "财务表现", "主要风险");
                });
    }

    @Test
    void evaluatesReportWithSupportedClaimsNumbersCitationsAndKeypoints() {
        FinancialEvaluationCase evalCase = new FinancialEvaluationCase(
                "600519",
                "贵州茅台",
                "latest",
                List.of("公司概况", "财务表现", "主要风险")
        );
        FinancialSnapshot snapshot = snapshot(
                evidence("NEWS_SUMMARY", "公司经营平稳，未见重大利空。"),
                evidence("TECHNICAL_SIGNAL", "RSI 52，均线震荡。")
        );
        List<FinancialMetricResult> metrics = List.of(
                metric("ROE", "30.00%"),
                metric("资产负债率", "25.00%")
        );
        String report = """
                # 600519.SH A股投研报告

                > 仅作研究辅助，不构成投资建议。

                ## 1. 公司概况
                贵州茅台是本次评测样例公司。

                ## 3. 财务表现
                ROE：30.00%。资产负债率：25.00%。

                ## 7. 主要风险
                主要风险来自数据缺口和市场波动。

                ## 引用与数据快照
                - [E1] PUBLIC_MARKET / 新闻 / latest / NEWS_SUMMARY / OK：公司经营平稳，未见重大利空。
                - [E2] PUBLIC_MARKET / 技术 / latest / TECHNICAL_SIGNAL / OK：RSI 52，均线震荡。
                """;

        FinancialEvaluationResult result = service.evaluate(evalCase, report, snapshot, metrics);

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.overallScore()).isGreaterThanOrEqualTo(new BigDecimal("0.80"));
        assertThat(score(result, "keypoint_coverage")).isEqualByComparingTo("1.00");
        assertThat(score(result, "numeric_consistency_rate")).isEqualByComparingTo("1.00");
        assertThat(score(result, "citation_hit_rate")).isEqualByComparingTo("1.00");
    }

    @Test
    void evaluatesDefaultCaseOnlyWhenTickerIsInEvalSet() {
        FinancialSnapshot knownSnapshot = snapshot(evidence("NEWS_SUMMARY", "公司经营平稳，未见重大利空。"));
        String report = """
                ## 公司概况
                贵州茅台。
                ## 财务表现
                ROE：30.00%。
                ## 盈利能力与现金流
                经营现金流覆盖净利润。
                ## 主要风险
                主要风险来自数据缺口。
                ## 引用与数据快照
                - [E1] PUBLIC_MARKET / 新闻 / latest / NEWS_SUMMARY / OK：公司经营平稳，未见重大利空。
                """;

        Optional<FinancialEvaluationResult> knownResult = service.evaluateDefaultCase(
                report,
                knownSnapshot,
                List.of(metric("ROE", "30.00%"))
        );
        Optional<FinancialEvaluationResult> unknownResult = service.evaluateDefaultCase(
                report,
                new FinancialSnapshot(
                        new StockSubject("123456", "SZ", "123456.SZ", "未知公司", "未知行业"),
                        "latest",
                        "hybrid",
                        List.of(),
                        LocalDateTime.of(2026, 7, 3, 10, 0)
                ),
                List.of(metric("ROE", "30.00%"))
        );

        assertThat(knownResult).isPresent();
        assertThat(knownResult.orElseThrow().ticker()).isEqualTo("600519");
        assertThat(unknownResult).isEmpty();
    }

    @Test
    void citationHitUsesOriginalEvidenceIndexWhenMissingEvidencePrecedesEffectiveEvidence() {
        FinancialEvaluationCase evalCase = new FinancialEvaluationCase(
                "600519",
                "贵州茅台",
                "latest",
                List.of("公司概况", "财务表现", "主要风险")
        );
        FinancialSnapshot snapshot = snapshot(
                new FinancialEvidenceItem(
                        "PUBLIC_MARKET",
                        "缺失项",
                        "",
                        null,
                        "latest",
                        "DATA_MISSING",
                        null,
                        null,
                        "公开行情缺失。",
                        BigDecimal.ZERO,
                        LocalDateTime.of(2026, 7, 3, 10, 0),
                        "DATA_MISSING"
                ),
                evidence("NEWS_SUMMARY", "公司经营平稳。"),
                evidence("TECHNICAL_SIGNAL", "RSI 52，均线震荡。")
        );
        String report = """
                ## 公司概况
                贵州茅台。
                ## 财务表现
                ROE：30.00%。
                ## 主要风险
                数据缺口和市场波动。
                ## 引用与数据快照
                - [E2] PUBLIC_MARKET / 新闻 / latest / NEWS_SUMMARY / OK：公司经营平稳。
                - [E3] PUBLIC_MARKET / 技术 / latest / TECHNICAL_SIGNAL / OK：RSI 52，均线震荡。
                """;

        FinancialEvaluationResult result = service.evaluate(evalCase, report, snapshot, List.of(metric("ROE", "30.00%")));

        assertThat(score(result, "citation_hit_rate")).isEqualByComparingTo("1.00");
    }

    @Test
    void failsReportWithUnsupportedRecommendationAndGuaranteedReturn() {
        FinancialEvaluationCase evalCase = new FinancialEvaluationCase(
                "600519",
                "贵州茅台",
                "latest",
                List.of("公司概况", "财务表现", "主要风险")
        );
        String report = """
                ## 公司概况
                贵州茅台。

                ## 财务表现
                ROE：30.00%。

                可以直接买入，保证收益。
                """;

        FinancialEvaluationResult result = service.evaluate(evalCase, report, snapshot(), List.of(metric("ROE", "30.00%")));

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(score(result, "unsupported_claim_rate")).isEqualByComparingTo("0.00");
        assertThat(result.failedReasons()).anyMatch(reason -> reason.contains("无依据") || reason.contains("保证收益"));
    }

    private BigDecimal score(FinancialEvaluationResult result, String metricName) {
        return result.metricScores().stream()
                .filter(item -> metricName.equals(item.metricName()))
                .findFirst()
                .orElseThrow()
                .score();
    }

    private FinancialMetricResult metric(String name, String displayValue) {
        return new FinancialMetricResult(
                name,
                null,
                displayValue,
                name + "公式",
                "OK",
                "",
                List.of(name)
        );
    }

    private FinancialSnapshot snapshot(FinancialEvidenceItem... items) {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "latest",
                "hybrid",
                List.of(items),
                LocalDateTime.of(2026, 7, 3, 10, 0)
        );
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                metricName,
                "",
                null,
                "latest",
                metricName,
                null,
                null,
                excerpt,
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 7, 3, 10, 0),
                ""
        );
    }
}
