package com.zzy.finsight.component.review;

import com.zzy.finsight.component.marketdata.FinancialEvidenceValidator;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;


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
                "## 报告\n营收同比：20.00% [E1]\n\n## 引用与数据快照\n- [E1] 营业收入\n- [E2] 净利润\n- [E3] 经营现金流",
                snapshot(evidenceItems),
                List.of(metric("营收同比", "20.00", "OK"))
        );

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.reason()).isBlank();
    }

    @Test
    void failsWhenBodyAddsFinancialNumberAbsentFromMetricsAndEvidence() {
        List<FinancialEvidenceItem> evidenceItems = List.of(
                evidence("营业收入"),
                evidence("净利润"),
                evidence("经营现金流")
        );

        CitationReviewResult result = reviewer.review(
                "## 报告\n营收同比：20.00% [E1]\n预测增幅：99.99% [E1]\n\n"
                        + "## 引用与数据快照\n- [E1] 营业收入",
                snapshot(evidenceItems),
                List.of(metric("营收同比", "20.00", "OK"))
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.reason()).contains("NUMERIC_FACT_UNSUPPORTED", "99.99%");
    }

    @Test
    void failsWhenMetricAppearsOnlyInAppendixWithoutBodyCitation() {
        List<FinancialEvidenceItem> evidenceItems = List.of(
                evidence("营业收入"),
                evidence("净利润"),
                evidence("经营现金流")
        );

        CitationReviewResult result = reviewer.review(
                "## 报告\n营收同比：20.00%\n\n## 引用与数据快照\n- [E1] 营业收入 20.00%",
                snapshot(evidenceItems),
                List.of(metric("营收同比", "20.00", "OK"))
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.reason()).contains("BODY_CITATION_MISSING");
    }

    @Test
    void failsWhenInterimRoeDoesNotDeclareUnannualizedPeriod() {
        List<FinancialEvidenceItem> evidenceItems = List.of(
                evidence("NET_PROFIT", "", "20260331"),
                evidence("BEGINNING_EQUITY", "", "20260331"),
                evidence("ENDING_EQUITY", "", "20260331")
        );
        FinancialMetricResult roe = new FinancialMetricResult(
                "ROE",
                new BigDecimal("10.57"),
                "10.57%",
                "ROE公式",
                "OK",
                "",
                List.of("NET_PROFIT", "BEGINNING_EQUITY", "ENDING_EQUITY")
        );

        CitationReviewResult result = reviewer.review(
                "## 报告\nROE：10.57% [E1][E2][E3]\n\n## 引用与数据快照\n- [E1] 净利润",
                snapshot(evidenceItems),
                List.of(roe)
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.reason()).contains("PERIOD_SEMANTIC_INVALID");
    }

    @Test
    void rejectsMixedTurnoverSnapshots() {
        List<FinancialEvidenceItem> evidenceItems = List.of(
                evidence("NEWS_SUMMARY"),
                evidence("PE_TTM"),
                evidence("PB")
        );

        CitationReviewResult result = reviewer.review(
                "## 报告\n成交额分别为50亿元、20亿元，对应换手率为0.3%、0.1% [E1]\n\n"
                        + "## 引用与数据快照\n- [E1] 行情",
                snapshot(evidenceItems),
                List.of()
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.reason()).contains("MARKET_SNAPSHOT_MIXED");
    }

    @Test
    void failsClosedWhenEvidenceHasCriticalSemanticIssue() {
        List<FinancialEvidenceItem> evidenceItems = List.of(
                evidence("营业收入"),
                evidence("净利润"),
                evidence("经营现金流"),
                evidence("上年同期营业收入", FinancialEvidenceIssueCodes.PRIOR_PERIOD_MISMATCH)
        );

        CitationReviewResult result = reviewer.review(
                "## 报告\n\n## 引用与数据快照",
                snapshot(evidenceItems),
                List.of()
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.reason()).contains("EVIDENCE_SEMANTIC_INVALID", "PRIOR_PERIOD_MISMATCH");
    }

    @Test
    void emitsStableEvidenceConflictCodeForUnresolvedSourceConflict() {
        FinancialSnapshot validated = new FinancialEvidenceValidator().validate(snapshot(List.of(
                evidence("UPLOADED_REPORT", "来源A", "净利润", "100"),
                evidence("UPLOADED_REPORT", "来源B", "净利润", "120")
        )));

        CitationReviewResult result = reviewer.review("## 报告", validated, List.of());

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.code()).isEqualTo("EVIDENCE_CONFLICT");
        assertThat(result.reason()).contains("净利润@2025", "无法安全选择");
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
        return evidence(metricName, "");
    }

    private FinancialEvidenceItem evidence(String metricName, String issueCode) {
        return evidence(metricName, issueCode, "2025");
    }

    private FinancialEvidenceItem evidence(String metricName, String issueCode, String period) {
        return evidence("FINANCIAL_REPORT", "年报", metricName, BigDecimal.TEN.toPlainString(), issueCode, period);
    }

    private FinancialEvidenceItem evidence(
            String sourceType,
            String sourceName,
            String metricName,
            String value
    ) {
        return evidence(sourceType, sourceName, metricName, value, "", "2025");
    }

    private FinancialEvidenceItem evidence(
            String sourceType,
            String sourceName,
            String metricName,
            String value,
            String issueCode,
            String period
    ) {
        BigDecimal number = new BigDecimal(value);
        return new FinancialEvidenceItem(
                sourceType,
                sourceName,
                "",
                1,
                period,
                metricName,
                number,
                number,
                metricName + " " + value,
                new BigDecimal("0.9"),
                LocalDateTime.of(2026, 7, 3, 10, 0),
                issueCode
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
                "营收同比".equals(name) ? List.of("营业收入") : List.of(name)
        );
    }
}
