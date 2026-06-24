package com.zzy.drai.agent.state;

import org.bsc.langgraph4j.state.AgentState;

import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ResearchState extends AgentState {
    public static final String THREAD_ID = "threadId";
    public static final String TASK_ID = "taskId";
    public static final String QUERY = "query";
    public static final String SEARCH_MODE = "searchMode";
    public static final String PLAN = "plan";
    public static final String SEARCH_RESULTS = "searchResults";
    public static final String FINAL_REPORT = "finalReport";
    public static final String CRITIQUE = "critique";
    public static final String REVISION_NUMBER = "revisionNumber";
    public static final String REVIEW_STATUS = "reviewStatus";
    public static final String SHOULD_STOP = "shouldStop";

    public ResearchState(Map<String, Object> initData) {
        super(initData);
    }

    public String threadId() {
        return stringValue(THREAD_ID).orElse("");
    }

    public String query() {
        return stringValue(QUERY).orElse("");
    }

    public String searchMode() {
        return stringValue(SEARCH_MODE).orElse("hybrid");
    }

    public String critique() {
        return stringValue(CRITIQUE).orElse("");
    }

    public String reviewStatus() {
        return stringValue(REVIEW_STATUS).orElse("PASS");
    }

    public boolean shouldStop() {
        return value(SHOULD_STOP).map(Boolean.class::cast).orElse(false);
    }

    public int revisionNumber() {
        return value(REVISION_NUMBER)
                .map(value -> value instanceof Number number ? number.intValue() : Integer.parseInt(value.toString()))
                .orElse(0);
    }

    @SuppressWarnings("unchecked")
    public List<String> plan() {
        return value(PLAN).map(value -> (List<String>) value).orElseGet(List::of);
    }

    @SuppressWarnings("unchecked")
    public List<String> searchResults() {
        return value(SEARCH_RESULTS).map(value -> (List<String>) value).orElseGet(List::of);
    }

    public Optional<String> finalReport() {
        return stringValue(FINAL_REPORT);
    }

    private Optional<String> stringValue(String key) {
        return value(key).map(Object::toString).filter(value -> !value.isBlank());
    }
}
