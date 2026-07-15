package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;


import com.zzy.finsight.search.SearchResult;
import com.zzy.finsight.search.SearchService;
import com.zzy.finsight.search.TavilyExtractClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 通过公开搜索补充行情和新闻证据。
 */
@Component
public class PublicMarketDataProvider implements FinancialDataProvider {
    private static final int SEARCH_RESULT_LIMIT = 5;
    private static final int MAX_EXCERPT_LENGTH = 1200;
    private final SearchService searchService;
    private final TavilyExtractClient extractClient;
    private final int extractMaxUrls;

    public PublicMarketDataProvider(
            SearchService searchService,
            TavilyExtractClient extractClient,
            @Value("${finsight.tavily.extract-max-urls:3}") int extractMaxUrls
    ) {
        this.searchService = searchService;
        this.extractClient = extractClient;
        this.extractMaxUrls = Math.max(1, extractMaxUrls);
    }

    @Override
    public String name() {
        return "public-market";
    }

    @Override
    public List<FinancialEvidenceItem> collect(long ownerId, StockSubject subject, String reportPeriod, String searchMode) {
        if ("document".equalsIgnoreCase(searchMode)) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        try {
            String query = subject.isEtf()
                    ? subject.fullCode() + " " + subject.companyName() + " ETF 公告 跟踪指数 规模 持仓 新闻"
                    : subject.fullCode() + " " + subject.companyName() + " 公司公告 经营动态 分红 业绩 新闻";
            List<SearchResult> candidates = selectCandidates(searchService.search(query, SEARCH_RESULT_LIMIT));
            List<SearchResult> results = mergeExtractedResults(candidates, extractClient.extract(candidates, extractMaxUrls));
            for (SearchResult result : results) {
                if ("fallback".equalsIgnoreCase(result.source())) {
                    continue;
                }
                items.add(new FinancialEvidenceItem(
                        "PUBLIC_MARKET",
                        result.title(),
                        result.url(),
                        null,
                        "latest",
                        "NEWS_SUMMARY",
                        null,
                        null,
                        trim(result.content()),
                        "tavily-extract".equals(result.source())
                                ? new BigDecimal("0.80")
                                : new BigDecimal("0.60"),
                        LocalDateTime.now(),
                        ""
                ));
            }
        } catch (RuntimeException ignored) {
            items.add(dataMissing(subject, reportPeriod, "PUBLIC_MARKET_ERROR"));
        }
        if (items.isEmpty()) {
            items.add(dataMissing(subject, reportPeriod, "DATA_MISSING"));
        }
        return items;
    }

    /** 对搜索结果执行 URL 预筛选和来源优先级排序。 */
    private List<SearchResult> selectCandidates(List<SearchResult> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        return results.stream()
                .filter(this::candidateUsable)
                .sorted(Comparator.comparingInt(this::sourcePriority))
                .limit(extractMaxUrls)
                .toList();
    }

    /** 合并 Extract 正文与可用搜索摘要，确保单个网页提取失败不会中断公开数据采集。 */
    private List<SearchResult> mergeExtractedResults(
            List<SearchResult> candidates,
            List<SearchResult> extractedResults
    ) {
        Map<String, SearchResult> extractedByUrl = new LinkedHashMap<>();
        if (extractedResults != null) {
            for (SearchResult result : extractedResults) {
                extractedByUrl.put(result.url(), result);
            }
        }
        List<SearchResult> merged = new ArrayList<>();
        for (SearchResult candidate : candidates) {
            SearchResult extracted = extractedByUrl.get(candidate.url());
            if (extracted != null) {
                merged.add(extracted);
            } else if (usableSnippet(candidate.content())) {
                merged.add(candidate);
            }
        }
        return List.copyOf(merged);
    }

    private boolean candidateUsable(SearchResult result) {
        if (result == null || result.url() == null || result.url().isBlank()) {
            return false;
        }
        String url = result.url().toLowerCase(Locale.ROOT);
        return (url.startsWith("https://") || url.startsWith("http://"))
                && !url.contains("xueqiu.com/k?")
                && !url.contains("/login")
                && !url.contains("/search?");
    }

    private int sourcePriority(SearchResult result) {
        String url = result.url().toLowerCase(Locale.ROOT);
        if (url.contains("cninfo.com.cn") || url.contains("sse.com.cn") || url.contains("szse.cn")) {
            return 0;
        }
        if (url.contains("gov.cn") || url.contains("cs.com.cn") || url.contains("stcn.com")) {
            return 1;
        }
        if (url.contains("moomoo.com") || url.contains("quote") || url.contains("finance.yahoo.com")) {
            return 3;
        }
        return 2;
    }

    private boolean usableSnippet(String content) {
        return content != null && content.replaceAll("\\s+", "").length() >= 60;
    }

    private FinancialEvidenceItem dataMissing(StockSubject subject, String reportPeriod, String issueCode) {
        return new FinancialEvidenceItem(
                "PUBLIC_MARKET",
                "公开行情/新闻数据源",
                "",
                null,
                reportPeriod,
                "行情与新闻",
                null,
                null,
                subject.fullCode() + " 暂未取得可用公开行情、估值或新闻摘要；流程继续执行并在报告中标记数据缺失。",
                BigDecimal.ZERO,
                LocalDateTime.now(),
                issueCode
        );
    }

    private String trim(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= MAX_EXCERPT_LENGTH
                ? normalized
                : normalized.substring(0, MAX_EXCERPT_LENGTH);
    }
}
