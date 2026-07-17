package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.MarketDataPoint;
import com.zzy.finsight.domain.stock.metric.MetricDefinitionCatalog;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialReportFingerprinterTest {
    private final FinancialReportFingerprinter service = new FinancialReportFingerprinter(
            new MetricDefinitionCatalog()
    );

    @Test
    void dataHashIgnoresEvidenceOrderingAndCollectionTime() {
        FinancialEvidenceItem revenue = evidence(
                "营业收入", "100.00", "https://example.com/report", 10, "2026-07-10T10:00:00"
        );
        FinancialEvidenceItem profit = evidence(
                "净利润", "20.00", "https://example.com/report", 11, "2026-07-10T10:00:00"
        );
        FinancialEvidenceItem revenueCollectedLater = evidence(
                "营业收入", "100.00", "https://example.com/report", 10, "2026-07-10T11:00:00"
        );
        FinancialEvidenceItem profitCollectedLater = evidence(
                "净利润", "20.00", "https://example.com/report", 11, "2026-07-10T11:00:00"
        );
        FinancialSnapshot first = snapshot(List.of(revenue, profit), LocalDateTime.of(2026, 7, 10, 10, 0));
        FinancialSnapshot reordered = snapshot(
                List.of(profitCollectedLater, revenueCollectedLater),
                LocalDateTime.of(2026, 7, 10, 11, 0)
        );

        assertThat(service.dataSnapshotHash(first)).isEqualTo(service.dataSnapshotHash(reordered));
    }

    @Test
    void dataHashChangesWhenBusinessEvidenceChanges() {
        FinancialSnapshot first = snapshot(
                List.of(evidence("营业收入", "100.00", "https://example.com/report", 10, "2026-07-10T10:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );
        FinancialSnapshot changed = snapshot(
                List.of(evidence("营业收入", "101.00", "https://example.com/report", 10, "2026-07-10T10:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );

        assertThat(service.dataSnapshotHash(first)).isNotEqualTo(service.dataSnapshotHash(changed));
        assertThat(service.generationContextHash(service.dataSnapshotHash(first))).hasSize(64);
    }

    @Test
    void dataHashChangesWhenCitationLocationChanges() {
        FinancialSnapshot first = snapshot(
                List.of(evidence("营业收入", "100.00", "https://example.com/report-a", 10, "2026-07-10T10:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );
        FinancialSnapshot changedUrl = snapshot(
                List.of(evidence("营业收入", "100.00", "https://example.com/report-b", 10, "2026-07-10T10:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );
        FinancialSnapshot changedPage = snapshot(
                List.of(evidence("营业收入", "100.00", "https://example.com/report-a", 11, "2026-07-10T10:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );

        assertThat(service.dataSnapshotHash(first)).isNotEqualTo(service.dataSnapshotHash(changedUrl));
        assertThat(service.dataSnapshotHash(first)).isNotEqualTo(service.dataSnapshotHash(changedPage));
    }

    @Test
    void dataHashChangesWhenEtfMarketSeriesChanges() {
        FinancialSnapshot first = etfSnapshot("1.234");
        FinancialSnapshot changed = etfSnapshot("1.235");

        assertThat(service.dataSnapshotHash(first)).isNotEqualTo(service.dataSnapshotHash(changed));
    }

    private FinancialSnapshot etfSnapshot(String close) {
        return new FinancialSnapshot(
                new StockSubject("588200", "SH", "588200.SH", "科创芯片ETF", "ETF"),
                "latest",
                "hybrid",
                List.of(),
                List.of(),
                List.of(),
                List.of(new MarketDataPoint(
                        "20260717", new BigDecimal("1.20"), new BigDecimal("1.25"),
                        new BigDecimal("1.19"), new BigDecimal(close), new BigDecimal("1.21"),
                        new BigDecimal("1.98"), new BigDecimal("280000"), new BigDecimal("35000")
                )),
                null,
                LocalDateTime.of(2026, 7, 17, 10, 0)
        );
    }

    private FinancialSnapshot snapshot(List<FinancialEvidenceItem> evidence, LocalDateTime createdAt) {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "2026Q2",
                "hybrid",
                evidence,
                createdAt
        );
    }

    private FinancialEvidenceItem evidence(
            String metric,
            String value,
            String url,
            Integer pageNumber,
            String asOf
    ) {
        BigDecimal number = new BigDecimal(value);
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET", "测试数据源", url, pageNumber, "2026Q2", metric,
                number, number, metric + "=" + value, new BigDecimal("0.90"), LocalDateTime.parse(asOf), ""
        );
    }
}
