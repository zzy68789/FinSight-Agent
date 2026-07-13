package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialMetricEngineTest {

    private final FinancialMetricEngine engine = new FinancialMetricEngine();

    @Test
    void computesDeterministicFinancialMetricsFromEvidenceValues() {
        FinancialSnapshot snapshot = snapshot(
                evidence(FinancialMetricInputNames.OPERATING_REVENUE, "120"),
                evidence(FinancialMetricInputNames.OPERATING_REVENUE_PRIOR, "100"),
                evidence(FinancialMetricInputNames.GROSS_PROFIT, "60"),
                evidence(FinancialMetricInputNames.NET_PROFIT, "24"),
                evidence(FinancialMetricInputNames.BEGINNING_EQUITY, "70"),
                evidence(FinancialMetricInputNames.ENDING_EQUITY, "90"),
                evidence(FinancialMetricInputNames.TOTAL_LIABILITIES, "50"),
                evidence(FinancialMetricInputNames.TOTAL_ASSETS, "200"),
                evidence(FinancialMetricInputNames.OPERATING_CASH_FLOW, "36")
        );

        List<FinancialMetricResult> results = engine.compute(snapshot);

        assertThat(metric(results, "营收同比").value()).isEqualByComparingTo("20.00");
        assertThat(metric(results, "毛利率").value()).isEqualByComparingTo("50.00");
        assertThat(metric(results, "净利率").value()).isEqualByComparingTo("20.00");
        assertThat(metric(results, "ROE").value()).isEqualByComparingTo("30.00");
        assertThat(metric(results, "ROE").formulaVersion()).isEqualTo("v2");
        assertThat(metric(results, "资产负债率").value()).isEqualByComparingTo("25.00");
        assertThat(metric(results, "经营现金流 / 净利润").value()).isEqualByComparingTo("1.50");
        assertThat(results).allMatch(result -> "OK".equals(result.status()));
    }

    @Test
    void returnsMissingInputWhenRequiredEvidenceIsAbsent() {
        FinancialSnapshot snapshot = snapshot(evidence(FinancialMetricInputNames.OPERATING_REVENUE, "120"));

        List<FinancialMetricResult> results = engine.compute(snapshot);

        assertThat(metric(results, "营收同比").status()).isEqualTo("MISSING_INPUT");
        assertThat(metric(results, "ROE").reason()).contains("缺少");
    }

    @Test
    void computesEtfMarketMetricsWithoutCompanyFinancialRatios() {
        FinancialSnapshot snapshot = new FinancialSnapshot(
                new StockSubject("588200", "SH", "588200.SH", "待识别ETF", "ETF", StockAssetType.ETF),
                "latest",
                "hybrid",
                List.of(
                        evidence(FinancialMetricInputNames.ETF_CLOSE, "1.234"),
                        evidence(FinancialMetricInputNames.ETF_PCT_CHANGE, "2.15"),
                        evidence(FinancialMetricInputNames.ETF_AMOUNT, "35000")
                ),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        );

        List<FinancialMetricResult> results = engine.compute(snapshot);

        assertThat(results).extracting(FinancialMetricResult::metricName)
                .containsExactly("ETF收盘价", "ETF涨跌幅", "ETF成交额");
        assertThat(results).allMatch(result -> "OK".equals(result.status()));
        assertThat(results).noneMatch(result -> "ROE".equals(result.metricName()) || "毛利率".equals(result.metricName()));
    }

    private FinancialMetricResult metric(List<FinancialMetricResult> results, String name) {
        return results.stream()
                .filter(result -> result.metricName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private FinancialSnapshot snapshot(FinancialEvidenceItem... items) {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "2025",
                "hybrid",
                List.of(items),
                LocalDateTime.of(2026, 7, 3, 10, 0)
        );
    }

    private FinancialEvidenceItem evidence(String metricName, String value) {
        return new FinancialEvidenceItem(
                "FINANCIAL_REPORT",
                "年报",
                "",
                1,
                "2025",
                metricName,
                new BigDecimal(value),
                new BigDecimal(value),
                metricName + "=" + value,
                new BigDecimal("0.95"),
                LocalDateTime.of(2026, 7, 3, 10, 0),
                ""
        );
    }
}
