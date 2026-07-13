package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
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
    void dataHashIgnoresEvidenceOrderingAndSnapshotCreationTime() {
        FinancialEvidenceItem revenue = evidence("营业收入", "100.00", "2026-06-30T00:00:00");
        FinancialEvidenceItem profit = evidence("净利润", "20.00", "2026-06-30T00:00:00");
        FinancialSnapshot first = snapshot(List.of(revenue, profit), LocalDateTime.of(2026, 7, 10, 10, 0));
        FinancialSnapshot reordered = snapshot(List.of(profit, revenue), LocalDateTime.of(2026, 7, 10, 11, 0));

        assertThat(service.dataSnapshotHash(first)).isEqualTo(service.dataSnapshotHash(reordered));
    }

    @Test
    void dataHashChangesWhenBusinessEvidenceChanges() {
        FinancialSnapshot first = snapshot(
                List.of(evidence("营业收入", "100.00", "2026-06-30T00:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );
        FinancialSnapshot changed = snapshot(
                List.of(evidence("营业收入", "101.00", "2026-06-30T00:00:00")),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );

        assertThat(service.dataSnapshotHash(first)).isNotEqualTo(service.dataSnapshotHash(changed));
        assertThat(service.generationContextHash(service.dataSnapshotHash(first))).hasSize(64);
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

    private FinancialEvidenceItem evidence(String metric, String value, String asOf) {
        BigDecimal number = new BigDecimal(value);
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET", "测试数据源", "", null, "2026Q2", metric,
                number, number, metric + "=" + value, new BigDecimal("0.90"), LocalDateTime.parse(asOf), ""
        );
    }
}
