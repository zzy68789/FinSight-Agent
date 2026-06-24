package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.rag.RagDocument;
import com.zzy.drai.rag.RagService;
import com.zzy.drai.search.SearchResult;
import com.zzy.drai.search.SearchService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class ResearcherNode {
    private final RagService ragService;
    private final SearchService searchService;

    public ResearcherNode(RagService ragService, SearchService searchService) {
        this.ragService = ragService;
        this.searchService = searchService;
    }

    public Map<String, Object> apply(ResearchState state) {
        List<String> results = new ArrayList<>();
        List<RagDocument> ragDocs = ragService.retrieve(state.query(), 5);
        boolean hasRelevantDocs = !ragDocs.isEmpty();
        ragDocs.forEach(doc -> results.add("### 本地文档片段 (" + doc.source() + ")\n" + doc.content()));

        if ("document".equalsIgnoreCase(state.searchMode()) && !hasRelevantDocs) {
            return Map.of(
                    ResearchState.SEARCH_RESULTS, List.of("【严重警告】：用户选择了 Document Only 模式，但上传文档与问题不匹配。"),
                    ResearchState.SHOULD_STOP, true
            );
        }

        for (String query : state.plan().isEmpty() ? List.of(state.query()) : state.plan()) {
            List<SearchResult> webResults = searchService.search(query, 3);
            webResults.forEach(result -> results.add("### 网络搜索结果 (" + query + ")\n" + result.content()));
        }

        return Map.of(
                ResearchState.SEARCH_RESULTS, results,
                ResearchState.SHOULD_STOP, false
        );
    }
}
