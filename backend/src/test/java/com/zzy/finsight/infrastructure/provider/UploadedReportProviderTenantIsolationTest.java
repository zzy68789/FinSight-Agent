package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.component.marketdata.FinancialEvidenceParser;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.rag.RagRetrievalResult;
import com.zzy.finsight.service.RagService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class UploadedReportProviderTenantIsolationTest {

    @Test
    void retrievalUsesWorkflowOwnerKnowledgeSpace() {
        RagService ragService = mock(RagService.class);
        UploadedReportProvider provider = new UploadedReportProvider(ragService, new FinancialEvidenceParser());
        StockSubject subject = new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料");
        when(ragService.retrieveWithTrace(eq(7L), contains("600519.SH"), eq(6)))
                .thenReturn(RagRetrievalResult.empty("query", 0.2d, 1L));

        var collection = provider.collectWithTrace(7L, subject, "latest", "hybrid");

        assertThat(collection.evidenceItems()).isEmpty();
        verify(ragService).retrieveWithTrace(eq(7L), contains("600519.SH"), eq(6));
    }
}
