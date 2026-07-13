package com.zzy.finsight.domain.stock;


import com.zzy.finsight.rag.RagRetrievalResult;

import java.util.List;

/**
 * 汇总数据源采集的证据和检索轨迹。
 * @param evidenceItems 金融证据列表。
 * @param retrievalResult 单次检索结果。
 */
public record FinancialDataCollection(
        List<FinancialEvidenceItem> evidenceItems,
        RagRetrievalResult retrievalResult
) {
    public FinancialDataCollection {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
    }

    public static FinancialDataCollection evidenceOnly(List<FinancialEvidenceItem> evidenceItems) {
        return new FinancialDataCollection(evidenceItems, null);
    }
}
