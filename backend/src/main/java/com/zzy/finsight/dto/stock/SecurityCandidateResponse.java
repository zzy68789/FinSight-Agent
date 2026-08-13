package com.zzy.finsight.dto.stock;

import com.zzy.finsight.domain.stock.StockAssetType;

/**
 * 表示证券代码或名称搜索返回的一个候选项。
 * @param ticker 六位证券代码。
 * @param fullCode 带交易所后缀的规范化代码。
 * @param companyName 已确认的证券名称；主档缺失时为空字符串。
 * @param industry 已确认的行业或资产类别；未知时为空字符串。
 * @param assetType 证券资产类型。
 * @param matchType 候选项与输入的匹配方式。
 * @param nameResolved 是否已经从本地主档确认证券名称。
 */
public record SecurityCandidateResponse(
        String ticker,
        String fullCode,
        String companyName,
        String industry,
        StockAssetType assetType,
        SecurityMatchType matchType,
        boolean nameResolved
) {
}
