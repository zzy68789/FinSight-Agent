package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.ComparisonSecuritySnapshot;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ComparisonMetricEngineTest {
    private final StockCodeResolver resolver = new StockCodeResolver();
    private final ComparisonMetricEngine engine = new ComparisonMetricEngine();

    @Test
    void computesEquityPePbAndRevenueGrowthBySecurityOwnership() {
        StockSubject primary = resolver.resolve("600519");
        StockSubject comparison = resolver.resolve("000858");
        String snapshotId = "comparison-snapshot";
        List<FinancialEvidenceItem> evidence = List.of(
                evidence(comparison, snapshotId, "PE_TTM", "18.5"),
                evidence(comparison, snapshotId, "PB", "4.2"),
                evidence(comparison, snapshotId, FinancialMetricInputNames.OPERATING_REVENUE, "120"),
                evidence(comparison, snapshotId, FinancialMetricInputNames.OPERATING_REVENUE_PRIOR, "100")
        );
        FinancialSnapshot snapshot = snapshot(primary, comparison, snapshotId, evidence, "SUCCESS");

        List<FinancialMetricResult> metrics = engine.compute(snapshot);

        assertThat(metrics).extracting(FinancialMetricResult::metricName)
                .containsExactly("000858.SZ 市盈率TTM", "000858.SZ 市净率", "000858.SZ 营收同比");
        assertThat(metrics).extracting(FinancialMetricResult::displayValue)
                .containsExactly("18.5倍", "4.2倍", "20.00%");
    }

    @Test
    void marksEveryUnavailableComparableMetricAsDataMissing() {
        StockSubject primary = resolver.resolve("600519");
        StockSubject comparison = resolver.resolve("000858");
        FinancialSnapshot snapshot = snapshot(
                primary, comparison, "missing", List.of(), "DATA_MISSING"
        );

        assertThat(engine.compute(snapshot))
                .allMatch(metric -> "DATA_MISSING".equals(metric.status()))
                .allMatch(metric -> metric.reason().startsWith("DATA_MISSING"));
    }

    private FinancialSnapshot snapshot(
            StockSubject primary,
            StockSubject comparison,
            String snapshotId,
            List<FinancialEvidenceItem> evidence,
            String status
    ) {
        return new FinancialSnapshot(
                primary,
                "20260813",
                "hybrid",
                evidence,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                null,
                LocalDateTime.now(),
                List.of(new ComparisonSecuritySnapshot(
                        snapshotId, comparison, "20260813", evidence.size(), evidence.size(), status
                ))
        );
    }

    private FinancialEvidenceItem evidence(
            StockSubject subject,
            String snapshotId,
            String metricName,
            String value
    ) {
        BigDecimal decimal = new BigDecimal(value);
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET", "测试数据源", "", null, "20260813", metricName,
                decimal, decimal, metricName + "：" + value, BigDecimal.ONE, LocalDateTime.now(), "",
                subject.fullCode(), snapshotId
        );
    }
}
