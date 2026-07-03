package com.zzy.drai.financial;

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
                .hasMessageContaining("仅支持 A 股");
    }
}
