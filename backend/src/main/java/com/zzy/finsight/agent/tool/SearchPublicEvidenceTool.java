package com.zzy.finsight.agent.tool;

import com.zzy.finsight.agent.memory.EvidenceMemory;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.search.SearchResult;
import com.zzy.finsight.search.SearchService;
import com.zzy.finsight.search.TavilyExtractClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 根据自然语言研究问题检索公开网页证据，并将有效结果增量写入证据账本。
 */
@Component
public class SearchPublicEvidenceTool implements ResearchTool {
    private static final int SEARCH_LIMIT = 5;
    private static final int MAX_EXCERPT_LENGTH = 1200;
    private final SearchService searchService;
    private final TavilyExtractClient extractClient;
    private final EvidenceMemory evidenceMemory;
    private final int extractMaxUrls;

    public SearchPublicEvidenceTool(
            SearchService searchService,
            TavilyExtractClient extractClient,
            EvidenceMemory evidenceMemory,
            @Value("${finsight.tavily.extract-max-urls:3}") int extractMaxUrls
    ) {
        this.searchService = searchService;
        this.extractClient = extractClient;
        this.evidenceMemory = evidenceMemory;
        this.extractMaxUrls = Math.max(1, extractMaxUrls);
    }

    @Override
    public String name() {
        return "search_public_evidence";
    }

    @Override
    public String description() {
        return "围绕当前自然语言研究问题检索公告、新闻和公开网页证据；参数可包含 query 以收窄问题。";
    }

    @Override
    public boolean allowParallel() {
        return true;
    }

    @Override
    public Set<String> allowedArguments() {
        return Set.of("query");
    }

    @Override
    public boolean producesEvidence() {
        return true;
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        if (context.state().getSubject() == null) {
            return ToolResult.failure("尚未解析证券主体", "SUBJECT_REQUIRED", false);
        }
        String focusedQuestion = stringArgument(arguments, "query");
        if (focusedQuestion.isBlank()) {
            focusedQuestion = context.request().getResearchQuestion();
        }
        String query = String.join(" ",
                context.state().getSubject().fullCode(),
                context.state().getSubject().companyName(),
                focusedQuestion,
                "公告 新闻 财报",
                context.request().getAsOfDate().toString()
        );
        long startedAt = System.nanoTime();
        List<SearchResult> candidates = usable(searchService.search(query, SEARCH_LIMIT));
        List<SearchResult> extracted = extractClient.extract(candidates, extractMaxUrls);
        List<SearchResult> results = preferExtracted(candidates, extracted);
        List<FinancialEvidenceItem> evidence = results.stream()
                .map(result -> toEvidence(result, context.request().getAsOfDate().toString()))
                .toList();
        if (evidence.isEmpty()) {
            return ToolResult.failure("未检索到可用公开证据", "DATA_MISSING", true);
        }
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        List<FinancialEvidenceItem> added = evidenceMemory.merge(
                context.state(), name(), FinancialDataCollection.evidenceOnly(evidence), durationMs, "SUCCESS", ""
        );
        return new ToolResult(
                "SUCCESS",
                "围绕研究问题新增 %d 条公开证据".formatted(added.size()),
                Map.of("query", query, "evidence", added, "evidenceCount", added.size()),
                added,
                "",
                false
        );
    }

    private List<SearchResult> usable(List<SearchResult> results) {
        if (results == null) {
            return List.of();
        }
        return results.stream()
                .filter(result -> result != null && httpUrl(result.url()))
                .filter(result -> result.content() != null && result.content().replaceAll("\\s+", "").length() >= 40)
                .limit(SEARCH_LIMIT)
                .toList();
    }

    private List<SearchResult> preferExtracted(List<SearchResult> candidates, List<SearchResult> extracted) {
        Map<String, SearchResult> byUrl = new LinkedHashMap<>();
        if (extracted != null) {
            extracted.forEach(result -> byUrl.put(result.url(), result));
        }
        List<SearchResult> merged = new ArrayList<>();
        for (SearchResult candidate : candidates) {
            merged.add(byUrl.getOrDefault(candidate.url(), candidate));
        }
        return List.copyOf(merged);
    }

    private FinancialEvidenceItem toEvidence(SearchResult result, String reportPeriod) {
        return new FinancialEvidenceItem(
                "PUBLIC_RESEARCH",
                result.title(),
                result.url(),
                null,
                reportPeriod,
                "RESEARCH_QUESTION_EVIDENCE",
                null,
                null,
                trim(result.content()),
                "tavily-extract".equals(result.source()) ? new BigDecimal("0.80") : new BigDecimal("0.60"),
                LocalDateTime.now(),
                ""
        );
    }

    private String stringArgument(Map<String, Object> arguments, String name) {
        if (arguments == null || arguments.get(name) == null) {
            return "";
        }
        return String.valueOf(arguments.get(name)).trim();
    }

    private boolean httpUrl(String url) {
        String normalized = url == null ? "" : url.trim().toLowerCase(java.util.Locale.ROOT);
        return normalized.startsWith("https://") || normalized.startsWith("http://");
    }

    private String trim(String content) {
        String normalized = content == null ? "" : content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_EXCERPT_LENGTH
                ? normalized : normalized.substring(0, MAX_EXCERPT_LENGTH);
    }
}
