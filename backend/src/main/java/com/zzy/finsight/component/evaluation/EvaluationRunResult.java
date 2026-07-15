package com.zzy.finsight.component.evaluation;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 表示一次可落盘的分层评测运行结果。
 * @param runId 运行标识。
 * @param mode 运行模式。
 * @param datasetVersion 数据集版本。
 * @param gitCommit Git 提交标识。
 * @param policyVersion 线上规则版本。
 * @param promptVersion Judge 提示词版本。
 * @param startedAt 开始时间。
 * @param finishedAt 结束时间。
 * @param durationMs 总耗时。
 * @param status 最终状态。
 * @param caseResults 冻结报告样例结果。
 * @param retrievalResults 检索样例结果。
 * @param judgeResults Judge 辅助评分结果。
 * @param aggregateMetrics 聚合指标。
 * @param baselineComparison 基线比较结果。
 * @param modelName 实际模型名称。
 * @param inputTokens 输入 Token 数。
 * @param outputTokens 输出 Token 数。
 * @param totalTokens 总 Token 数。
 */
public record EvaluationRunResult(
        String runId,
        EvaluationMode mode,
        String datasetVersion,
        String gitCommit,
        String policyVersion,
        String promptVersion,
        LocalDateTime startedAt,
        LocalDateTime finishedAt,
        long durationMs,
        String status,
        List<EvaluationCaseResult> caseResults,
        List<RetrievalEvaluationResult> retrievalResults,
        List<LlmJudgeEvaluationResult> judgeResults,
        Map<String, BigDecimal> aggregateMetrics,
        EvaluationBaselineComparison baselineComparison,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens
) {
    public EvaluationRunResult {
        caseResults = caseResults == null ? List.of() : List.copyOf(caseResults);
        retrievalResults = retrievalResults == null ? List.of() : List.copyOf(retrievalResults);
        judgeResults = judgeResults == null ? List.of() : List.copyOf(judgeResults);
        aggregateMetrics = aggregateMetrics == null ? Map.of() : Map.copyOf(aggregateMetrics);
    }
}
