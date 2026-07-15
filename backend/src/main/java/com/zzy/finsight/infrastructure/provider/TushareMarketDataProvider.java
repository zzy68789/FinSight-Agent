package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
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
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 从 TuShare 采集股票和 ETF 财务行情证据。
 */
@Component
@Order(5)
public class TushareMarketDataProvider implements FinancialDataProvider {
    private static final String SOURCE_TYPE = "AUTHORIZED_MARKET";
    private static final String SOURCE_NAME = "TuShare Pro";
    private static final String SOURCE_URL = "https://tushare.pro";
    private static final BigDecimal CONFIDENCE = new BigDecimal("0.90");
    private static final BigDecimal YUAN_PER_YI = new BigDecimal("100000000");
    private static final BigDecimal WAN_PER_YI = new BigDecimal("10000");
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

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
    public List<FinancialEvidenceItem> collect(long ownerId, StockSubject subject, String reportPeriod, String searchMode) {
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

    /** 选择唯一财报版本，并严格匹配相差一年的上年同期利润表。 */
    private List<FinancialEvidenceItem> incomeEvidence(StockSubject subject, String reportPeriod) {
        String fields = "ts_code,ann_date,f_ann_date,end_date,report_type,update_flag,revenue,oper_cost,n_income_attr_p";
        List<Map<String, JsonNode>> rows = query(
                "income",
                subject,
                reportPeriod,
                fields
        );
        Optional<Map<String, JsonNode>> currentRow = selectFinancialRow(rows, concretePeriod(reportPeriod));
        if (currentRow.isEmpty()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Map<String, JsonNode> latest = currentRow.orElseThrow();
        String period = text(latest, "end_date", reportPeriod);
        addMoney(items, period, FinancialMetricInputNames.OPERATING_REVENUE, latest, "revenue", "营业收入");
        addMoney(items, period, FinancialMetricInputNames.OPERATING_COST, latest, "oper_cost", "营业成本");
        addMoney(items, period, FinancialMetricInputNames.NET_PROFIT, latest, "n_income_attr_p", "归母净利润");

        String priorPeriod = priorYearPeriod(period);
        if (!priorPeriod.isBlank()) {
            List<Map<String, JsonNode>> priorRows = isConcreteReportPeriod(reportPeriod)
                    ? query("income", subject, priorPeriod, fields)
                    : rows;
            Optional<Map<String, JsonNode>> priorRow = selectFinancialRow(priorRows, priorPeriod);
            if (priorRow.isPresent()) {
                addMoney(items, priorPeriod, FinancialMetricInputNames.OPERATING_REVENUE_PRIOR,
                        priorRow.orElseThrow(), "revenue", "上年同期营业收入");
            } else {
                items.add(metricMissing(
                        priorPeriod,
                        FinancialMetricInputNames.OPERATING_REVENUE_PRIOR,
                        "TuShare Pro 未返回与本期严格对应的上年同期营业收入。"
                ));
            }
        }
        return items;
    }

    /** 采集期末资产负债和当前财年年初权益，供 ROE 使用。 */
    private List<FinancialEvidenceItem> balanceSheetEvidence(StockSubject subject, String reportPeriod) {
        String fields = "ts_code,ann_date,f_ann_date,end_date,report_type,update_flag,total_assets,total_liab,total_hldr_eqy_exc_min_int";
        List<Map<String, JsonNode>> rows = query(
                "balancesheet",
                subject,
                reportPeriod,
                fields
        );
        Optional<Map<String, JsonNode>> currentRow = selectFinancialRow(rows, concretePeriod(reportPeriod));
        if (currentRow.isEmpty()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Map<String, JsonNode> latest = currentRow.orElseThrow();
        String period = text(latest, "end_date", reportPeriod);
        addMoney(items, period, FinancialMetricInputNames.TOTAL_ASSETS, latest, "total_assets", "资产总额");
        addMoney(items, period, FinancialMetricInputNames.TOTAL_LIABILITIES, latest, "total_liab", "负债总额");
        addMoney(items, period, FinancialMetricInputNames.ENDING_EQUITY, latest,
                "total_hldr_eqy_exc_min_int", "期末归母权益");

        String beginningPeriod = beginningOfFiscalYearPeriod(period);
        if (!beginningPeriod.isBlank()) {
            List<Map<String, JsonNode>> beginningRows = isConcreteReportPeriod(reportPeriod)
                    ? query("balancesheet", subject, beginningPeriod, fields)
                    : rows;
            Optional<Map<String, JsonNode>> beginningRow = selectFinancialRow(beginningRows, beginningPeriod);
            if (beginningRow.isPresent()) {
                addMoney(items, beginningPeriod, FinancialMetricInputNames.BEGINNING_EQUITY,
                        beginningRow.orElseThrow(), "total_hldr_eqy_exc_min_int", "年初归母权益");
            } else {
                items.add(metricMissing(
                        beginningPeriod,
                        FinancialMetricInputNames.BEGINNING_EQUITY,
                        "TuShare Pro 未返回当前财年年初归母权益。"
                ));
            }
        }
        return items;
    }

    private List<FinancialEvidenceItem> cashFlowEvidence(StockSubject subject, String reportPeriod) {
        List<Map<String, JsonNode>> rows = query(
                "cashflow",
                subject,
                reportPeriod,
                "ts_code,ann_date,f_ann_date,end_date,report_type,update_flag,n_cashflow_act"
        );
        Optional<Map<String, JsonNode>> currentRow = selectFinancialRow(rows, concretePeriod(reportPeriod));
        if (currentRow.isEmpty()) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Map<String, JsonNode> latest = currentRow.orElseThrow();
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

    /** 按报告期、标准合并报表、更新标记和公告日期选择唯一财报版本。 */
    private Optional<Map<String, JsonNode>> selectFinancialRow(List<Map<String, JsonNode>> rows, String requestedPeriod) {
        String targetPeriod = requestedPeriod;
        if (targetPeriod == null || targetPeriod.isBlank()) {
            targetPeriod = rows.stream()
                    .map(row -> text(row, "end_date", ""))
                    .filter(value -> !value.isBlank())
                    .max(String::compareTo)
                    .orElse("");
        }
        if (targetPeriod.isBlank()) {
            return Optional.empty();
        }
        String finalTargetPeriod = targetPeriod;
        List<Map<String, JsonNode>> candidates = rows.stream()
                .filter(row -> finalTargetPeriod.equals(text(row, "end_date", "")))
                .toList();
        List<Map<String, JsonNode>> standardReports = candidates.stream()
                .filter(row -> "1".equals(text(row, "report_type", "")))
                .toList();
        List<Map<String, JsonNode>> eligible = standardReports.isEmpty() ? candidates : standardReports;
        return eligible.stream()
                .max(Comparator
                        .comparingInt((Map<String, JsonNode> row) -> integer(row, "update_flag"))
                        .thenComparing(row -> text(row, "f_ann_date", ""))
                        .thenComparing(row -> text(row, "ann_date", "")));
    }

    private String concretePeriod(String reportPeriod) {
        return isConcreteReportPeriod(reportPeriod) ? reportPeriod : "";
    }

    /** 将当前报告期映射到严格的上年同期。 */
    private String priorYearPeriod(String period) {
        LocalDate date = parsePeriod(period);
        return date == null ? "" : date.minusYears(1).format(BASIC_DATE);
    }

    /** 将报告期映射到当前财年的年初权益时点。 */
    private String beginningOfFiscalYearPeriod(String period) {
        LocalDate date = parsePeriod(period);
        return date == null ? "" : LocalDate.of(date.getYear() - 1, 12, 31).format(BASIC_DATE);
    }

    private LocalDate parsePeriod(String period) {
        if (!isConcreteReportPeriod(period)) {
            return null;
        }
        try {
            return LocalDate.parse(period, BASIC_DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
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
        return metricMissing(reportPeriod, "DATA_MISSING", excerpt);
    }

    private FinancialEvidenceItem metricMissing(String reportPeriod, String metricName, String excerpt) {
        return new FinancialEvidenceItem(
                SOURCE_TYPE,
                SOURCE_NAME,
                SOURCE_URL,
                null,
                reportPeriod,
                metricName,
                null,
                null,
                excerpt,
                BigDecimal.ZERO,
                LocalDateTime.now(),
                FinancialEvidenceIssueCodes.DATA_MISSING
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

    private int integer(Map<String, JsonNode> row, String field) {
        JsonNode value = row.get(field);
        return value == null || value.isNull() ? 0 : value.asInt(0);
    }

    private boolean isConcreteReportPeriod(String reportPeriod) {
        return reportPeriod != null && reportPeriod.matches("\\d{8}");
    }

    private static String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
