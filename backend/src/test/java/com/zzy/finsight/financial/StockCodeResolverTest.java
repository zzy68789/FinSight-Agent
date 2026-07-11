package com.zzy.finsight.financial;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StockCodeResolverTest {

    private final StockCodeResolver resolver = new StockCodeResolver();

    @Test
    void resolvesShanghaiAShareCode() {
        StockSubject subject = resolver.resolve("600519");

        assertThat(subject.ticker()).isEqualTo("600519");
        assertThat(subject.exchange()).isEqualTo("SH");
        assertThat(subject.fullCode()).isEqualTo("600519.SH");
    }

    @Test
    void resolvesKnownCompanyProfileFromLocalDirectory() {
        StockSubject subject = resolver.resolve("600519");

        assertThat(subject.companyName()).isEqualTo("贵州茅台");
        assertThat(subject.industry()).isEqualTo("食品饮料");
    }

    @Test
    void resolvesShenzhenAShareCode() {
        StockSubject subject = resolver.resolve("300750");

        assertThat(subject.ticker()).isEqualTo("300750");
        assertThat(subject.exchange()).isEqualTo("SZ");
        assertThat(subject.fullCode()).isEqualTo("300750.SZ");
    }

    @Test
    void keepsExplicitExchangeSuffix() {
        StockSubject subject = resolver.resolve("000001.SZ");

        assertThat(subject.ticker()).isEqualTo("000001");
        assertThat(subject.exchange()).isEqualTo("SZ");
        assertThat(subject.fullCode()).isEqualTo("000001.SZ");
    }

    @Test
    void rejectsUnsupportedCode() {
        assertThatThrownBy(() -> resolver.resolve("900001"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("当前仅支持沪深 A 股普通股票代码");
    }

    @Test
    void resolvesShanghaiEtfCode() {
        StockSubject subject = resolver.resolve("588200");

        assertThat(subject.ticker()).isEqualTo("588200");
        assertThat(subject.exchange()).isEqualTo("SH");
        assertThat(subject.fullCode()).isEqualTo("588200.SH");
        assertThat(subject.assetType()).isEqualTo(StockAssetType.ETF);
        assertThat(subject.companyName()).isEqualTo("待识别ETF");
        assertThat(subject.industry()).isEqualTo("ETF");
    }

    @Test
    void resolvesShenzhenEtfCode() {
        StockSubject subject = resolver.resolve("159915");

        assertThat(subject.ticker()).isEqualTo("159915");
        assertThat(subject.exchange()).isEqualTo("SZ");
        assertThat(subject.fullCode()).isEqualTo("159915.SZ");
        assertThat(subject.assetType()).isEqualTo(StockAssetType.ETF);
    }
}
