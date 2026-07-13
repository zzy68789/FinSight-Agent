package com.zzy.finsight.domain.stock.metric;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 集中维护金融指标定义及目录版本。
 */
public class MetricDefinitionCatalog {
    public static final String CATALOG_VERSION = "financial-metrics-v1";

    private final Map<String, MetricDefinition> definitions;

    public MetricDefinitionCatalog() {
        Map<String, MetricDefinition> items = new LinkedHashMap<>();
        register(items, "revenue_yoy", "营收同比", "（本期营业收入 - 上年同期营业收入）/ 上年同期营业收入",
                FinancialMetricInputNames.OPERATING_REVENUE, FinancialMetricInputNames.OPERATING_REVENUE_PRIOR);
        register(items, "gross_margin_profit", "毛利率", "毛利 / 营业收入",
                FinancialMetricInputNames.GROSS_PROFIT, FinancialMetricInputNames.OPERATING_REVENUE);
        register(items, "gross_margin_cost", "毛利率", "（营业收入 - 营业成本）/ 营业收入",
                FinancialMetricInputNames.OPERATING_REVENUE, FinancialMetricInputNames.OPERATING_COST);
        register(items, "net_margin", "净利率", "净利润 / 营业收入",
                FinancialMetricInputNames.NET_PROFIT, FinancialMetricInputNames.OPERATING_REVENUE);
        register(items, "roe", "ROE", "净利润 / 平均净资产",
                FinancialMetricInputNames.NET_PROFIT, FinancialMetricInputNames.AVERAGE_EQUITY);
        register(items, "debt_ratio", "资产负债率", "总负债 / 总资产",
                FinancialMetricInputNames.TOTAL_LIABILITIES, FinancialMetricInputNames.TOTAL_ASSETS);
        register(items, "cashflow_profit", "经营现金流 / 净利润", "经营活动现金流量净额 / 净利润",
                FinancialMetricInputNames.OPERATING_CASH_FLOW, FinancialMetricInputNames.NET_PROFIT);
        register(items, "etf_close", "ETF收盘价", "ETF二级市场收盘价", FinancialMetricInputNames.ETF_CLOSE);
        register(items, "etf_pct_change", "ETF涨跌幅", "ETF二级市场涨跌幅", FinancialMetricInputNames.ETF_PCT_CHANGE);
        register(items, "etf_amount", "ETF成交额", "ETF二级市场成交额", FinancialMetricInputNames.ETF_AMOUNT);
        definitions = Map.copyOf(items);
    }

    public MetricDefinition get(String code) {
        MetricDefinition definition = definitions.get(code);
        if (definition == null) {
            throw new IllegalArgumentException("未注册的金融指标：" + code);
        }
        return definition;
    }

    public String catalogVersion() {
        return CATALOG_VERSION;
    }

    private void register(Map<String, MetricDefinition> items, String code, String name, String formula, String... dependencies) {
        items.put(code, new MetricDefinition(code, name, formula, "v1", List.of(dependencies)));
    }
}
