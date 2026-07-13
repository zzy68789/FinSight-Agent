package com.zzy.finsight.rag;

import java.util.List;

/**
 * 定义向量文档的保存、搜索和清理能力。
 */
public interface VectorDocumentStore {
    /** 批量写入文档分片。 */
    void add(List<RagDocumentChunk> chunks);

    /** 按相似度查询最相关文档。 */
    List<RagDocument> query(String query, int topK);

    /** 清空全部向量文档。 */
    void clear();
}
