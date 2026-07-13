package com.zzy.finsight.rag;

/**
 * 表示一条带相关性分数的 RAG 文档。
 * @param source 数据来源。
 * @param content 文档正文。
 * @param score 评分。
 */
public record RagDocument(String source, String content, double score) {
}
