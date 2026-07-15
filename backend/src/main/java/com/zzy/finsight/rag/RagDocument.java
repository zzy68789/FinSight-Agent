package com.zzy.finsight.rag;

/**
 * 表示一条带相关性分数的 RAG 文档。
 * @param chunkId 稳定分片标识。
 * @param source 数据来源。
 * @param content 文档正文。
 * @param score 评分。
 */
public record RagDocument(String chunkId, String source, String content, double score) {
    public RagDocument {
        source = source == null ? "" : source;
        content = content == null ? "" : content;
        chunkId = chunkId == null || chunkId.isBlank()
                ? RagChunkIds.generate(source, -1, content)
                : chunkId;
    }

    /** 保留原有检索结果构造方式并根据内容生成兼容标识。 */
    public RagDocument(String source, String content, double score) {
        this(null, source, content, score);
    }
}
