package com.zzy.finsight.rag;

/**
 * 表示切分后的 RAG 文档片段。
 * @param chunkId 稳定分片标识。
 * @param source 数据来源。
 * @param chunkIndex 文档分片序号。
 * @param content 正文内容。
 */
public record RagDocumentChunk(String chunkId, String source, int chunkIndex, String content) {
    public RagDocumentChunk {
        source = source == null ? "" : source;
        content = content == null ? "" : content;
        chunkId = chunkId == null || chunkId.isBlank()
                ? RagChunkIds.generate(source, chunkIndex, content)
                : chunkId;
    }

    /** 保留原有分片构造方式并自动生成稳定标识。 */
    public RagDocumentChunk(String source, int chunkIndex, String content) {
        this(null, source, chunkIndex, content);
    }
}
