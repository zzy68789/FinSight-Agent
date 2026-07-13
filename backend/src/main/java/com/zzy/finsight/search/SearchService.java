package com.zzy.finsight.search;

import java.util.List;

/**
 * 定义联网搜索服务。
 */
public interface SearchService {
    /** 聚合多个数据源并返回限定数量的搜索结果。 */
    List<SearchResult> search(String query, int maxResults);
}
