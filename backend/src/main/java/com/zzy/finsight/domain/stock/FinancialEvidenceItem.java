package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;
import java.time.LocalDateTime;

public record FinancialEvidenceItem(
        String sourceType,
        String sourceName,
        String url,
        Integer pageNumber,
        String reportPeriod,
        String metricName,
        BigDecimal rawValue,
        BigDecimal normalizedValue,
        String excerpt,
        BigDecimal confidence,
        LocalDateTime asOf,
        String issueCode
) {
    public boolean effective() {
        return issueCode == null || issueCode.isBlank() || !"DATA_MISSING".equalsIgnoreCase(issueCode);
    }
}
