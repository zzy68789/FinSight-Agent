package com.zzy.drai.financial;

import com.zzy.drai.search.SearchResult;
import com.zzy.drai.search.SearchService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Component
public class PublicMarketDataProvider implements FinancialDataProvider {
    private final SearchService searchService;

    public PublicMarketDataProvider(SearchService searchService) {
        this.searchService = searchService;
    }

    @Override
    public String name() {
        return "public-market";
    }

    @Override
    public List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode) {
        if ("document".equalsIgnoreCase(searchMode)) {
            return List.of();
        }
        List<FinancialEvidenceItem> items = new ArrayList<>();
        try {
            List<SearchResult> results = searchService.search(subject.fullCode() + " A股 财报 行情 新闻 估值", 5);
            for (SearchResult result : results) {
                if ("fallback".equalsIgnoreCase(result.source())) {
                    continue;
                }
                items.add(new FinancialEvidenceItem(
                        "PUBLIC_MARKET",
                        result.title(),
                        result.url(),
                        null,
                        reportPeriod,
                        "NEWS_SUMMARY",
                        null,
                        null,
                        trim(result.content()),
                        new BigDecimal("0.65"),
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
        return normalized.length() <= 320 ? normalized : normalized.substring(0, 320);
    }
}
