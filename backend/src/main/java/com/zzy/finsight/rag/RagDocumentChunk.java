package com.zzy.finsight.rag;

/**
 * 表示切分后的 RAG 文档片段。
 * @param source 数据来源。
 * @param chunkIndex 文档分片序号。
 * @param content 正文内容。
 */
public record RagDocumentChunk(String source, int chunkIndex, String content) {
}
