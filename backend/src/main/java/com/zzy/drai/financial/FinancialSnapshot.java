package com.zzy.drai.financial;

import java.time.LocalDateTime;
import java.util.List;

public record FinancialSnapshot(
        StockSubject subject,
        String reportPeriod,
        String searchMode,
        List<FinancialEvidenceItem> evidenceItems,
        LocalDateTime createdAt
) {
    public FinancialSnapshot {
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
    }
}
