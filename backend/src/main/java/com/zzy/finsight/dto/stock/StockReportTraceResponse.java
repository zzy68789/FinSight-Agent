package com.zzy.finsight.dto.stock;


import com.fasterxml.jackson.databind.JsonNode;
import com.zzy.finsight.rag.RagRetrievalResult;

import java.util.List;
import java.util.Map;

public record StockReportTraceResponse(
        long taskId,
        String status,
        String stage,
        int attemptCount,
        Integer reportVersion,
        String dataSnapshotHash,
        String generationContextHash,
        boolean cacheHit,
        Long reusedFromReportId,
        int evidenceTotal,
        int evidenceEffective,
        int evidenceMissing,
        List<RagRetrievalResult> retrievalResults,
        List<StockReportStageTrace> stages,
        Map<String, JsonNode> reviewSummary
) {
    public StockReportTraceResponse {
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
        stages = stages == null ? List.of() : List.copyOf(stages);
        reviewSummary = reviewSummary == null ? Map.of() : Map.copyOf(reviewSummary);
    }
}
