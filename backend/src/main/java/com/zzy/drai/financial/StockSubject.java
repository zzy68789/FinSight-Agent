package com.zzy.drai.financial;

public record StockSubject(
        String ticker,
        String exchange,
        String fullCode,
        String companyName,
        String industry
) {
}
