package com.zzy.finsight.search;

import java.util.List;

/**
 * 定义单个联网搜索数据源。
 */
public interface SearchSource {
    /** 返回搜索数据源名称。 */
    String name();

    /** 从当前数据源执行搜索。 */
    List<SearchResult> search(String query, int maxResults);
}
