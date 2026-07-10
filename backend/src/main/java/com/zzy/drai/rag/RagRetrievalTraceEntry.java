package com.zzy.drai.rag;

import java.util.List;

public record RagRetrievalTraceEntry(
        String source,
        String content,
        double keywordScore,
        double vectorScore,
        double fusionScore,
        int rank,
        List<String> channels
) {
    public RagRetrievalTraceEntry {
        channels = channels == null ? List.of() : List.copyOf(channels);
    }
}
