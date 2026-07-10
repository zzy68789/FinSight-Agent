package com.zzy.drai.financial;

import java.util.List;

public interface FinancialDataProvider {
    String name();

    List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode);

    default FinancialDataCollection collectWithTrace(StockSubject subject, String reportPeriod, String searchMode) {
        return FinancialDataCollection.evidenceOnly(collect(subject, reportPeriod, searchMode));
    }
}
