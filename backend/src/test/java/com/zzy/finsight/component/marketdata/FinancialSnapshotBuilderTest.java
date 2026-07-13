package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialAgentStageResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.infrastructure.provider.FinancialDataProvider;


import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialSnapshotBuilderTest {

    @Test
    void collectsIndependentProvidersConcurrentlyAndRecordsStageMetadata() {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            FinancialSnapshotBuilder builder = new FinancialSnapshotBuilder(List.of(
                    slowProvider("uploaded-report", "LOCAL_CONTEXT"),
                    slowProvider("public-market", "NEWS_SUMMARY")
            ), executor);
            StockSubject subject = new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料");

            long startedAt = System.currentTimeMillis();
            FinancialSnapshot snapshot = builder.build(subject, "latest", "hybrid");
            long elapsedMs = System.currentTimeMillis() - startedAt;

            assertThat(snapshot.evidenceItems()).hasSize(2);
            assertThat(snapshot.stageResults()).hasSize(2);
            assertThat(snapshot.stageResults()).allMatch(stage -> "SUCCESS".equals(stage.status()));
            assertThat(snapshot.stageResults()).extracting(FinancialAgentStageResult::stageName)
                    .containsExactlyInAnyOrder("uploaded-report", "public-market");
            assertThat(elapsedMs).isLessThan(450);
        } finally {
            executor.shutdownNow();
        }
    }

    private FinancialDataProvider slowProvider(String name, String metricName) {
        return new FinancialDataProvider() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode) {
                try {
                    Thread.sleep(250);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                return List.of(new FinancialEvidenceItem(
                        "TEST",
                        name,
                        "",
                        null,
                        reportPeriod,
                        metricName,
                        null,
                        null,
                        name + " evidence",
                        new BigDecimal("0.8"),
                        LocalDateTime.of(2026, 7, 3, 10, 0),
                        ""
                ));
            }
        };
    }
}
