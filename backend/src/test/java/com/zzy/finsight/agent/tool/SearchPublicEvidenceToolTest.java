package com.zzy.finsight.agent.tool;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.EvidenceMemory;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.search.SearchResult;
import com.zzy.finsight.search.SearchService;
import com.zzy.finsight.search.TavilyExtractClient;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SearchPublicEvidenceToolTest {

    @Test
    void usesResearchQuestionToBuildPublicEvidenceQuery() {
        SearchService searchService = mock(SearchService.class);
        TavilyExtractClient extractClient = mock(TavilyExtractClient.class);
        EvidenceMemory memory = mock(EvidenceMemory.class);
        SearchResult result = new SearchResult(
                "tavily",
                "贵州茅台公告",
                "https://example.com/notice",
                "贵州茅台发布季度公告，主营业务数据和毛利率变化原因均有详细披露，并说明产品结构、渠道节奏、成本变化和报告期口径。"
        );
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of(result));
        when(extractClient.extract(any(), anyInt())).thenReturn(List.of());
        when(memory.merge(any(), anyString(), any(), anyLong(), anyString(), anyString()))
                .thenAnswer(invocation -> invocation
                        .<com.zzy.finsight.domain.stock.FinancialDataCollection>getArgument(2)
                        .evidenceItems());
        SearchPublicEvidenceTool tool = new SearchPublicEvidenceTool(
                searchService, extractClient, memory, 3
        );
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion("最近两个季度毛利率下降的原因是什么");
        AgentState state = new AgentState();
        state.setRequest(request);
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));

        ToolResult toolResult = tool.execute(
                new ToolContext(7L, 11L, request, state),
                new SearchPublicEvidenceArguments("")
        );

        ArgumentCaptor<String> query = ArgumentCaptor.forClass(String.class);
        verify(searchService).search(query.capture(), anyInt());
        assertThat(query.getValue())
                .contains("600519.SH", "贵州茅台", "最近两个季度毛利率下降的原因是什么");
        assertThat(toolResult.status()).isEqualTo("SUCCESS");
        assertThat(toolResult.evidenceItems()).singleElement()
                .extracting(FinancialEvidenceItem::metricName)
                .isEqualTo("RESEARCH_QUESTION_EVIDENCE");
    }

    @Test
    void failsExplicitlyWhenSearchReturnsNoUsableEvidence() {
        SearchService searchService = mock(SearchService.class);
        TavilyExtractClient extractClient = mock(TavilyExtractClient.class);
        SearchPublicEvidenceTool tool = new SearchPublicEvidenceTool(
                searchService, extractClient, mock(EvidenceMemory.class), 3
        );
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion("分析公告事件");
        AgentState state = new AgentState();
        state.setRequest(request);
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        when(searchService.search(anyString(), anyInt())).thenReturn(List.of());

        ToolResult result = tool.execute(
                new ToolContext(7L, 11L, request, state),
                new SearchPublicEvidenceArguments("")
        );

        assertThat(result.status()).isEqualTo("FAILED");
        assertThat(result.errorCode()).isEqualTo("DATA_MISSING");
        assertThat(result.retryable()).isTrue();
    }
}
