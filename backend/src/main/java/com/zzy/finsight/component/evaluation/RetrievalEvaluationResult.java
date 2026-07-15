package com.zzy.finsight.component.evaluation;

import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;

import java.util.List;

/**
 * 表示一个检索问题的离线评测结果。
 * @param query 检索问题。
 * @param metricScores Recall、Precision、MRR 和 NDCG 指标。
 * @param status 硬门禁状态。
 * @param failedReasons 失败原因。
 */
public record RetrievalEvaluationResult(
        String query,
        List<FinancialEvaluationMetricScore> metricScores,
        String status,
        List<String> failedReasons
) {
    public RetrievalEvaluationResult {
        query = query == null ? "" : query;
        metricScores = metricScores == null ? List.of() : List.copyOf(metricScores);
        failedReasons = failedReasons == null ? List.of() : List.copyOf(failedReasons);
    }
}
