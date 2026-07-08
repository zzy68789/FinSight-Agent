package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.agent.state.AgentSubTaskResult;
import com.zzy.drai.agent.tool.ResearcherAgent;
import com.zzy.drai.config.AgentProperties;
import com.zzy.drai.rag.RagDocument;
import com.zzy.drai.rag.RagService;
import com.zzy.drai.search.SearchResult;
import com.zzy.drai.search.SearchService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearcherNodeTest {

    private final AgentProperties properties = new AgentProperties();

    private ResearcherNode nodeWithoutAgent(RagService ragService, SearchService searchService) {
        return new ResearcherNode(null, ragService, searchService, properties);
    }

    @Test
    void hybridModeUsesLocalEvidenceOnlyWhenItIsRelevantEnough() {
        RagService ragService = mock(RagService.class);
        SearchService searchService = mock(SearchService.class);
        ResearcherNode node = nodeWithoutAgent(ragService, searchService);
        when(ragService.retrieve("agent workflow", 5))
                .thenReturn(List.of(new RagDocument("agent.pdf", "local agent evidence", 0.72)));

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow",
                ResearchState.SEARCH_MODE, "hybrid"
        )));

        @SuppressWarnings("unchecked")
        List<String> searchResults = (List<String>) result.get(ResearchState.SEARCH_RESULTS);
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0)).contains("LOCAL", "agent.pdf", "local agent evidence");
        verify(searchService, never()).search("agent workflow", 3);
    }

    @Test
    void hybridModeAddsWebResultsWhenLocalEvidenceIsWeak() {
        RagService ragService = mock(RagService.class);
        SearchService searchService = mock(SearchService.class);
        ResearcherNode node = nodeWithoutAgent(ragService, searchService);
        when(ragService.retrieve("agent workflow", 5))
                .thenReturn(List.of(new RagDocument("agent.pdf", "weak local evidence", 0.10)));
        when(searchService.search("agent workflow", 3))
                .thenReturn(List.of(new SearchResult("web title", "https://example.com", "web evidence")));

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow",
                ResearchState.SEARCH_MODE, "hybrid"
        )));

        @SuppressWarnings("unchecked")
        List<String> searchResults = (List<String>) result.get(ResearchState.SEARCH_RESULTS);
        assertThat(searchResults).hasSize(2);
        assertThat(searchResults.get(0)).contains("LOCAL", "agent.pdf");
        assertThat(searchResults.get(1)).contains("WEB", "web title", "https://example.com", "web evidence");
    }

    @Test
    void webModeSkipsLocalRagAndOnlyUsesSearchResults() {
        RagService ragService = mock(RagService.class);
        SearchService searchService = mock(SearchService.class);
        ResearcherNode node = nodeWithoutAgent(ragService, searchService);
        when(searchService.search("agent workflow", 3))
                .thenReturn(List.of(new SearchResult("web title", "https://example.com", "web evidence")));

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow",
                ResearchState.SEARCH_MODE, "web"
        )));

        @SuppressWarnings("unchecked")
        List<String> searchResults = (List<String>) result.get(ResearchState.SEARCH_RESULTS);
        assertThat(searchResults).hasSize(1);
        assertThat(searchResults.get(0)).contains("WEB", "web title");
        verify(ragService, never()).retrieve("agent workflow", 5);
    }

    @Test
    void researchesEachPlannedSubTaskIndependently() {
        RagService ragService = mock(RagService.class);
        SearchService searchService = mock(SearchService.class);
        ResearcherNode node = nodeWithoutAgent(ragService, searchService);
        when(ragService.retrieve("background", 5))
                .thenReturn(List.of(new RagDocument("background.pdf", "background evidence", 0.76)));
        when(ragService.retrieve("risk", 5))
                .thenReturn(List.of(new RagDocument("risk.pdf", "risk evidence", 0.81)));

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow",
                ResearchState.SEARCH_MODE, "hybrid",
                ResearchState.PLAN, List.of("background", "risk")
        )));

        @SuppressWarnings("unchecked")
        List<AgentSubTaskResult> subTasks = (List<AgentSubTaskResult>) result.get(ResearchState.SUB_TASK_RESULTS);
        assertThat(subTasks).extracting(AgentSubTaskResult::query)
                .containsExactly("background", "risk");
        assertThat(subTasks).extracting(AgentSubTaskResult::status)
                .containsExactly("COMPLETED", "COMPLETED");
        assertThat(subTasks.get(0).evidence()).singleElement().asString().contains("background.pdf");
        assertThat(subTasks.get(1).evidence()).singleElement().asString().contains("risk.pdf");

        @SuppressWarnings("unchecked")
        List<String> searchResults = (List<String>) result.get(ResearchState.SEARCH_RESULTS);
        assertThat(searchResults).hasSize(2);
    }

    @Test
    void documentModeNeverInvokesAgentEvenWhenAvailable() {
        RagService ragService = mock(RagService.class);
        SearchService searchService = mock(SearchService.class);
        ResearcherAgent agent = mock(ResearcherAgent.class);
        ResearcherNode node = new ResearcherNode(agent, ragService, searchService, properties);
        when(ragService.retrieve("agent workflow", 5))
                .thenReturn(List.of(new RagDocument("doc.pdf", "content", 0.9)));

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow",
                ResearchState.SEARCH_MODE, "document"
        )));

        @SuppressWarnings("unchecked")
        List<String> searchResults = (List<String>) result.get(ResearchState.SEARCH_RESULTS);
        assertThat(searchResults).hasSize(1).allMatch(s -> s.contains("LOCAL"));
        verify(agent, never()).research(anyString());
    }
}
