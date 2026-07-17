package com.zzy.finsight.domain.stock;

import java.math.BigDecimal;

/**
 * 表示 ETF 基础资料、净值和二级市场折溢价的深度快照。
 * @param fundName 基金简称。
 * @param management 基金管理人。
 * @param custodian 基金托管人。
 * @param fundType 基金投资类型。
 * @param investType 基金投资风格。
 * @param benchmark 业绩比较基准。
 * @param listDate 上市日期，格式为 yyyyMMdd。
 * @param managementFee 管理费率，保留数据源原始口径。
 * @param custodyFee 托管费率，保留数据源原始口径。
 * @param navDate 净值日期，格式为 yyyyMMdd。
 * @param unitNav 单位净值。
 * @param accumulatedNav 累计净值。
 * @param totalNetAsset 合计资产净值，保留数据源原始口径。
 * @param premiumDiscountRate 同一日期收盘价相对单位净值的折溢价率，单位为百分比。
 */
public record EtfDeepData(
        String fundName,
        String management,
        String custodian,
        String fundType,
        String investType,
        String benchmark,
        String listDate,
        BigDecimal managementFee,
        BigDecimal custodyFee,
        String navDate,
        BigDecimal unitNav,
        BigDecimal accumulatedNav,
        BigDecimal totalNetAsset,
        BigDecimal premiumDiscountRate
) {
    /** 创建空的 ETF 深度快照，供部分数据源降级时使用。 */
    public static EtfDeepData empty() {
        return new EtfDeepData("", "", "", "", "", "", "", null, null,
                "", null, null, null, null);
    }
}
