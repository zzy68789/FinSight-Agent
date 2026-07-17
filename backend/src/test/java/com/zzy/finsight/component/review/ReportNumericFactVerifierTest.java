package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ReportNumericFactVerifierTest {
    private final ReportNumericFactVerifier verifier = new ReportNumericFactVerifier();

    @Test
    void rejectsInventedPercentageEvenWhenKnownMetricIsAlsoPresent() {
        ReportNumericFactVerifier.Verification result = verifier.verify(
                "ROE 为 30.00%，预计增长 99.99% [E1]。",
                snapshot(evidence("ROE 为 30.00%")),
                List.of(metric("ROE", "30.00%"))
        );

        assertThat(result.totalClaims()).isEqualTo(2);
        assertThat(result.supportedClaims()).isEqualTo(1);
        assertThat(result.unsupportedClaims()).extracting(ReportNumericFactVerifier.NumericClaim::token)
                .containsExactly("99.99%");
    }

    @Test
    void matchesEquivalentMoneyUnitsWithinTolerance() {
        ReportNumericFactVerifier.Verification result = verifier.verify(
                "营业收入约 539.09 亿元 [E1]。",
                snapshot(evidence("营业收入 5390925.222051 万元")),
                List.of()
        );

        assertThat(result.passed()).isTrue();
    }

    @Test
    void ignoresYearsSectionNumbersAndEvidenceIds() {
        ReportNumericFactVerifier.Verification result = verifier.verify(
                "## 3. 财务表现\n2026 年报告期数据见 [E1]。",
                snapshot(evidence("公开财报")),
                List.of()
        );

        assertThat(result.totalClaims()).isZero();
        assertThat(result.passed()).isTrue();
    }

    private FinancialMetricResult metric(String name, String displayValue) {
        return new FinancialMetricResult(
                name, new BigDecimal("30.00"), displayValue, name + "公式", "OK", "", List.of(name)
        );
    }

    private FinancialEvidenceItem evidence(String excerpt) {
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET", "TuShare Pro", "", null, "20260331", "ROE",
                null, null, excerpt, new BigDecimal("0.90"), LocalDateTime.now(), ""
        );
    }

    private FinancialSnapshot snapshot(FinancialEvidenceItem... evidenceItems) {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260331", "hybrid", List.of(evidenceItems), LocalDateTime.now()
        );
    }
}
