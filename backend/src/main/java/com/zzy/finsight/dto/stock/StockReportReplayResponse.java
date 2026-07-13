package com.zzy.finsight.dto.stock;


import java.util.List;

public record StockReportReplayResponse(
        long taskId,
        String snapshotJson,
        List<String> evidenceJson,
        List<String> metricJson
) {
    public StockReportReplayResponse {
        evidenceJson = evidenceJson == null ? List.of() : List.copyOf(evidenceJson);
        metricJson = metricJson == null ? List.of() : List.copyOf(metricJson);
    }
}
