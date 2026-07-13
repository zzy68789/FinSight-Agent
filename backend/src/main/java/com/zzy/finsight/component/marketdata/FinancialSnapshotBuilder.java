package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialAgentStageResult;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.infrastructure.provider.FinancialDataProvider;
import com.zzy.finsight.rag.RagRetrievalResult;


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
        List<com.zzy.finsight.rag.RagRetrievalResult> retrievalResults = new ArrayList<>();
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
            if (result.retrievalResult() != null) {
                retrievalResults.add(result.retrievalResult());
            }
        }
        return new FinancialSnapshot(subject, reportPeriod, searchMode, evidenceItems, stageResults, retrievalResults, LocalDateTime.now());
    }

    private ProviderResult collectProvider(FinancialDataProvider provider, StockSubject subject, String reportPeriod, String searchMode) {
        long startedAt = System.currentTimeMillis();
        try {
            FinancialDataCollection collection = provider.collectWithTrace(subject, reportPeriod, searchMode);
            List<FinancialEvidenceItem> items = collection == null ? List.of() : collection.evidenceItems();
            items = items == null ? List.of() : items;
            long durationMs = Math.max(0, System.currentTimeMillis() - startedAt);
            return new ProviderResult(
                    items,
                    new FinancialAgentStageResult(provider.name(), "SUCCESS", durationMs, items.size(), ""),
                    collection == null ? null : collection.retrievalResult()
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
                    new FinancialAgentStageResult(provider.name(), "FAILED", durationMs, 0, e.getMessage()),
                    null
            );
        }
    }

    private record ProviderResult(
            List<FinancialEvidenceItem> evidenceItems,
            FinancialAgentStageResult stageResult,
            com.zzy.finsight.rag.RagRetrievalResult retrievalResult
    ) {
    }
}
