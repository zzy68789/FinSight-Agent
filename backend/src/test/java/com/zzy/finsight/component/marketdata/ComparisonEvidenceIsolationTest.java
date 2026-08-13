package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonEvidenceIsolationTest {

    @Test
    void sameMetricFromDifferentSubjectsDoesNotBecomeDuplicateOrConflict() {
        FinancialSnapshot raw = new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260331",
                "hybrid",
                List.of(
                        evidence("600519.SH", "primary-snapshot", "120"),
                        evidence("000858.SZ", "comparison-snapshot", "999")
                ),
                LocalDateTime.of(2026, 8, 13, 10, 0)
        );

        FinancialSnapshot validated = new FinancialEvidenceValidator().validate(raw);

        assertThat(validated.evidenceItems())
                .allMatch(FinancialEvidenceItem::effective)
                .extracting(FinancialEvidenceItem::subjectCode)
                .containsExactly("600519.SH", "000858.SZ");
        assertThat(validated.evidenceArbitrations()).isEmpty();
    }

    private FinancialEvidenceItem evidence(String subjectCode, String snapshotId, String value) {
        BigDecimal number = new BigDecimal(value);
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                "https://tushare.pro",
                null,
                "20260331",
                FinancialMetricInputNames.OPERATING_REVENUE,
                number,
                number,
                "营业收入=" + value,
                new BigDecimal("0.95"),
                LocalDateTime.of(2026, 8, 13, 10, 0),
                "",
                subjectCode,
                snapshotId
        );
    }
}
