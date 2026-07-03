package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class FinancialSnapshotBuilder {
    private final List<FinancialDataProvider> providers;

    public FinancialSnapshotBuilder(List<FinancialDataProvider> providers) {
        this.providers = providers == null ? List.of() : providers;
    }

    public FinancialSnapshot build(StockSubject subject, String reportPeriod, String searchMode) {
        List<FinancialEvidenceItem> evidenceItems = new ArrayList<>();
        for (FinancialDataProvider provider : providers) {
            try {
                evidenceItems.addAll(provider.collect(subject, reportPeriod, searchMode));
            } catch (RuntimeException e) {
                evidenceItems.add(new FinancialEvidenceItem(
                        "DATA_PROVIDER",
                        provider.name(),
                        "",
                        null,
                        reportPeriod,
                        "DATA_MISSING",
                        null,
                        null,
                        provider.name() + " 数据源失败：" + e.getMessage(),
                        java.math.BigDecimal.ZERO,
                        LocalDateTime.now(),
                        "DATA_MISSING"
                ));
            }
        }
        return new FinancialSnapshot(subject, reportPeriod, searchMode, evidenceItems, LocalDateTime.now());
    }
}
