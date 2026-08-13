package com.zzy.finsight.agent.tool;

import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchIntent;

import java.time.LocalDate;
import java.util.List;

/**
 * 表示工具可读取的不可变研究请求投影。
 * @param ticker 证券代码。
 * @param researchQuestion 自然语言研究问题。
 * @param researchIntent 研究意图。
 * @param asOfDate 数据截止日期。
 * @param timeHorizon 观察区间。
 * @param researchDepth 研究深度。
 * @param searchMode 证据检索模式。
 * @param comparisonTickers 已规范化的可比证券代码。
 */
public record ToolResearchRequest(
        String ticker,
        String researchQuestion,
        ResearchIntent researchIntent,
        LocalDate asOfDate,
        String timeHorizon,
        String researchDepth,
        String searchMode,
        List<String> comparisonTickers
) {
    public ToolResearchRequest {
        ticker = ticker == null ? "" : ticker;
        researchQuestion = researchQuestion == null ? "" : researchQuestion;
        researchIntent = researchIntent == null ? ResearchIntent.COMPREHENSIVE : researchIntent;
        asOfDate = asOfDate == null ? LocalDate.now() : asOfDate;
        timeHorizon = timeHorizon == null ? "" : timeHorizon;
        researchDepth = researchDepth == null ? "standard" : researchDepth;
        searchMode = searchMode == null ? "hybrid" : searchMode;
        comparisonTickers = comparisonTickers == null ? List.of() : List.copyOf(comparisonTickers);
    }

    /** 从可变 API DTO 提取工具所需字段，之后不再暴露原请求对象。 */
    public static ToolResearchRequest from(ResearchRunRequest request) {
        if (request == null) {
            return new ToolResearchRequest(
                    "", "", ResearchIntent.COMPREHENSIVE, LocalDate.now(), "", "standard", "hybrid", List.of()
            );
        }
        return new ToolResearchRequest(
                request.getTicker(),
                request.getResearchQuestion(),
                request.getResearchIntent(),
                request.getAsOfDate(),
                request.getTimeHorizon(),
                request.getResearchDepth(),
                request.getSearchMode(),
                request.getComparisonTickers()
        );
    }
}
