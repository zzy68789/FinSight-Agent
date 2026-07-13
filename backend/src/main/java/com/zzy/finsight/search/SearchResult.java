package com.zzy.finsight.search;

/**
 * 表示一条联网搜索结果。
 * @param source 数据来源。
 * @param title 搜索结果标题。
 * @param url 数据来源地址。
 * @param content 搜索结果正文。
 */
public record SearchResult(String source, String title, String url, String content) {
    public SearchResult(String title, String url, String content) {
        this("unknown", title, url, content);
    }
}
