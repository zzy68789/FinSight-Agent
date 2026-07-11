package com.zzy.finsight.search;

import java.util.List;

public interface SearchSource {
    String name();

    List<SearchResult> search(String query, int maxResults);
}
