package com.zzy.finsight.rag;

import java.util.List;

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
