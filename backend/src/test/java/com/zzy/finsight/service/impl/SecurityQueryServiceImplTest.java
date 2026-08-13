package com.zzy.finsight.service.impl;

import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.reference.AShareCompanyDirectory;
import com.zzy.finsight.dto.stock.SecurityMatchType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityQueryServiceImplTest {
    private final AShareCompanyDirectory directory = new AShareCompanyDirectory();
    private final SecurityQueryServiceImpl service = new SecurityQueryServiceImpl(
            new StockCodeResolver(directory), directory
    );

    @Test
    void searchesExactCompanyNameAndReturnsCanonicalCode() {
        var candidates = service.search("贵州茅台");

        assertThat(candidates).hasSize(1);
        assertThat(candidates.get(0).fullCode()).isEqualTo("600519.SH");
        assertThat(candidates.get(0).matchType()).isEqualTo(SecurityMatchType.EXACT_NAME);
        assertThat(candidates.get(0).nameResolved()).isTrue();
    }

    @Test
    void returnsMultipleCandidatesWithoutGuessingAmbiguousName() {
        var candidates = service.search("银行");

        assertThat(candidates).extracting(candidate -> candidate.companyName())
                .containsExactly("平安银行", "招商银行");
    }

    @Test
    void previewsKnownStockWithResolvedName() {
        var preview = service.preview("600519");

        assertThat(preview.resolved()).isTrue();
        assertThat(preview.nameResolved()).isTrue();
        assertThat(preview.fullCode()).isEqualTo("600519.SH");
        assertThat(preview.companyName()).isEqualTo("贵州茅台");
        assertThat(preview.assetType()).isEqualTo(StockAssetType.EQUITY);
    }

    @Test
    void keepsSupportedUnknownEtfWithoutInventingName() {
        var preview = service.preview("588200");

        assertThat(preview.resolved()).isTrue();
        assertThat(preview.nameResolved()).isFalse();
        assertThat(preview.companyName()).isEmpty();
        assertThat(preview.fullCode()).isEqualTo("588200.SH");
        assertThat(preview.assetType()).isEqualTo(StockAssetType.ETF);
    }

    @Test
    void returnsTypedUnresolvedPreviewForInvalidCode() {
        var preview = service.preview("900001");

        assertThat(preview.resolved()).isFalse();
        assertThat(preview.fullCode()).isEmpty();
        assertThat(preview.message()).contains("当前仅支持沪深 A 股普通股票代码");
    }
}
