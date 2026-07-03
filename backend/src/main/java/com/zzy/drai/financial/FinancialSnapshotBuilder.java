package com.zzy.drai.financial;

import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Qualifier;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Component
public class FinancialSnapshotBuilder {
    private final List<FinancialDataProvider> providers;
    private final Executor executor;

    public FinancialSnapshotBuilder(
            List<FinancialDataProvider> providers,
            @Qualifier("financialProviderExecutor") Executor executor
    ) {
        this.providers = providers == null ? List.of() : providers;
        this.executor = executor;
    }

    public FinancialSnapshot build(StockSubject subject, String reportPeriod, String searchMode) {
        List<FinancialEvidenceItem> evidenceItems = new ArrayList<>();
        List<FinancialAgentStageResult> stageResults = new ArrayList<>();
        List<CompletableFuture<ProviderResult>> futures = providers.stream()
                .map(provider -> CompletableFuture.supplyAsync(
                        () -> collectProvider(provider, subject, reportPeriod, searchMode),
                        executor
                ))
                .toList();
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new)).join();
        for (CompletableFuture<ProviderResult> future : futures) {
            ProviderResult result = future.join();
            evidenceItems.addAll(result.evidenceItems());
            stageResults.add(result.stageResult());
        }
        return new FinancialSnapshot(subject, reportPeriod, searchMode, evidenceItems, stageResults, LocalDateTime.now());
    }

    private ProviderResult collectProvider(FinancialDataProvider provider, StockSubject subject, String reportPeriod, String searchMode) {
        long startedAt = System.currentTimeMillis();
        try {
            List<FinancialEvidenceItem> items = provider.collect(subject, reportPeriod, searchMode);
            items = items == null ? List.of() : items;
            long durationMs = Math.max(0, System.currentTimeMillis() - startedAt);
            return new ProviderResult(
                    items,
                    new FinancialAgentStageResult(provider.name(), "SUCCESS", durationMs, items.size(), "")
            );
        } catch (RuntimeException e) {
            long durationMs = Math.max(0, System.currentTimeMillis() - startedAt);
            List<FinancialEvidenceItem> fallback = List.of(new FinancialEvidenceItem(
                    "DATA_PROVIDER",
                    provider.name(),
                    "",
                    null,
                    reportPeriod,
                    "DATA_MISSING",
                    null,
                    null,
                    provider.name() + " 数据源失败：" + e.getMessage(),
                    java.math.BigDecimal.ZERO,
                    LocalDateTime.now(),
                    "DATA_MISSING"
            ));
            return new ProviderResult(
                    fallback,
                    new FinancialAgentStageResult(provider.name(), "FAILED", durationMs, 0, e.getMessage())
            );
        }
    }

    private record ProviderResult(List<FinancialEvidenceItem> evidenceItems, FinancialAgentStageResult stageResult) {
    }
}
