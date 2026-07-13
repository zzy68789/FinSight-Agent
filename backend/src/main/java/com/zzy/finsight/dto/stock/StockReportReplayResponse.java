package com.zzy.finsight.dto.stock;


import java.util.List;

/**
 * 表示股票报告的数据回放结果。
 * @param taskId 关联任务标识。
 * @param snapshotJson 序列化后的金融快照。
 * @param evidenceJson 序列化后的证据列表。
 * @param metricJson 序列化后的指标列表。
 */
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
