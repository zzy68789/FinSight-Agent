package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialRiskDimension;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialRiskScorerTest {

    private final FinancialRiskScorer service = new FinancialRiskScorer();

    @Test
    void computesWeightedRiskScoreFromFinancialMetricsAndEvidenceCoverage() {
        List<FinancialMetricResult> metrics = List.of(
                metric("ROE", "30.00", "OK"),
                metric("资产负债率", "25.00", "OK"),
                metric("经营现金流 / 净利润", "1.50", "OK")
        );

        FinancialRiskAssessment assessment = service.assess(metrics, List.of(
                evidence("NEWS_SUMMARY", "政策环境平稳，未见重大利空。"),
                evidence("TECHNICAL_SIGNAL", "RSI 52，均线震荡。"),
                evidence("SENTIMENT_SIGNAL", "市场情绪中性。")
        ));

        assertThat(assessment.finalScore()).isEqualByComparingTo("3.60");
        assertThat(assessment.riskLevel()).isEqualTo("中等风险");
        assertThat(assessment.dimensions()).hasSize(5);
        assertThat(assessment.dimensions().get(0).name()).isEqualTo("基本面风险");
        assertThat(assessment.dimensions().get(0).score()).isEqualTo(2);
    }

    @Test
    void marksMissingTechnicalSentimentNewsAndMarketInputsAsTraceableGaps() {
        FinancialRiskAssessment assessment = service.assess(List.of(), List.of());

        assertThat(assessment.finalScore()).isEqualByComparingTo("6.00");
        assertThat(assessment.riskLevel()).isEqualTo("中等风险");
        assertThat(assessment.dimensions())
                .extracting(FinancialRiskDimension::reason)
                .allMatch(reason -> reason.contains("缺少") || reason.contains("指标"));
    }

    @Test
    void doesNotCompareInterimRoeWithAnnualThreshold() {
        List<FinancialMetricResult> metrics = List.of(
                metric("ROE", "30.00", "OK"),
                metric("资产负债率", "25.00", "OK"),
                metric("经营现金流 / 净利润", "1.50", "OK")
        );

        FinancialRiskAssessment assessment = service.assess(metrics, List.of(
                evidence("OPERATING_REVENUE", "20260331", "一季度营业收入 100 亿元。")
        ));

        assertThat(assessment.dimensions().get(0).reason()).contains("阶段性ROE未年化");
        assertThat(assessment.dimensions().get(0).score()).isEqualTo(3);
    }

    private FinancialMetricResult metric(String name, String value, String status) {
        return new FinancialMetricResult(
                name,
                new BigDecimal(value),
                value + ("经营现金流 / 净利润".equals(name) ? "" : "%"),
                name + "公式",
                status,
                "",
                List.of(name)
        );
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return evidence(metricName, "latest", excerpt);
    }

    private FinancialEvidenceItem evidence(String metricName, String period, String excerpt) {
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                "公开资料",
                "",
                null,
                period,
                metricName,
                null,
                null,
                excerpt,
                new BigDecimal("0.8"),
                java.time.LocalDateTime.of(2026, 7, 3, 10, 0),
                ""
        );
    }
}
