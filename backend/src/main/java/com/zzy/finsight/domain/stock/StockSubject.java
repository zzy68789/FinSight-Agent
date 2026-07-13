package com.zzy.finsight.domain.stock;


/**
 * 表示已解析的股票或 ETF 研究主体。
 * @param ticker 证券代码。
 * @param exchange 证券交易所。
 * @param fullCode 带交易所后缀的完整证券代码。
 * @param companyName 公司名称。
 * @param industry 所属行业。
 * @param assetType 证券资产类型。
 */
public record StockSubject(
        String ticker,
        String exchange,
        String fullCode,
        String companyName,
        String industry,
        StockAssetType assetType
) {
    public StockSubject(String ticker, String exchange, String fullCode, String companyName, String industry) {
        this(ticker, exchange, fullCode, companyName, industry, StockAssetType.EQUITY);
    }

    public StockSubject {
        assetType = assetType == null ? StockAssetType.EQUITY : assetType;
    }

    public boolean isEtf() {
        return StockAssetType.ETF.equals(assetType);
    }
}
