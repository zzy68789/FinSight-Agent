package com.zzy.finsight.search;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 通过 Tavily Extract 批量提取搜索结果对应的网页正文。
 */
@Component
public class TavilyExtractClient {
    private final RestClient restClient;
    private final String apiKey;
    private final int timeoutSeconds;
    private final int maxAttempts;

    public TavilyExtractClient(
            RestClient.Builder restClientBuilder,
            @Value("${finsight.tavily.api-key:}") String apiKey,
            @Value("${finsight.tavily.extract-timeout-seconds:10}") int timeoutSeconds,
            @Value("${finsight.tavily.extract-max-attempts:2}") int maxAttempts
    ) {
        this.restClient = restClientBuilder.baseUrl("https://api.tavily.com").build();
        this.apiKey = apiKey;
        this.timeoutSeconds = Math.max(1, Math.min(60, timeoutSeconds));
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** 批量提取限定数量的有效 URL，失败时返回空结果并由上层使用搜索摘要降级。 */
    public List<SearchResult> extract(List<SearchResult> candidates, int maxUrls) {
        if (apiKey == null || apiKey.isBlank() || candidates == null || candidates.isEmpty() || maxUrls <= 0) {
            return List.of();
        }
        Map<String, SearchResult> originals = new LinkedHashMap<>();
        for (SearchResult candidate : candidates) {
            if (candidate == null || !isHttpUrl(candidate.url())) {
                continue;
            }
            originals.putIfAbsent(candidate.url().trim(), candidate);
            if (originals.size() >= maxUrls) {
                break;
            }
        }
        if (originals.isEmpty()) {
            return List.of();
        }
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                return requestExtract(originals);
            } catch (RuntimeException exception) {
                if (attempt == maxAttempts || !retryable(exception)) {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    /** 调用 Extract 批量接口并把正文映射回原始搜索标题。 */
    private List<SearchResult> requestExtract(Map<String, SearchResult> originals) {
        JsonNode response = restClient.post()
                .uri("/extract")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(Map.of(
                        "urls", new ArrayList<>(originals.keySet()),
                        "extract_depth", "basic",
                        "format", "markdown",
                        "include_images", false,
                        "timeout", timeoutSeconds
                ))
                .retrieve()
                .body(JsonNode.class);
        if (response == null || !response.has("results")) {
            return List.of();
        }
        List<SearchResult> results = new ArrayList<>();
        for (JsonNode item : response.get("results")) {
            String url = item.path("url").asText("").trim();
            String rawContent = item.path("raw_content").asText("").trim();
            SearchResult original = originals.get(url);
            if (original == null) {
                String normalizedUrl = normalizeUrl(url);
                original = originals.entrySet().stream()
                        .filter(entry -> normalizeUrl(entry.getKey()).equals(normalizedUrl))
                        .map(Map.Entry::getValue)
                        .findFirst()
                        .orElse(null);
            }
            if (original == null || rawContent.isBlank()) {
                continue;
            }
            results.add(new SearchResult("tavily-extract", original.title(), url, rawContent));
        }
        return List.copyOf(results);
    }

    private boolean isHttpUrl(String url) {
        if (url == null) {
            return false;
        }
        String normalized = url.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private String normalizeUrl(String url) {
        String normalized = url == null ? "" : url.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.endsWith("/") ? normalized.substring(0, normalized.length() - 1) : normalized;
    }

    /** 仅对网络访问失败和服务端错误重试，客户端配置错误直接降级。 */
    private boolean retryable(RuntimeException exception) {
        if (exception instanceof ResourceAccessException) {
            return true;
        }
        return exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode().is5xxServerError();
    }
}
