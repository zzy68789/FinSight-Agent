package com.zzy.finsight.dto.agent;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 表示受约束投研 Agent 的运行请求。
 */
public class ResearchRunRequest {
    /** 股票或 ETF 代码。 */
    @NotBlank
    @Pattern(regexp = "\\d{6}(\\.(SH|SZ))?", flags = Pattern.Flag.CASE_INSENSITIVE)
    private String ticker;

    /** 本次运行需要回答的自然语言研究问题。 */
    @NotBlank
    @JsonProperty("research_question")
    private String researchQuestion;

    /** 本次运行的研究意图，只约束研究重点而不固定工具链。 */
    @JsonProperty("research_intent")
    private ResearchIntent researchIntent = ResearchIntent.COMPREHENSIVE;

    /** 会话标识。 */
    @JsonProperty("thread_id")
    private String threadId;

    /** 研究使用的数据截止日期。 */
    @JsonProperty("as_of_date")
    private LocalDate asOfDate;

    /** 行情和事件观察区间。 */
    @JsonProperty("time_horizon")
    private String timeHorizon = "2Y";

    /** 研究深度，只允许 quick、standard 或 deep。 */
    @JsonProperty("research_depth")
    private String researchDepth = "standard";

    /** 证据检索模式。 */
    @JsonProperty("search_mode")
    private String searchMode = "hybrid";

    /** 最多三个已确认并规范化的同类可比证券代码。 */
    @Size(max = 3)
    @JsonProperty("comparison_tickers")
    private List<@Pattern(
            regexp = "\\d{6}(\\.(SH|SZ))?",
            flags = Pattern.Flag.CASE_INSENSITIVE
    ) String> comparisonTickers = List.of();

    /** 可选的客户端预算上限，只能收紧服务端预算。 */
    @Valid
    private ResearchBudgetRequest budget;

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getResearchQuestion() {
        return researchQuestion;
    }

    public void setResearchQuestion(String researchQuestion) {
        this.researchQuestion = researchQuestion;
    }

    public ResearchIntent getResearchIntent() {
        return researchIntent == null ? ResearchIntent.COMPREHENSIVE : researchIntent;
    }

    public void setResearchIntent(ResearchIntent researchIntent) {
        this.researchIntent = researchIntent;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public LocalDate getAsOfDate() {
        return asOfDate == null ? LocalDate.now() : asOfDate;
    }

    public void setAsOfDate(LocalDate asOfDate) {
        this.asOfDate = asOfDate;
    }

    public String getTimeHorizon() {
        return normalize(timeHorizon, "2Y");
    }

    public void setTimeHorizon(String timeHorizon) {
        this.timeHorizon = timeHorizon;
    }

    public String getResearchDepth() {
        String normalized = normalize(researchDepth, "standard").toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "quick", "deep" -> normalized;
            default -> "standard";
        };
    }

    public void setResearchDepth(String researchDepth) {
        this.researchDepth = researchDepth;
    }

    public String getSearchMode() {
        String normalized = normalize(searchMode, "hybrid").toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "document", "web" -> normalized;
            default -> "hybrid";
        };
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    public List<String> getComparisonTickers() {
        return comparisonTickers == null ? List.of() : List.copyOf(comparisonTickers);
    }

    public void setComparisonTickers(List<String> comparisonTickers) {
        this.comparisonTickers = comparisonTickers == null ? List.of() : List.copyOf(comparisonTickers);
    }

    public ResearchBudgetRequest getBudget() {
        return budget;
    }

    public void setBudget(ResearchBudgetRequest budget) {
        this.budget = budget;
    }

    private String normalize(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value.trim();
    }
}
