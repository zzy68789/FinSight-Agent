package com.zzy.drai.financial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentReportWriterTest {
    private final InvestmentReportWriter writer = new InvestmentReportWriter();

    @Test
    void writesEtfReportWithoutCompanyFinancialSections() {
        FinancialSnapshot snapshot = new FinancialSnapshot(
                new StockSubject("588200", "SH", "588200.SH", "待识别ETF", "ETF", StockAssetType.ETF),
                "latest",
                "hybrid",
                List.of(
                        evidence(FinancialMetricInputNames.ETF_CLOSE, "ETF收盘价 1.234"),
                        evidence(FinancialMetricInputNames.ETF_PCT_CHANGE, "ETF涨跌幅 2.15%"),
                        evidence(FinancialMetricInputNames.ETF_AMOUNT, "ETF成交额 35000")
                ),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        );

        String report = writer.write(
                snapshot,
                List.of(
                        metric("ETF收盘价", "1.234"),
                        metric("ETF涨跌幅", "2.15%"),
                        metric("ETF成交额", "35000")
                ),
                new FinancialRiskScoringService().assess(List.of(), snapshot.evidenceItems()),
                null
        );

        assertThat(report).contains("588200.SH ETF研究报告");
        assertThat(report).contains("基金代码：588200.SH");
        assertThat(report).contains("ETF收盘价");
        assertThat(report).doesNotContain("A股投研报告");
        assertThat(report).doesNotContain("ROE");
        assertThat(report).doesNotContain("毛利率");
    }

    private FinancialMetricResult metric(String name, String displayValue) {
        return new FinancialMetricResult(name, null, displayValue, name + "来自ETF行情证据", "OK", "", List.of(name));
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                "https://tushare.pro",
                null,
                "latest",
                metricName,
                BigDecimal.ONE,
                BigDecimal.ONE,
                excerpt,
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 7, 6, 10, 0),
                ""
        );
    }
}
