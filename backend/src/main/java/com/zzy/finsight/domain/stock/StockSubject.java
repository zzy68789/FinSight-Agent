package com.zzy.finsight.domain.stock;


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
