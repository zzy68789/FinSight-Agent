package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.EtfDeepData;
import com.zzy.finsight.domain.stock.MarketDataPoint;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;


import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.convert.DurationStyle;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
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

    @Autowired
    public TushareMarketDataProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            @Value("${finsight.market.tushare.enabled:false}") boolean enabled,
            @Value("${finsight.market.tushare.base-url:https://api.tushare.pro}") String baseUrl,
            @Value("${finsight.market.tushare.api-key:}") String token,
            @Value("${finsight.market.tushare.timeout:10s}") String timeout
    ) {
        this(restClientBuilder, objectMapper, enabled, baseUrl, token, timeout, true);
    }

    /**
     * 创建 TuShare Provider；测试可关闭底层请求工厂替换，以保留 MockRestServiceServer。
     */
    public TushareMarketDataProvider(
            RestClient.Builder restClientBuilder,
            ObjectMapper objectMapper,
            boolean enabled,
            String baseUrl,
            String token,
            String timeout,
            boolean configureTransportTimeout
    ) {
        if (configureTransportTimeout) {
            java.time.Duration requestTimeout = DurationStyle.detectAndParse(timeout);
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(requestTimeout);
            requestFactory.setReadTimeout(requestTimeout);
            restClientBuilder.requestFactory(requestFactory);
        }
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
        return collectWithTrace(ownerId, subject, reportPeriod, searchMode).evidenceItems();
    }

    /** 采集证据，并为 ETF 附带行情序列和净值深度快照。 */
    @Override
    public FinancialDataCollection collectWithTrace(
            long ownerId,
            StockSubject subject,
            String reportPeriod,
            String searchMode
    ) {
        if (!enabled || token.isBlank() || "document".equalsIgnoreCase(searchMode)) {
            return FinancialDataCollection.evidenceOnly(List.of());
        }
        try {
            if (subject.isEtf()) {
                return etfDeepCollection(subject, reportPeriod);
            }
            List<FinancialEvidenceItem> items = new ArrayList<>();
            items.addAll(incomeEvidence(subject, reportPeriod));
            items.addAll(balanceSheetEvidence(subject, reportPeriod));
            items.addAll(cashFlowEvidence(subject, reportPeriod));
            items.addAll(dailyBasicEvidence(subject, reportPeriod));
            if (items.isEmpty()) {
                return FinancialDataCollection.evidenceOnly(List.of(
                        dataMissing(reportPeriod, "TuShare Pro 未返回可用行情或财务数据。")
                ));
            }
            return FinancialDataCollection.evidenceOnly(items);
        } catch (RuntimeException e) {
            return FinancialDataCollection.evidenceOnly(List.of(
                    dataMissing(reportPeriod, "TuShare Pro 数据不可用：" + e.getMessage())
            ));
        }
    }

    /** 聚合 ETF 日线、基金资料和净值；单个深度接口失败时保留其余可用结果。 */
    private FinancialDataCollection etfDeepCollection(StockSubject subject, String reportPeriod) {
        List<FinancialEvidenceItem> items = new ArrayList<>();
        List<Map<String, JsonNode>> dailyRows = safeEtfQuery(
                "fund_daily",
                marketSeriesParams(subject, reportPeriod),
                "ts_code,trade_date,open,high,low,close,pre_close,pct_chg,vol,amount",
                items,
                reportPeriod,
                "ETF行情"
        );
        List<MarketDataPoint> marketSeries = marketSeries(dailyRows);
        if (!dailyRows.isEmpty()) {
            addLatestMarketEvidence(items, dailyRows.get(0), reportPeriod);
        }

        List<Map<String, JsonNode>> basicRows = safeEtfQuery(
                "fund_basic",
                Map.of("ts_code", subject.fullCode()),
                "ts_code,name,management,custodian,fund_type,list_date,m_fee,c_fee,benchmark,invest_type",
                items,
                reportPeriod,
                "ETF基础资料"
        );
        List<Map<String, JsonNode>> navRows = safeEtfQuery(
                "fund_nav",
                navParams(subject, reportPeriod),
                "ts_code,ann_date,nav_date,unit_nav,accum_nav,total_netasset",
                items,
                reportPeriod,
                "ETF净值"
        );
        EtfDeepData deepData = etfDeepData(basicRows, navRows, dailyRows);
        addEtfDeepEvidence(items, deepData, reportPeriod);
        if (items.isEmpty()) {
            items.add(dataMissing(reportPeriod, "TuShare Pro 未返回可用 ETF 深度数据。"));
        }
        return new FinancialDataCollection(items, null, marketSeries, deepData);
    }

    private void addLatestMarketEvidence(
            List<FinancialEvidenceItem> items,
            Map<String, JsonNode> latest,
            String reportPeriod
    ) {
        String period = text(latest, "trade_date", reportPeriod);
        addRawMetric(items, period, FinancialMetricInputNames.ETF_CLOSE, latest, "close", "ETF收盘价");
        addRawMetric(items, period, FinancialMetricInputNames.ETF_PCT_CHANGE, latest, "pct_chg", "ETF涨跌幅");
        addRawMetric(items, period, FinancialMetricInputNames.ETF_AMOUNT, latest, "amount", "ETF成交额");
    }

    private List<MarketDataPoint> marketSeries(List<Map<String, JsonNode>> rows) {
        return rows.stream()
                .limit(60)
                .map(row -> new MarketDataPoint(
                        text(row, "trade_date", ""),
                        decimal(row, "open"),
                        decimal(row, "high"),
                        decimal(row, "low"),
                        decimal(row, "close"),
                        decimal(row, "pre_close"),
                        decimal(row, "pct_chg"),
                        decimal(row, "vol"),
                        decimal(row, "amount")
                ))
                .sorted(Comparator.comparing(MarketDataPoint::tradeDate))
                .toList();
    }

    private EtfDeepData etfDeepData(
            List<Map<String, JsonNode>> basicRows,
            List<Map<String, JsonNode>> navRows,
            List<Map<String, JsonNode>> dailyRows
    ) {
        Map<String, JsonNode> basic = basicRows.isEmpty() ? Map.of() : basicRows.get(0);
        List<Map<String, JsonNode>> sortedNav = sortByDate(navRows, "nav_date");
        Map<String, JsonNode> nav = sortedNav.isEmpty() ? Map.of() : sortedNav.get(0);
        String navDate = text(nav, "nav_date", "");
        BigDecimal unitNav = decimal(nav, "unit_nav");
        BigDecimal sameDayClose = dailyRows.stream()
                .filter(row -> navDate.equals(text(row, "trade_date", "")))
                .map(row -> decimal(row, "close"))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .orElse(null);
        BigDecimal premiumDiscountRate = unitNav == null || sameDayClose == null
                || BigDecimal.ZERO.compareTo(unitNav) == 0
                ? null
                : sameDayClose.subtract(unitNav)
                        .divide(unitNav, 8, RoundingMode.HALF_UP)
                        .multiply(BigDecimal.valueOf(100))
                        .setScale(4, RoundingMode.HALF_UP);
        return new EtfDeepData(
                text(basic, "name", ""),
                text(basic, "management", ""),
                text(basic, "custodian", ""),
                text(basic, "fund_type", ""),
                text(basic, "invest_type", ""),
                text(basic, "benchmark", ""),
                text(basic, "list_date", ""),
                decimal(basic, "m_fee"),
                decimal(basic, "c_fee"),
                navDate,
                unitNav,
                decimal(nav, "accum_nav"),
                decimal(nav, "total_netasset"),
                premiumDiscountRate
        );
    }

    private void addEtfDeepEvidence(
            List<FinancialEvidenceItem> items,
            EtfDeepData deepData,
            String reportPeriod
    ) {
        String period = deepData.navDate().isBlank() ? reportPeriod : deepData.navDate();
        addEtfValue(items, period, FinancialMetricInputNames.ETF_UNIT_NAV,
                deepData.unitNav(), "ETF单位净值");
        addEtfValue(items, period, FinancialMetricInputNames.ETF_ACCUMULATED_NAV,
                deepData.accumulatedNav(), "ETF累计净值");
        addEtfValue(items, period, FinancialMetricInputNames.ETF_TOTAL_NET_ASSET,
                deepData.totalNetAsset(), "ETF合计资产净值（数据源原始口径）");
        addEtfValue(items, period, FinancialMetricInputNames.ETF_PREMIUM_DISCOUNT_RATE,
                deepData.premiumDiscountRate(), "ETF同日折溢价率");
        if (!deepData.fundName().isBlank() || !deepData.management().isBlank() || !deepData.benchmark().isBlank()) {
            String excerpt = "ETF资料：简称=" + blankToDefault(deepData.fundName(), "-")
                    + "，管理人=" + blankToDefault(deepData.management(), "-")
                    + "，托管人=" + blankToDefault(deepData.custodian(), "-")
                    + "，基金类型=" + blankToDefault(deepData.fundType(), "-")
                    + "，业绩比较基准=" + blankToDefault(deepData.benchmark(), "-");
            items.add(evidence(period, FinancialMetricInputNames.ETF_PROFILE, null, null, excerpt));
        }
    }

    private void addEtfValue(
            List<FinancialEvidenceItem> items,
            String period,
            String metricName,
            BigDecimal value,
            String label
    ) {
        if (value != null) {
            items.add(evidence(period, metricName, value, value, label + " " + value.toPlainString()));
        }
    }

    private List<Map<String, JsonNode>> safeEtfQuery(
            String apiName,
            Map<String, Object> params,
            String fields,
            List<FinancialEvidenceItem> items,
            String reportPeriod,
            String label
    ) {
        try {
            return sortByDate(query(apiName, params, fields), dateField(apiName));
        } catch (RuntimeException e) {
            items.add(metricMissing(reportPeriod, "ETF_" + apiName.toUpperCase(java.util.Locale.ROOT),
                    label + "不可用：" + e.getMessage()));
            return List.of();
        }
    }

    private String dateField(String apiName) {
        return "fund_nav".equals(apiName) ? "nav_date" : "fund_daily".equals(apiName) ? "trade_date" : "list_date";
    }

    private Map<String, Object> marketSeriesParams(StockSubject subject, String reportPeriod) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", subject.fullCode());
        LocalDate endDate = parsePeriod(reportPeriod);
        if (endDate != null) {
            params.put("start_date", endDate.minusDays(120).format(BASIC_DATE));
            params.put("end_date", endDate.format(BASIC_DATE));
        }
        return params;
    }

    private Map<String, Object> navParams(StockSubject subject, String reportPeriod) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("ts_code", subject.fullCode());
        LocalDate endDate = parsePeriod(reportPeriod);
        if (endDate != null) {
            params.put("start_date", endDate.minusDays(120).format(BASIC_DATE));
            params.put("end_date", endDate.format(BASIC_DATE));
        }
        return params;
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
        return query(apiName, params, fields);
    }

    private List<Map<String, JsonNode>> query(String apiName, Map<String, Object> params, String fields) {
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
