package com.zzy.finsight.dto.stock;


import com.fasterxml.jackson.databind.JsonNode;
import com.zzy.finsight.rag.RagRetrievalResult;

import java.util.List;
import java.util.Map;

/**
 * 表示股票报告完整的可信度轨迹。
 * @param taskId 关联任务标识。
 * @param status 当前状态。
 * @param stage 当前执行阶段。
 * @param attemptCount 累计执行次数。
 * @param reportVersion 报告版本号。
 * @param dataSnapshotHash 数据快照摘要。
 * @param generationContextHash 报告生成上下文摘要。
 * @param cacheHit 是否命中报告缓存。
 * @param reusedFromReportId 复用来源报告标识。
 * @param evidenceTotal 证据总数。
 * @param evidenceEffective 有效证据数量。
 * @param evidenceMissing 缺失证据数量。
 * @param retrievalResults 检索结果列表。
 * @param stages 任务阶段追踪列表。
 * @param reviewSummary 审查结果摘要。
 */
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
