package com.zzy.finsight.rag;

import java.util.List;

/**
 * 定义向量文档的保存、搜索和清理能力。
 */
public interface VectorDocumentStore {
    /** 向指定知识空间批量写入文档分片。 */
    void add(RagKnowledgeSpace space, List<RagDocumentChunk> chunks);

    /** 在指定知识空间内按相似度查询最相关文档。 */
    List<RagDocument> query(RagKnowledgeSpace space, String query, int topK);

    /** 只清空指定知识空间的向量文档。 */
    void clear(RagKnowledgeSpace space);
}
