package com.zzy.finsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;

/**
 * 表示兼容保留的聊天请求参数。
 */
public class ChatRequest {
    /** 用户研究问题。 */
    @NotBlank
    private String query;

    /** 检索模式。 */
    @JsonProperty("search_mode")
    private String searchMode = "hybrid";

    /** 会话标识。 */
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
