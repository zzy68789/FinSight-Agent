package com.zzy.finsight.domain.stock;


import com.zzy.finsight.rag.RagRetrievalResult;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 表示一次证券研究使用的数据快照。
 * @param subject 研究证券主体。
 * @param reportPeriod 报告期。
 * @param searchMode 检索模式。
 * @param evidenceItems 金融证据列表。
 * @param stageResults 数据源执行结果列表。
 * @param retrievalResults 检索结果列表。
 * @param marketSeries 可视化使用的行情序列。
 * @param etfDeepData ETF 基础资料与净值快照。
 * @param createdAt 创建时间。
 */
public record FinancialSnapshot(
        StockSubject subject,
        String reportPeriod,
        String searchMode,
        List<FinancialEvidenceItem> evidenceItems,
        List<FinancialAgentStageResult> stageResults,
        List<RagRetrievalResult> retrievalResults,
        List<MarketDataPoint> marketSeries,
        EtfDeepData etfDeepData,
        LocalDateTime createdAt
) {
    public FinancialSnapshot {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        stageResults = stageResults == null ? List.of() : List.copyOf(stageResults);
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
        marketSeries = marketSeries == null ? List.of() : List.copyOf(marketSeries);
    }

    public FinancialSnapshot(
            StockSubject subject,
            String reportPeriod,
            String searchMode,
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialAgentStageResult> stageResults,
            List<RagRetrievalResult> retrievalResults,
            LocalDateTime createdAt
    ) {
        this(subject, reportPeriod, searchMode, evidenceItems, stageResults, retrievalResults,
                List.of(), null, createdAt);
    }

    public FinancialSnapshot(
            StockSubject subject,
            String reportPeriod,
            String searchMode,
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialAgentStageResult> stageResults,
            LocalDateTime createdAt
    ) {
        this(subject, reportPeriod, searchMode, evidenceItems, stageResults, List.of(), List.of(), null, createdAt);
    }

    public FinancialSnapshot(
            StockSubject subject,
            String reportPeriod,
            String searchMode,
            List<FinancialEvidenceItem> evidenceItems,
            LocalDateTime createdAt
    ) {
        this(subject, reportPeriod, searchMode, evidenceItems, List.of(), List.of(), List.of(), null, createdAt);
    }
}
