package com.zzy.finsight.rag;

import java.util.List;

/**
 * 表示混合检索结果及其执行轨迹。
 * @param query 本次检索问题。
 * @param documents 命中的文档列表。
 * @param traceEntries 检索轨迹列表。
 * @param candidateCount 候选文档总数。
 * @param acceptedCount 通过阈值的文档数量。
 * @param filteredCount 被阈值过滤的文档数量。
 * @param keywordCandidateCount 关键词检索候选数量。
 * @param vectorCandidateCount 向量检索候选数量。
 * @param relevanceThreshold 相关性过滤阈值。
 * @param durationMs 执行耗时，单位毫秒。
 */
public record RagRetrievalResult(
        String query,
        List<RagDocument> documents,
        List<RagRetrievalTraceEntry> traceEntries,
        int candidateCount,
        int acceptedCount,
        int filteredCount,
        int keywordCandidateCount,
        int vectorCandidateCount,
        double relevanceThreshold,
        long durationMs
) {
    public RagRetrievalResult {
        documents = documents == null ? List.of() : List.copyOf(documents);
        traceEntries = traceEntries == null ? List.of() : List.copyOf(traceEntries);
    }

    public static RagRetrievalResult empty(String query, double relevanceThreshold, long durationMs) {
        return new RagRetrievalResult(query, List.of(), List.of(), 0, 0, 0, 0, 0, relevanceThreshold, durationMs);
    }
}
