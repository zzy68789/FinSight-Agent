package com.zzy.finsight.dto.stock;

import com.zzy.finsight.domain.stock.StockAssetType;

/**
 * 表示证券代码的只读预解析结果。
 * @param query 原始查询文本。
 * @param ticker 六位证券代码；解析失败时为空字符串。
 * @param fullCode 带交易所后缀的规范化代码；解析失败时为空字符串。
 * @param companyName 已确认的证券名称；主档缺失时为空字符串。
 * @param industry 已确认的行业或资产类别；未知时为空字符串。
 * @param assetType 证券资产类型；解析失败时为空。
 * @param resolved 代码格式和资产范围是否受系统支持。
 * @param nameResolved 是否已经从本地主档确认证券名称。
 * @param message 面向前端的解析结果说明。
 */
public record SecurityPreviewResponse(
        String query,
        String ticker,
        String fullCode,
        String companyName,
        String industry,
        StockAssetType assetType,
        boolean resolved,
        boolean nameResolved,
        String message
) {
}
