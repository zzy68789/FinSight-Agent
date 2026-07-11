package com.zzy.finsight.rag;

import java.util.List;

public interface VectorDocumentStore {
    void add(List<RagDocumentChunk> chunks);

    List<RagDocument> query(String query, int topK);

    void clear();
}
