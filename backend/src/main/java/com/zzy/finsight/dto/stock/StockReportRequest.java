package com.zzy.finsight.dto.stock;


import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class StockReportRequest {
    @NotBlank
    private String ticker;

    @JsonProperty("thread_id")
    private String threadId;

    @JsonProperty("report_period")
    private String reportPeriod = "latest";

    @JsonProperty("search_mode")
    private String searchMode = "hybrid";

    public String getTicker() {
        return ticker;
    }

    public void setTicker(String ticker) {
        this.ticker = ticker;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }

    public String getReportPeriod() {
        return reportPeriod == null || reportPeriod.isBlank() ? "latest" : reportPeriod;
    }

    public void setReportPeriod(String reportPeriod) {
        this.reportPeriod = reportPeriod;
    }

    public String getSearchMode() {
        return searchMode == null || searchMode.isBlank() ? "hybrid" : searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }
}
