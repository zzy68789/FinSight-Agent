package com.zzy.finsight.agent.planning;

import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.dto.agent.ResearchIntent;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResearchIntentPolicyTest {
    private final ResearchIntentPolicy policy = new ResearchIntentPolicy();

    @Test
    void acceptsEtfTrackingOnlyForEtf() {
        assertThatCode(() -> policy.validate(ResearchIntent.ETF_TRACKING, StockAssetType.ETF))
                .doesNotThrowAnyException();
        assertThatThrownBy(() -> policy.validate(ResearchIntent.ETF_TRACKING, StockAssetType.EQUITY))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("ETF 跟踪");
    }

    @Test
    void rejectsFinancialQualityForEtf() {
        assertThatThrownBy(() -> policy.validate(ResearchIntent.FINANCIAL_QUALITY, StockAssetType.ETF))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("财务质量");
    }
}
