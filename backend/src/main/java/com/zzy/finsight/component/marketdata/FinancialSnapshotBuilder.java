package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialAgentStageResult;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.infrastructure.provider.FinancialDataProvider;
import com.zzy.finsight.rag.RagRetrievalResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * 在统一超时窗口内并行聚合多个数据源并构建金融快照。
 */
@Component
public class FinancialSnapshotBuilder {
    private final List<FinancialDataProvider> providers;
    private final Executor executor;
    private final FinancialEvidenceValidator evidenceValidator;
    private final Duration providerTimeout;

    public FinancialSnapshotBuilder(List<FinancialDataProvider> providers, Executor executor) {
        this(providers, executor, new FinancialEvidenceValidator(), Duration.ofSeconds(15));
    }

    @Autowired
    public FinancialSnapshotBuilder(
            List<FinancialDataProvider> providers,
            @Qualifier("financialProviderExecutor") Executor executor,
            FinancialEvidenceValidator evidenceValidator,
            @Value("${finsight.async.financial-provider-timeout:PT15S}") Duration providerTimeout
    ) {
        this.providers = providers == null ? List.of() : providers;
        this.executor = executor;
        this.evidenceValidator = evidenceValidator;
        this.providerTimeout = providerTimeout == null || providerTimeout.isNegative() || providerTimeout.isZero()
                ? Duration.ofSeconds(15)
                : providerTimeout;
    }

    /** 调用适用数据源并构建不可变金融快照，单个慢源不会无限阻塞整个工作流。 */
    public FinancialSnapshot build(long ownerId, StockSubject subject, String reportPeriod, String searchMode) {
        List<FinancialEvidenceItem> evidenceItems = new ArrayList<>();
        List<FinancialAgentStageResult> stageResults = new ArrayList<>();
        List<RagRetrievalResult> retrievalResults = new ArrayList<>();
        long deadlineNanos = System.nanoTime() + providerTimeout.toNanos();
        List<ProviderCall> calls = providers.stream()
                .map(provider -> submitProvider(provider, ownerId, subject, reportPeriod, searchMode))
                .toList();

        for (ProviderCall call : calls) {
            ProviderResult result = awaitProvider(call, reportPeriod, deadlineNanos);
            evidenceItems.addAll(result.evidenceItems());
            stageResults.add(result.stageResult());
            if (result.retrievalResult() != null) {
                retrievalResults.add(result.retrievalResult());
            }
        }
        FinancialSnapshot snapshot = new FinancialSnapshot(
                subject,
                reportPeriod,
                searchMode,
                evidenceItems,
                stageResults,
                retrievalResults,
                LocalDateTime.now()
        );
        return evidenceValidator.validate(snapshot);
    }

    private ProviderCall submitProvider(
            FinancialDataProvider provider,
            long ownerId,
            StockSubject subject,
            String reportPeriod,
            String searchMode
    ) {
        try {
            FutureTask<ProviderResult> future = new FutureTask<>(
                    () -> collectProvider(provider, ownerId, subject, reportPeriod, searchMode)
            );
            executor.execute(future);
            return new ProviderCall(provider, future, null);
        } catch (RejectedExecutionException e) {
            return new ProviderCall(provider, null, e);
        }
    }

    private ProviderResult awaitProvider(ProviderCall call, String reportPeriod, long deadlineNanos) {
        if (call.rejection() != null) {
            return unavailable(call.provider(), reportPeriod, "REJECTED", "数据采集队列已满", 0);
        }
        try {
            if (call.future().isDone()) {
                return call.future().get();
            }
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                call.future().cancel(true);
                return unavailable(
                        call.provider(), reportPeriod, "TIMEOUT", "数据源调用超时", providerTimeout.toMillis()
                );
            }
            return call.future().get(remainingNanos, TimeUnit.NANOSECONDS);
        } catch (TimeoutException e) {
            call.future().cancel(true);
            return unavailable(call.provider(), reportPeriod, "TIMEOUT", "数据源调用超时", providerTimeout.toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            call.future().cancel(true);
            return unavailable(call.provider(), reportPeriod, "INTERRUPTED", "数据采集被中断", 0);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause() == null ? e : e.getCause();
            return unavailable(call.provider(), reportPeriod, "FAILED", cause.getMessage(), 0);
        }
    }

    private ProviderResult collectProvider(
            FinancialDataProvider provider,
            long ownerId,
            StockSubject subject,
            String reportPeriod,
            String searchMode
    ) {
        long startedAt = System.currentTimeMillis();
        try {
            FinancialDataCollection collection = provider.collectWithTrace(ownerId, subject, reportPeriod, searchMode);
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
            return unavailable(provider, reportPeriod, "FAILED", e.getMessage(), durationMs);
        }
    }

    private ProviderResult unavailable(
            FinancialDataProvider provider,
            String reportPeriod,
            String status,
            String message,
            long durationMs
    ) {
        String detail = message == null || message.isBlank() ? "未知异常" : message;
        List<FinancialEvidenceItem> fallback = List.of(new FinancialEvidenceItem(
                "DATA_PROVIDER",
                provider.name(),
                "",
                null,
                reportPeriod,
                "DATA_MISSING",
                null,
                null,
                provider.name() + " 数据源不可用：" + detail,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                "DATA_MISSING"
        ));
        return new ProviderResult(
                fallback,
                new FinancialAgentStageResult(provider.name(), status, Math.max(0, durationMs), 0, detail),
                null
        );
    }

    private record ProviderResult(
            List<FinancialEvidenceItem> evidenceItems,
            FinancialAgentStageResult stageResult,
            RagRetrievalResult retrievalResult
    ) {
    }

    private record ProviderCall(
            FinancialDataProvider provider,
            Future<ProviderResult> future,
            RejectedExecutionException rejection
    ) {
    }
}
