package com.zzy.finsight.domain.stock;


import com.zzy.finsight.rag.RagRetrievalResult;

import java.util.List;

/**
 * 汇总数据源采集的证据和检索轨迹。
 * @param evidenceItems 金融证据列表。
 * @param retrievalResult 单次检索结果。
 * @param marketSeries 可视化使用的行情序列。
 * @param etfDeepData ETF 基础资料与净值快照。
 */
public record FinancialDataCollection(
        List<FinancialEvidenceItem> evidenceItems,
        RagRetrievalResult retrievalResult,
        List<MarketDataPoint> marketSeries,
        EtfDeepData etfDeepData
) {
    public FinancialDataCollection {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        marketSeries = marketSeries == null ? List.of() : List.copyOf(marketSeries);
    }

    public FinancialDataCollection(List<FinancialEvidenceItem> evidenceItems, RagRetrievalResult retrievalResult) {
        this(evidenceItems, retrievalResult, List.of(), null);
    }

    public static FinancialDataCollection evidenceOnly(List<FinancialEvidenceItem> evidenceItems) {
        return new FinancialDataCollection(evidenceItems, null, List.of(), null);
    }
}
