package com.zzy.drai.financial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CitationReviewerTest {

    private final CitationReviewer reviewer = new CitationReviewer();

    @Test
    void acceptsNumericValuesWithinTolerance() {
        assertThat(reviewer.withinTolerance(new BigDecimal("100"), new BigDecimal("100.4"))).isTrue();
        assertThat(reviewer.withinTolerance(new BigDecimal("100"), new BigDecimal("100.6"))).isFalse();
        assertThat(reviewer.withinTolerance(new BigDecimal("0.01"), new BigDecimal("0.019"))).isTrue();
    }

    @Test
    void failsWhenEvidenceIsInsufficient() {
        CitationReviewResult result = reviewer.review(
                "## 报告\n营收同比：20.00%",
                snapshot(List.of(evidence("营业收入"))),
                List.of(metric("营收同比", "20.00", "OK"))
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.reason()).contains("EVIDENCE_INSUFFICIENT");
    }

    @Test
    void passesWhenMetricsAndEvidenceAreTraceable() {
        List<FinancialEvidenceItem> evidenceItems = List.of(
                evidence("营业收入"),
                evidence("净利润"),
                evidence("经营现金流")
        );

        CitationReviewResult result = reviewer.review(
                "## 报告\n营收同比：20.00%\n\n## 引用与数据快照\n- 营业收入\n- 净利润\n- 经营现金流",
                snapshot(evidenceItems),
                List.of(metric("营收同比", "20.00", "OK"))
        );

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.reason()).isBlank();
    }

    private FinancialSnapshot snapshot(List<FinancialEvidenceItem> evidenceItems) {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "2025",
                "hybrid",
                evidenceItems,
                LocalDateTime.of(2026, 7, 3, 10, 0)
        );
    }

    private FinancialEvidenceItem evidence(String metricName) {
        return new FinancialEvidenceItem(
                "FINANCIAL_REPORT",
                "年报",
                "",
                1,
                "2025",
                metricName,
                BigDecimal.TEN,
                BigDecimal.TEN,
                metricName + " 10",
                new BigDecimal("0.9"),
                LocalDateTime.of(2026, 7, 3, 10, 0),
                ""
        );
    }

    private FinancialMetricResult metric(String name, String value, String status) {
        return new FinancialMetricResult(
                name,
                new BigDecimal(value),
                value + "%",
                name + "公式",
                status,
                "",
                List.of(name)
        );
    }
}
