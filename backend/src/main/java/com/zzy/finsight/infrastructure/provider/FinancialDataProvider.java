package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;


import java.util.List;

public interface FinancialDataProvider {
    String name();

    List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode);

    default FinancialDataCollection collectWithTrace(StockSubject subject, String reportPeriod, String searchMode) {
        return FinancialDataCollection.evidenceOnly(collect(subject, reportPeriod, searchMode));
    }
}
