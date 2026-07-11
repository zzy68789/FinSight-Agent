package com.zzy.finsight.search;

import java.util.List;

public interface SearchService {
    List<SearchResult> search(String query, int maxResults);
}
