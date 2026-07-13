package com.zzy.finsight.search;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 通过 Tavily API 获取联网搜索结果。
 */
@Component
public class TavilySearchSource implements SearchSource {
    private final RestClient restClient;
    private final String apiKey;

    public TavilySearchSource(
            RestClient.Builder restClientBuilder,
            @Value("${finsight.tavily.api-key:}") String apiKey
    ) {
        this.restClient = restClientBuilder.baseUrl("https://api.tavily.com").build();
        this.apiKey = apiKey;
    }

    @Override
    public String name() {
        return "tavily";
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (apiKey == null || apiKey.isBlank()) {
            return List.of();
        }
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
                        name(),
                        item.path("title").asText("Web Result"),
                        item.path("url").asText(""),
                        item.path("content").asText("")
                ));
            }
        }
        return results;
    }
}
