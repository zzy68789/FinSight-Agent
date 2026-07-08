package com.zzy.drai.agent.tool;

import com.zzy.drai.config.AgentProperties;
import com.zzy.drai.rag.RagDocument;
import com.zzy.drai.rag.RagService;
import com.zzy.drai.search.SearchResult;
import com.zzy.drai.search.SearchService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchToolsTest {

    private final RagService ragService = mock(RagService.class);
    private final SearchService searchService = mock(SearchService.class);
    private final AgentProperties properties = new AgentProperties();
    private final ResearchTools tools = new ResearchTools(ragService, searchService, properties);

    @BeforeEach
    void begin() {
        ToolCallLog.begin();
    }

    @AfterEach
    void clear() {
        ToolCallLog.clear();
    }

    @Test
    void searchLocalKnowledgeReturnsRelevantDocsAndRecordsCall() {
        when(ragService.retrieve("query", 5))
                .thenReturn(List.of(
                        new RagDocument("a.pdf", "relevant content", 0.8),
                        new RagDocument("b.pdf", "irrelevant", 0.05)));

        String result = tools.searchLocalKnowledge("query");

        assertThat(result).contains("a.pdf", "relevant content");
        assertThat(result).doesNotContain("b.pdf");
        assertThat(ToolCallLog.current().calls()).singleElement()
                .satisfies(call -> {
                    assertThat(call.tool()).isEqualTo("searchLocalKnowledge");
                    assertThat(call.resultCount()).isEqualTo(1);
                });
    }

    @Test
    void searchLocalKnowledgeReturnsClearMessageWhenNothingRelevant() {
        when(ragService.retrieve("query", 5))
                .thenReturn(List.of(new RagDocument("a.pdf", "weak", 0.05)));

        String result = tools.searchLocalKnowledge("query");

        assertThat(result).contains("未找到");
        assertThat(ToolCallLog.current().isEmpty()).isTrue();
    }

    @Test
    void searchWebFiltersFallbackResults() {
        when(searchService.search("query", 3))
                .thenReturn(List.of(
                        new SearchResult("real", "title1", "https://a.com", "real content"),
                        new SearchResult("fallback", "title2", "https://b.com", "fallback content")));

        String result = tools.searchWeb("query");

        assertThat(result).contains("title1", "https://a.com");
        assertThat(result).doesNotContain("title2");
        assertThat(ToolCallLog.current().calls()).singleElement()
                .satisfies(call -> assertThat(call.resultCount()).isEqualTo(1));
    }

    @Test
    void probeKnowledgeReturnsSourceHintsWithoutFullContent() {
        String longContent = "x".repeat(300);
        when(ragService.retrieve("topic", 5))
                .thenReturn(List.of(new RagDocument("report.pdf", longContent, 0.7)));

        String result = tools.probeKnowledge("topic");

        assertThat(result).contains("report.pdf");
        // 摘要截断到 120 字，而非完整的 300 字
        assertThat(result.length()).isLessThan(longContent.length() + 100);
    }

    @Test
    void probeKnowledgeSignalsWebDirectionWhenEmpty() {
        when(ragService.retrieve("topic", 5)).thenReturn(List.of());

        String result = tools.probeKnowledge("topic");

        assertThat(result).contains("联网");
    }
}
