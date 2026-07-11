package com.zzy.finsight.search;

public record SearchResult(String source, String title, String url, String content) {
    public SearchResult(String title, String url, String content) {
        this("unknown", title, url, content);
    }
}
