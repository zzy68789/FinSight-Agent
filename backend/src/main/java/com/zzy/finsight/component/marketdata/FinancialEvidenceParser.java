package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从检索文本中解析结构化金融证据。
 */
@Component
public class FinancialEvidenceParser {
    private static final Map<String, Pattern> PATTERNS = Map.of(
            FinancialMetricInputNames.OPERATING_REVENUE_PRIOR, Pattern.compile("(上年同期营业收入|去年同期营业收入|上年营收)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.OPERATING_REVENUE, Pattern.compile("(营业收入|营收)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.GROSS_PROFIT, Pattern.compile("(毛利润|毛利额|毛利)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.OPERATING_COST, Pattern.compile("(营业成本)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.NET_PROFIT, Pattern.compile("(归母净利润|净利润)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.AVERAGE_EQUITY, Pattern.compile("(平均净资产|平均所有者权益|净资产平均值)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.TOTAL_LIABILITIES, Pattern.compile("(负债总额|总负债)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.TOTAL_ASSETS, Pattern.compile("(资产总额|总资产)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?"),
            FinancialMetricInputNames.OPERATING_CASH_FLOW, Pattern.compile("(经营活动产生的现金流量净额|经营现金流|经营性现金流)[^0-9\\-]{0,16}(-?\\d+(?:\\.\\d+)?)(亿元|万元|元)?")
    );

    /** 将检索文本解析为带来源和口径的金融证据。 */
    public List<FinancialEvidenceItem> parse(
            String content,
            String sourceType,
            String sourceName,
            String url,
            String reportPeriod,
            BigDecimal confidence
    ) {
        if (content == null || content.isBlank()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        for (Map.Entry<String, Pattern> entry : PATTERNS.entrySet()) {
            Matcher matcher = entry.getValue().matcher(content);
            if (matcher.find()) {
                BigDecimal rawValue = new BigDecimal(matcher.group(2));
                BigDecimal normalized = normalizeUnit(rawValue, matcher.group(3));
                items.add(new FinancialEvidenceItem(
                        sourceType,
                        sourceName,
                        url,
                        null,
                        reportPeriod,
                        entry.getKey(),
                        rawValue,
                        normalized,
                        excerpt(content, matcher.start(), matcher.end()),
                        confidence,
                        LocalDateTime.now(),
                        ""
                ));
            }
        }
        return items;
    }

    private BigDecimal normalizeUnit(BigDecimal value, String unit) {
        if (unit == null || unit.isBlank() || "亿元".equals(unit)) {
            return value;
        }
        if ("万元".equals(unit)) {
            return value.divide(BigDecimal.valueOf(10000), 6, RoundingMode.HALF_UP);
        }
        if ("元".equals(unit)) {
            return value.divide(BigDecimal.valueOf(100000000), 6, RoundingMode.HALF_UP);
        }
        return value;
    }

    private String excerpt(String content, int start, int end) {
        int safeStart = Math.max(0, start - 40);
        int safeEnd = Math.min(content.length(), end + 60);
        return content.substring(safeStart, safeEnd).replaceAll("\\s+", " ").trim();
    }
}
