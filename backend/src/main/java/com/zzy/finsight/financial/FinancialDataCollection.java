package com.zzy.finsight.financial;

import com.zzy.finsight.rag.RagRetrievalResult;

import java.util.List;

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
