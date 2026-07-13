package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.reference.AShareCompanyDirectory;
import com.zzy.finsight.component.workflow.StockCodeResolver;


import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AShareMasterDataProviderTest {

    private final AShareMasterDataProvider provider = new AShareMasterDataProvider(new AShareCompanyDirectory());

    @Test
    void emitsLocalContextEvidenceForKnownTicker() {
        StockSubject subject = new StockCodeResolver(new AShareCompanyDirectory()).resolve("600519");

        List<FinancialEvidenceItem> evidenceItems = provider.collect(subject, "latest", "hybrid");

        assertThat(evidenceItems).hasSize(1);
        assertThat(evidenceItems.get(0).sourceType()).isEqualTo("LOCAL_CONTEXT");
        assertThat(evidenceItems.get(0).metricName()).isEqualTo("LOCAL_CONTEXT");
        assertThat(evidenceItems.get(0).excerpt()).contains("贵州茅台", "食品饮料");
        assertThat(evidenceItems.get(0).effective()).isTrue();
    }

    @Test
    void skipsUnknownTickerWithoutCreatingFakeCompanyData() {
        StockSubject subject = new StockSubject("123456", "SZ", "123456.SZ", "待识别上市公司", "待识别行业");

        List<FinancialEvidenceItem> evidenceItems = provider.collect(subject, "latest", "hybrid");

        assertThat(evidenceItems).isEmpty();
    }
}
