package com.zzy.finsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

public class ChatRequest {
    @NotBlank
    private String query;

    @JsonProperty("search_mode")
    private String searchMode = "hybrid";

    @NotBlank
    @JsonProperty("thread_id")
    private String threadId;

    public String getQuery() {
        return query;
    }

    public void setQuery(String query) {
        this.query = query;
    }

    public String getSearchMode() {
        return searchMode == null || searchMode.isBlank() ? "hybrid" : searchMode;
    }

    public void setSearchMode(String searchMode) {
        this.searchMode = searchMode;
    }

    public String getThreadId() {
        return threadId;
    }

    public void setThreadId(String threadId) {
        this.threadId = threadId;
    }
}
