package com.zzy.drai.financial;

import java.time.LocalDateTime;
import java.util.List;

public record FinancialSnapshot(
        StockSubject subject,
        String reportPeriod,
        String searchMode,
        List<FinancialEvidenceItem> evidenceItems,
        List<FinancialAgentStageResult> stageResults,
        LocalDateTime createdAt
) {
    public FinancialSnapshot {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        stageResults = stageResults == null ? List.of() : List.copyOf(stageResults);
    }

    public FinancialSnapshot(
            StockSubject subject,
            String reportPeriod,
            String searchMode,
            List<FinancialEvidenceItem> evidenceItems,
            LocalDateTime createdAt
    ) {
        this(subject, reportPeriod, searchMode, evidenceItems, List.of(), createdAt);
    }
}
