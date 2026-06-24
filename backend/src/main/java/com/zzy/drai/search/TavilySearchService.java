package com.zzy.drai.search;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
public class TavilySearchService implements SearchService {
    private final RestClient restClient;
    private final String apiKey;

    public TavilySearchService(
            RestClient.Builder restClientBuilder,
            @Value("${drai.tavily.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl("https://api.tavily.com").build();
        this.apiKey = apiKey;
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            return fallback(query);
        }
        try {
            JsonNode response = restClient.post()
                    .uri("/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "api_key", apiKey,
                            "query", query,
                            "search_depth", "basic",
                            "max_results", maxResults
                    ))
                    .retrieve()
                    .body(JsonNode.class);
            List<SearchResult> results = new ArrayList<>();
            if (response != null && response.has("results")) {
                for (JsonNode item : response.get("results")) {
                    results.add(new SearchResult(
                            item.path("title").asText("Web Result"),
                            item.path("url").asText(""),
                            item.path("content").asText("")
                    ));
                }
            }
            return results.isEmpty() ? fallback(query) : results;
        } catch (Exception e) {
            return fallback(query);
        }
    }

    private List<SearchResult> fallback(String query) {
        return List.of(new SearchResult(
                "Local fallback search",
                "",
                "未配置 Tavily API Key，当前使用本地降级检索结果。检索主题：" + query
        ));
    }
}
