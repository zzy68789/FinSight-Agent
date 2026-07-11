package com.zzy.finsight.financial;

import com.zzy.finsight.rag.RagRetrievalResult;

import java.time.LocalDateTime;
import java.util.List;

public record FinancialSnapshot(
        StockSubject subject,
        String reportPeriod,
        String searchMode,
        List<FinancialEvidenceItem> evidenceItems,
        List<FinancialAgentStageResult> stageResults,
        List<RagRetrievalResult> retrievalResults,
        LocalDateTime createdAt
) {
    public FinancialSnapshot {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        stageResults = stageResults == null ? List.of() : List.copyOf(stageResults);
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
    }

    public FinancialSnapshot(
            StockSubject subject,
            String reportPeriod,
            String searchMode,
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialAgentStageResult> stageResults,
            LocalDateTime createdAt
    ) {
        this(subject, reportPeriod, searchMode, evidenceItems, stageResults, List.of(), createdAt);
    }

    public FinancialSnapshot(
            StockSubject subject,
            String reportPeriod,
            String searchMode,
            List<FinancialEvidenceItem> evidenceItems,
            LocalDateTime createdAt
    ) {
        this(subject, reportPeriod, searchMode, evidenceItems, List.of(), List.of(), createdAt);
    }
}
