package com.zzy.finsight.search;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 聚合搜索源并执行重试、去重和质量过滤。
 */
@Service
public class EnhancedSearchService implements SearchService {
    private final List<SearchSource> searchSources;
    private final int maxAttempts;

    public EnhancedSearchService(
            List<SearchSource> searchSources,
            @Value("${finsight.search.max-attempts:2}") int maxAttempts
    ) {
        this.searchSources = searchSources == null ? List.of() : searchSources;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    public List<SearchResult> search(String query, int maxResults) {
        if (query == null || query.isBlank() || maxResults <= 0) {
            return List.of();
        }
        List<SearchResult> collected = new ArrayList<>();
        for (SearchSource source : searchSources) {
            collected.addAll(searchSourceWithRetry(source, query, maxResults));
            if (collected.size() >= maxResults) {
                break;
            }
        }
        List<SearchResult> normalized = deduplicateAndFilter(collected, maxResults);
        return normalized.isEmpty() ? fallback(query) : normalized;
    }

    private List<SearchResult> searchSourceWithRetry(SearchSource source, String query, int maxResults) {
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                List<SearchResult> results = source.search(query, maxResults);
                if (results != null && !results.isEmpty()) {
                    return results;
                }
            } catch (RuntimeException e) {
                if (attempt == maxAttempts) {
                    return List.of();
                }
            }
        }
        return List.of();
    }

    private List<SearchResult> deduplicateAndFilter(List<SearchResult> results, int maxResults) {
        Map<String, SearchResult> uniqueResults = new LinkedHashMap<>();
        for (SearchResult result : results) {
            if (!isUseful(result)) {
                continue;
            }
            uniqueResults.putIfAbsent(deduplicateKey(result), result);
            if (uniqueResults.size() >= maxResults) {
                break;
            }
        }
        return new ArrayList<>(uniqueResults.values());
    }

    private boolean isUseful(SearchResult result) {
        return result != null
                && notBlank(result.title())
                && notBlank(result.content());
    }

    private boolean notBlank(String value) {
        return value != null && !value.isBlank();
    }

    private String deduplicateKey(SearchResult result) {
        if (notBlank(result.url())) {
            return "url:" + result.url().trim().toLowerCase(Locale.ROOT);
        }
        return "content:" + result.title().trim().toLowerCase(Locale.ROOT)
                + "::"
                + result.content().trim().toLowerCase(Locale.ROOT);
    }

    private List<SearchResult> fallback(String query) {
        return List.of(new SearchResult(
                "fallback",
                "Local fallback search",
                "",
                "未配置可用搜索源或搜索源暂时不可用。检索主题：" + query
        ));
    }
}
