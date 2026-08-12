package com.zzy.finsight.agent.memory;

import com.zzy.finsight.domain.stock.EtfDeepData;
import com.zzy.finsight.domain.stock.FinancialAgentStageResult;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.MarketDataPoint;
import com.zzy.finsight.component.marketdata.FinancialEvidenceValidator;
import com.zzy.finsight.rag.RagRetrievalResult;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 将多次工具观察增量合并为不可变金融证据视图。
 */
@Component
public class EvidenceMemory {
    private final FinancialEvidenceValidator evidenceValidator;

    public EvidenceMemory(FinancialEvidenceValidator evidenceValidator) {
        this.evidenceValidator = evidenceValidator;
    }

    /** 合并新数据并使依赖旧证据的派生结果失效。 */
    public synchronized List<FinancialEvidenceItem> merge(
            AgentState state,
            String toolName,
            FinancialDataCollection collection,
            long durationMs,
            String status,
            String message
    ) {
        FinancialSnapshot current = state.getSnapshot();
        List<FinancialEvidenceItem> before = current == null ? List.of() : current.evidenceItems();
        java.util.Set<String> beforeKeys = before.stream()
                .map(this::evidenceKey)
                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new));
        List<FinancialEvidenceItem> incoming = collection == null ? List.of() : collection.evidenceItems();
        Map<String, FinancialEvidenceItem> unique = new LinkedHashMap<>();
        before.forEach(item -> unique.put(evidenceKey(item), item));
        List<FinancialEvidenceItem> added = new ArrayList<>();
        for (FinancialEvidenceItem item : incoming) {
            String key = evidenceKey(item);
            if (!unique.containsKey(key)) {
                unique.put(key, item);
                added.add(item);
            }
        }

        List<FinancialAgentStageResult> stages = new ArrayList<>(
                current == null ? List.of() : current.stageResults()
        );
        stages.add(new FinancialAgentStageResult(
                toolName,
                status == null || status.isBlank() ? "SUCCESS" : status,
                Math.max(0L, durationMs),
                added.size(),
                message == null ? "" : message
        ));
        List<RagRetrievalResult> retrievals = new ArrayList<>(
                current == null ? List.of() : current.retrievalResults()
        );
        if (collection != null && collection.retrievalResult() != null) {
            retrievals.add(collection.retrievalResult());
        }
        List<MarketDataPoint> marketSeries = mergeMarketSeries(
                current == null ? List.of() : current.marketSeries(),
                collection == null ? List.of() : collection.marketSeries()
        );
        EtfDeepData etfDeepData = collection != null && collection.etfDeepData() != null
                ? collection.etfDeepData()
                : current == null ? null : current.etfDeepData();
        FinancialSnapshot updated = new FinancialSnapshot(
                state.getSubject(),
                state.getRequest().getAsOfDate().toString(),
                state.getRequest().getSearchMode(),
                List.copyOf(unique.values()),
                stages,
                retrievals,
                marketSeries,
                etfDeepData,
                LocalDateTime.now()
        );
        FinancialSnapshot validated = evidenceValidator.validate(updated);
        state.setSnapshot(validated);
        List<FinancialEvidenceItem> validatedAdded = validated.evidenceItems().stream()
                .filter(item -> !beforeKeys.contains(evidenceKey(item)))
                .toList();
        if (!validatedAdded.isEmpty()) {
            state.setMetrics(List.of());
            state.setRiskAssessment(null);
            state.setBullBearResearch(null);
        }
        return List.copyOf(validatedAdded);
    }

    /** 生成稳定证据去重键。 */
    public String evidenceKey(FinancialEvidenceItem item) {
        if (item == null) {
            return "NULL";
        }
        return String.join("|",
                safe(item.sourceType()),
                safe(item.sourceName()),
                safe(item.url()),
                item.pageNumber() == null ? "" : item.pageNumber().toString(),
                safe(item.reportPeriod()),
                safe(item.metricName()),
                item.rawValue() == null ? "" : item.rawValue().stripTrailingZeros().toPlainString(),
                safe(item.excerpt())
        );
    }

    private List<MarketDataPoint> mergeMarketSeries(List<MarketDataPoint> current, List<MarketDataPoint> incoming) {
        Map<String, MarketDataPoint> points = new LinkedHashMap<>();
        for (MarketDataPoint point : current) {
            points.put(point.tradeDate(), point);
        }
        for (MarketDataPoint point : incoming) {
            points.put(point.tradeDate(), point);
        }
        return List.copyOf(points.values());
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
