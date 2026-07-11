package com.zzy.finsight.search;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EnhancedSearchServiceTest {

    @Test
    void retriesSearchSourceAndDeduplicatesResults() {
        FlakySearchSource source = new FlakySearchSource(List.of(
                new SearchResult("tavily", "Agent", "https://example.com/a", "agent evidence"),
                new SearchResult("tavily", "Agent duplicate", "https://example.com/a", "duplicate evidence"),
                new SearchResult("tavily", "", "https://example.com/blank", "   "),
                new SearchResult("tavily", "RAG", "https://example.com/rag", "rag evidence")
        ));
        EnhancedSearchService service = new EnhancedSearchService(List.of(source), 2);

        List<SearchResult> results = service.search("agent", 5);

        assertThat(source.attempts).isEqualTo(2);
        assertThat(results)
                .extracting(SearchResult::url)
                .containsExactly("https://example.com/a", "https://example.com/rag");
        assertThat(results)
                .extracting(SearchResult::source)
                .containsOnly("tavily");
    }

    @Test
    void returnsFallbackWhenAllSourcesFail() {
        SearchSource source = new SearchSource() {
            @Override
            public String name() {
                return "broken";
            }

            @Override
            public List<SearchResult> search(String query, int maxResults) {
                throw new IllegalStateException("downstream unavailable");
            }
        };
        EnhancedSearchService service = new EnhancedSearchService(List.of(source), 2);

        List<SearchResult> results = service.search("agent", 3);

        assertThat(results).containsExactly(new SearchResult(
                "fallback",
                "Local fallback search",
                "",
                "未配置可用搜索源或搜索源暂时不可用。检索主题：agent"
        ));
    }

    private static class FlakySearchSource implements SearchSource {
        private final List<SearchResult> response;
        private int attempts;

        private FlakySearchSource(List<SearchResult> response) {
            this.response = response;
        }

        @Override
        public String name() {
            return "tavily";
        }

        @Override
        public List<SearchResult> search(String query, int maxResults) {
            attempts++;
            if (attempts == 1) {
                throw new IllegalStateException("temporary");
            }
            return response;
        }
    }
}
