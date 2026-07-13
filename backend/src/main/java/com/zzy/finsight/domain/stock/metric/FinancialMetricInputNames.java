package com.zzy.finsight.domain.stock.metric;


/**
 * 集中定义金融指标输入字段名称。
 */
public final class FinancialMetricInputNames {
    public static final String OPERATING_REVENUE = "OPERATING_REVENUE";
    public static final String OPERATING_REVENUE_PRIOR = "OPERATING_REVENUE_PRIOR";
    public static final String GROSS_PROFIT = "GROSS_PROFIT";
    public static final String OPERATING_COST = "OPERATING_COST";
    public static final String NET_PROFIT = "NET_PROFIT";
    public static final String BEGINNING_EQUITY = "BEGINNING_EQUITY";
    public static final String ENDING_EQUITY = "ENDING_EQUITY";
    /**
     * 兼容上传报告中直接披露的平均净资产，不再用于映射期末权益。
     * @deprecated 新采集链路应提供年初与期末归母权益。
     */
    @Deprecated
    public static final String AVERAGE_EQUITY = "AVERAGE_EQUITY";
    public static final String TOTAL_LIABILITIES = "TOTAL_LIABILITIES";
    public static final String TOTAL_ASSETS = "TOTAL_ASSETS";
    public static final String OPERATING_CASH_FLOW = "OPERATING_CASH_FLOW";
    public static final String ETF_CLOSE = "ETF_CLOSE";
    public static final String ETF_PCT_CHANGE = "ETF_PCT_CHANGE";
    public static final String ETF_AMOUNT = "ETF_AMOUNT";

    private FinancialMetricInputNames() {
    }
}
