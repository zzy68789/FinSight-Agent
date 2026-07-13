package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
@Order(5)
public class TushareMarketDataProvider implements FinancialDataProvider {
    private static final String SOURCE_TYPE = "AUTHORIZED_MARKET";
    private static final String SOURCE_NAME = "TuShare Pro";
    private static final String SOURCE_URL = "https://tushare.pro";
    private static final BigDecimal CONFIDENCE = new BigDecimal("0.90");
    private static final BigDecimal YUAN_PER_YI = new BigDecimal("100000000");
    private static final BigDecimal WAN_PER_YI = new BigDecimal("10000");

    private final RestClient restClient;
    private final boolean enabled;
    private final String token;

    public TushareMarketDataProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${finsight.market.tushare.enabled:false}") boolean enabled,
            @Value("${finsight.market.tushare.base-url:https://api.tushare.pro}") String baseUrl,
            @Value("${finsight.market.tushare.api-key:}") String token,
            @Value("${finsight.market.tushare.timeout:10s}") String timeout
    ) {
        this.restClient = restClientBuilder.baseUrl(blankToDefault(baseUrl, "https://api.tushare.pro")).build();
        this.enabled = enabled;
        this.token = token == null ? "" : token.trim();
    }

    @Override
    public String name() {
        return "tushare-market";
    }

    @Override
    public List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode) {
        if (!enabled || token.isBlank() || "document".equalsIgnoreCase(searchMode)) {
            return List.of();
        }
        try {
            if (subject.isEtf()) {
                List<FinancialEvidenceItem> etfItems = fundDailyEvidence(subject, reportPeriod);
                if (etfItems.isEmpty()) {
                    return List.of(dataMissing(reportPeriod, "TuShare Pro 未返回可用 ETF 行情数据。"));
                }
                return etfItems;
            }
            List<FinancialEvidenceItem> items = new ArrayList<>();
            items.addAll(incomeEvidence(subject, reportPeriod));
            items.addAll(balanceSheetEvidence(subject, reportPeriod));
            items.addAll(cashFlowEvidence(subject, reportPeriod));
            items.addAll(dailyBasicEvidence(subject, reportPeriod));
            if (items.isEmpty()) {
                return List.of(dataMissing(reportPeriod, "TuShare Pro 未返回可用行情或财务数据。"));
            }
            return items;
        } catch (RuntimeException e) {
            return List.of(dataMissing(reportPeriod, "TuShare Pro 数据不可用：" + e.getMessage()));
        }
    }

    private List<FinancialEvidenceItem> fundDailyEvidence(StockSubject subject, String reportPeriod) {
        List<Map<String, JsonNode>> rows = sortByDate(query(
                "fund_daily",
                subject,
                reportPeriod,
                "ts_code,trade_date,close,pct_chg,amount"
        ), "trade_date");
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, JsonNode> latest = rows.get(0);
        String period = text(latest, "trade_date", reportPeriod);
        List<FinancialEvidenceItem> items = new ArrayList<>();
        addRawMetric(items, period, FinancialMetricInputNames.ETF_CLOSE, latest, "close", "ETF收盘价");
        addRawMetric(items, period, FinancialMetricInputNames.ETF_PCT_CHANGE, latest, "pct_chg", "ETF涨跌幅");
        addRawMetric(items, period, FinancialMetricInputNames.ETF_AMOUNT, latest, "amount", "ETF成交额");
        return items;
    }

    private List<FinancialEvidenceItem> incomeEvidence(StockSubject subject, String reportPeriod) {
        List<Map<String, JsonNode>> rows = query(
                "income",
                subject,
                reportPeriod,
                "ts_code,end_date,revenue,oper_cost,n_income_attr_p"
        );
        rows = sortByDate(rows, "end_date");
        if (rows.isEmpty()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Map<String, JsonNode> latest = rows.get(0);
        String period = text(latest, "end_date", reportPeriod);
        addMoney(items, period, FinancialMetricInputNames.OPERATING_REVENUE, latest, "revenue", "营业收入");
        addMoney(items, period, FinancialMetricInputNames.OPERATING_COST, latest, "oper_cost", "营业成本");
        addMoney(items, period, FinancialMetricInputNames.NET_PROFIT, latest, "n_income_attr_p", "归母净利润");
        if (rows.size() > 1) {
            Map<String, JsonNode> prior = rows.get(1);
            addMoney(items, text(prior, "end_date", reportPeriod), FinancialMetricInputNames.OPERATING_REVENUE_PRIOR, prior, "revenue", "上年同期营业收入");
        }
        return items;
    }

    private List<FinancialEvidenceItem> balanceSheetEvidence(StockSubject subject, String reportPeriod) {
        List<Map<String, JsonNode>> rows = sortByDate(query(
                "balancesheet",
                subject,
                reportPeriod,
                "ts_code,end_date,total_assets,total_liab,total_hldr_eqy_exc_min_int"
        ), "end_date");
        if (rows.isEmpty()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Map<String, JsonNode> latest = rows.get(0);
        String period = text(latest, "end_date", reportPeriod);
        addMoney(items, period, FinancialMetricInputNames.TOTAL_ASSETS, latest, "total_assets", "资产总额");
        addMoney(items, period, FinancialMetricInputNames.TOTAL_LIABILITIES, latest, "total_liab", "负债总额");
        addMoney(items, period, FinancialMetricInputNames.AVERAGE_EQUITY, latest, "total_hldr_eqy_exc_min_int", "期末归母权益近似平均净资产");
        return items;
    }

    private List<FinancialEvidenceItem> cashFlowEvidence(StockSubject subject, String reportPeriod) {
        List<Map<String, JsonNode>> rows = sortByDate(query(
                "cashflow",
                subject,
                reportPeriod,
                "ts_code,end_date,n_cashflow_act"
        ), "end_date");
        if (rows.isEmpty()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Map<String, JsonNode> latest = rows.get(0);
        addMoney(items, text(latest, "end_date", reportPeriod), FinancialMetricInputNames.OPERATING_CASH_FLOW, latest, "n_cashflow_act", "经营活动产生的现金流量净额");
        return items;
    }

    private List<FinancialEvidenceItem> dailyBasicEvidence(StockSubject subject, String reportPeriod) {
        List<Map<String, JsonNode>> rows = sortByDate(query(
                "daily_basic",
                subject,
                reportPeriod,
                "ts_code,trade_date,pe_ttm,pb,total_mv"
        ), "trade_date");
        if (rows.isEmpty()) {
            return List.of();
        }
        Map<String, JsonNode> latest = rows.get(0);
        String period = text(latest, "trade_date", reportPeriod);
        List<FinancialEvidenceItem> items = new ArrayList<>();
        addRatio(items, period, "PE_TTM", latest, "pe_ttm", "市盈率TTM");
        addRatio(items, period, "PB", latest, "pb", "市净率");
        addWanMoney(items, period, "TOTAL_MARKET_VALUE", latest, "total_mv", "总市值");
        return items;
    }

    private List<Map<String, JsonNode>> query(String apiName, StockSubject subject, String reportPeriod, String fields) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", subject.fullCode());
        if (isConcreteReportPeriod(reportPeriod)) {
            if ("daily_basic".equals(apiName) || "fund_daily".equals(apiName)) {
                params.put("trade_date", reportPeriod);
            } else {
                params.put("end_date", reportPeriod);
            }
        }
        JsonNode response = restClient.post()
                .uri("")
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "api_name", apiName,
                        "token", token,
                        "params", params,
                        "fields", fields
                ))
                .retrieve()
                .body(JsonNode.class);
        int code = response == null ? -1 : response.path("code").asInt(-1);
        if (code != 0) {
            String message = response == null ? "empty response" : response.path("msg").asText("");
            throw new IllegalStateException(apiName + " 返回 " + code + "：" + message);
        }
        return rows(response.path("data"));
    }

    private List<Map<String, JsonNode>> rows(JsonNode data) {
        JsonNode fields = data.path("fields");
        JsonNode items = data.path("items");
        if (!fields.isArray() || !items.isArray()) {
            return List.of();
        }
        List<Map<String, JsonNode>> rows = new ArrayList<>();
        for (JsonNode item : items) {
            Map<String, JsonNode> row = new LinkedHashMap<>();
            for (int i = 0; i < fields.size(); i++) {
                row.put(fields.path(i).asText(), item.path(i));
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, JsonNode>> sortByDate(List<Map<String, JsonNode>> rows, String dateField) {
        return rows.stream()
                .sorted(Comparator.comparing((Map<String, JsonNode> row) -> text(row, dateField, "")).reversed())
                .toList();
    }

    private void addMoney(List<FinancialEvidenceItem> items, String period, String metricName, Map<String, JsonNode> row, String field, String label) {
        BigDecimal raw = decimal(row, field);
        if (raw == null) {
            return;
        }
        BigDecimal normalized = raw.divide(YUAN_PER_YI, 6, RoundingMode.HALF_UP);
        items.add(evidence(period, metricName, raw, normalized, label + " " + normalized.toPlainString() + " 亿元"));
    }

    private void addWanMoney(List<FinancialEvidenceItem> items, String period, String metricName, Map<String, JsonNode> row, String field, String label) {
        BigDecimal raw = decimal(row, field);
        if (raw == null) {
            return;
        }
        BigDecimal normalized = raw.divide(WAN_PER_YI, 6, RoundingMode.HALF_UP);
        items.add(evidence(period, metricName, raw, normalized, label + " " + normalized.toPlainString() + " 亿元"));
    }

    private void addRatio(List<FinancialEvidenceItem> items, String period, String metricName, Map<String, JsonNode> row, String field, String label) {
        BigDecimal value = decimal(row, field);
        if (value == null) {
            return;
        }
        items.add(evidence(period, metricName, value, value, label + " " + value.toPlainString()));
    }

    private void addRawMetric(List<FinancialEvidenceItem> items, String period, String metricName, Map<String, JsonNode> row, String field, String label) {
        BigDecimal value = decimal(row, field);
        if (value == null) {
            return;
        }
        items.add(evidence(period, metricName, value, value, label + " " + value.toPlainString()));
    }

    private FinancialEvidenceItem evidence(String period, String metricName, BigDecimal raw, BigDecimal normalized, String excerpt) {
        return new FinancialEvidenceItem(
                SOURCE_TYPE,
                SOURCE_NAME,
                SOURCE_URL,
                null,
                period,
                metricName,
                raw,
                normalized,
                excerpt,
                CONFIDENCE,
                LocalDateTime.now(),
                ""
        );
    }

    private FinancialEvidenceItem dataMissing(String reportPeriod, String excerpt) {
        return new FinancialEvidenceItem(
                SOURCE_TYPE,
                SOURCE_NAME,
                SOURCE_URL,
                null,
                reportPeriod,
                "DATA_MISSING",
                null,
                null,
                excerpt,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                "DATA_MISSING"
        );
    }

    private BigDecimal decimal(Map<String, JsonNode> row, String field) {
        JsonNode value = row.get(field);
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            return null;
        }
        return new BigDecimal(value.asText());
    }

    private String text(Map<String, JsonNode> row, String field, String fallback) {
        JsonNode value = row.get(field);
        if (value == null || value.isNull() || value.asText("").isBlank()) {
            return fallback;
        }
        return value.asText();
    }

    private boolean isConcreteReportPeriod(String reportPeriod) {
        return reportPeriod != null && reportPeriod.matches("\\d{8}");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
