package com.zzy.drai.financial;

import java.util.List;

public interface FinancialDataProvider {
    String name();

    List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode);
}
