package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.search.SearchResult;
import com.zzy.finsight.search.SearchService;
import com.zzy.finsight.search.TavilyExtractClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PublicMarketDataProviderTest {

    @Test
    void prefersExtractedContentAndUsesLatestAsPublicEvidencePeriod() {
        SearchService searchService = mock(SearchService.class);
        TavilyExtractClient extractClient = mock(TavilyExtractClient.class);
        PublicMarketDataProvider provider = new PublicMarketDataProvider(searchService, extractClient, 3);
        SearchResult searchResult = new SearchResult(
                "tavily",
                "贵州茅台经营公告",
                "https://www.sse.com.cn/news/600519",
                "这是长度足够的搜索摘要，用于在正文提取失败时作为降级证据继续执行报告工作流。"
        );
        when(searchService.search(org.mockito.ArgumentMatchers.anyString(), eq(5))).thenReturn(List.of(searchResult));
        when(extractClient.extract(anyList(), eq(3))).thenReturn(List.of(new SearchResult(
                "tavily-extract",
                searchResult.title(),
                searchResult.url(),
                "贵州茅台发布经营公告，公告正文披露了报告期经营变化、风险提示和后续安排。"
        )));

        List<FinancialEvidenceItem> items = provider.collect(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260331",
                "hybrid"
        );

        assertThat(items).singleElement().satisfies(item -> {
            assertThat(item.reportPeriod()).isEqualTo("latest");
            assertThat(item.excerpt()).contains("公告正文");
            assertThat(item.confidence()).isEqualByComparingTo("0.80");
        });
    }
}
