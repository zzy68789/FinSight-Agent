package com.zzy.finsight.domain.stock;


import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 表示一条可追踪的金融证据。
 * @param sourceType 数据源类型。
 * @param sourceName 数据源名称。
 * @param url 数据来源地址。
 * @param pageNumber 证据所在页码。
 * @param reportPeriod 报告期。
 * @param metricName 指标名称。
 * @param rawValue 数据源原始数值。
 * @param normalizedValue 标准化后的数值。
 * @param excerpt 证据原文摘要。
 * @param confidence 证据置信度。
 * @param asOf 数据对应时间。
 * @param issueCode 数据问题编码。
 */
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
    /** 只有没有任何问题编码的证据才可参与指标和审查。 */
    public boolean effective() {
        return issueCode == null || issueCode.isBlank();
    }
}
