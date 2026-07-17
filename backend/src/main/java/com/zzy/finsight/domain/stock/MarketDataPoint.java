package com.zzy.finsight.domain.stock;

import java.math.BigDecimal;

/**
 * 表示可用于行情图和报告回放的单日证券行情。
 * @param tradeDate 交易日期，格式为 yyyyMMdd。
 * @param open 开盘价。
 * @param high 最高价。
 * @param low 最低价。
 * @param close 收盘价。
 * @param previousClose 前收盘价。
 * @param changePercent 涨跌幅，单位为百分比。
 * @param volume 成交量，保留数据源原始口径。
 * @param amount 成交额，保留数据源原始口径。
 */
public record MarketDataPoint(
        String tradeDate,
        BigDecimal open,
        BigDecimal high,
        BigDecimal low,
        BigDecimal close,
        BigDecimal previousClose,
        BigDecimal changePercent,
        BigDecimal volume,
        BigDecimal amount
) {
}
