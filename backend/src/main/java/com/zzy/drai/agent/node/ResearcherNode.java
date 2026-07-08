package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.AgentSubTaskResult;
import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.agent.tool.ResearcherAgent;
import com.zzy.drai.agent.tool.ToolCallLog;
import com.zzy.drai.config.AgentProperties;
import com.zzy.drai.rag.RagDocument;
import com.zzy.drai.rag.RagService;
import com.zzy.drai.search.SearchResult;
import com.zzy.drai.search.SearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 为每个规划出的子任务收集证据。
 *
 * <p>当 {@link ResearcherAgent}（LLM）可用且模式允许联网时，agent 以 ReAct 循环自主决定
 * 调用哪些检索工具、何时停止。{@code document} 模式和无 LLM 的情况走确定性的"本地优先"检索路径，
 * 该路径同时保证 document 模式永远不会触达联网搜索。
 */
@Component
public class ResearcherNode {

    @Nullable
    private final ResearcherAgent researcherAgent;
    private final RagService ragService;
    private final SearchService searchService;
    private final AgentProperties properties;

    @Autowired
    public ResearcherNode(
            @Nullable ResearcherAgent researcherAgent,
            RagService ragService,
            SearchService searchService,
            AgentProperties properties
    ) {
        this.researcherAgent = researcherAgent;
        this.ragService = ragService;
        this.searchService = searchService;
        this.properties = properties;
    }

    public Map<String, Object> apply(ResearchState state) {
        String searchMode = state.searchMode();
        Set<String> deduplicated = new LinkedHashSet<>();
        List<AgentSubTaskResult> subTaskResults = new ArrayList<>();
        boolean useAgent = researcherAgent != null && !"document".equalsIgnoreCase(searchMode);

        for (String query : researchQueries(state)) {
            long startedAt = System.currentTimeMillis();
            List<String> evidence = useAgent
                    ? collectViaAgent(query)
                    : collectEvidence(query, searchMode);
            long durationMs = Math.max(0, System.currentTimeMillis() - startedAt);
            deduplicated.addAll(evidence);
            subTaskResults.add(evidence.isEmpty()
                    ? AgentSubTaskResult.noEvidence(query, durationMs)
                    : AgentSubTaskResult.completed(query, evidence, durationMs));
        }

        List<String> results = new ArrayList<>(deduplicated);
        if ("document".equalsIgnoreCase(searchMode) && results.isEmpty()) {
            return Map.of(
                    ResearchState.SEARCH_RESULTS, List.of("[WARNING] Document mode was selected, but no relevant local evidence was found."),
                    ResearchState.SUB_TASK_RESULTS, subTaskResults,
                    ResearchState.SHOULD_STOP, true
            );
        }

        return Map.of(
                ResearchState.SEARCH_RESULTS, results,
                ResearchState.SUB_TASK_RESULTS, subTaskResults,
                ResearchState.SHOULD_STOP, false
        );
    }

    private List<String> researchQueries(ResearchState state) {
        return state.plan().isEmpty() ? List.of(state.query()) : state.plan();
    }

    /**
     * 对单个子任务运行 ReAct agent，并从它选择调用的工具中重建原始证据列表。
     * 若 agent 报错或什么都没收集到，则回退到确定性路径，使 LLM/工具失败不会静默丢掉子任务。
     */
    private List<String> collectViaAgent(String query) {
        ToolCallLog.begin();
        try {
            researcherAgent.research(query);
            ToolCallLog log = ToolCallLog.current();
            if (log != null && !log.isEmpty()) {
                return new ArrayList<>(new LinkedHashSet<>(log.evidence()));
            }
            return collectEvidence(query, "hybrid");
        } catch (RuntimeException e) {
            return collectEvidence(query, "hybrid");
        } finally {
            ToolCallLog.clear();
        }
    }

    private List<String> collectEvidence(String query, String searchMode) {
        List<String> results = new ArrayList<>();
        List<RagDocument> ragDocs = "web".equalsIgnoreCase(searchMode)
                ? List.of()
                : ragService.retrieve(query, properties.getLocalTopK());
        boolean hasRelevantDocs = ragDocs.stream().anyMatch(doc -> doc.score() >= properties.getLocalRelevanceThreshold());
        for (RagDocument doc : ragDocs) {
            results.add(localEvidence(doc));
        }

        if (shouldSearchWeb(searchMode, hasRelevantDocs)) {
            Set<String> deduplicated = new LinkedHashSet<>(results);
            List<SearchResult> webResults = searchService.search(query, properties.getWebTopK());
            webResults.forEach(result -> deduplicated.add(webEvidence(query, result)));
            results = new ArrayList<>(deduplicated);
        }

        return results;
    }

    private boolean shouldSearchWeb(String searchMode, boolean hasRelevantDocs) {
        if ("web".equalsIgnoreCase(searchMode)) {
            return true;
        }
        return "hybrid".equalsIgnoreCase(searchMode) && !hasRelevantDocs;
    }

    private String localEvidence(RagDocument doc) {
        return "### LOCAL evidence (" + doc.source() + ", score=" + doc.score() + ")\n" + doc.content();
    }

    private String webEvidence(String query, SearchResult result) {
        return "### WEB evidence (" + query + ")\n"
                + "- source: " + result.source() + "\n"
                + "- title: " + result.title() + "\n"
                + "- url: " + result.url() + "\n"
                + result.content();
    }
}
