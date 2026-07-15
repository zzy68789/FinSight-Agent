package com.zzy.finsight.component.evaluation;

import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;

import java.util.List;

/**
 * 表示 LLM-as-Judge 对单个冻结样例的辅助评价。
 * @param caseId 评测样例标识。
 * @param status 结果状态，可能为 PASS、WARN、ERROR 或 SKIPPED。
 * @param metricScores Judge 四维评分。
 * @param issues Judge 识别的问题。
 * @param rationale Judge 简要依据。
 * @param promptVersion Judge 提示词版本。
 * @param modelName 实际 Judge 模型。
 * @param inputTokens 输入 Token 数。
 * @param outputTokens 输出 Token 数。
 * @param totalTokens 总 Token 数。
 * @param finishReason 模型结束原因。
 * @param durationMs 调用耗时。
 * @param errorMessage 错误原因。
 */
public record LlmJudgeEvaluationResult(
        String caseId,
        String status,
        List<FinancialEvaluationMetricScore> metricScores,
        List<String> issues,
        List<String> rationale,
        String promptVersion,
        String modelName,
        int inputTokens,
        int outputTokens,
        int totalTokens,
        String finishReason,
        long durationMs,
        String errorMessage
) {
    public LlmJudgeEvaluationResult {
        caseId = caseId == null ? "" : caseId;
        status = status == null ? "ERROR" : status;
        metricScores = metricScores == null ? List.of() : List.copyOf(metricScores);
        issues = issues == null ? List.of() : List.copyOf(issues);
        rationale = rationale == null ? List.of() : List.copyOf(rationale);
        promptVersion = promptVersion == null ? "" : promptVersion;
        modelName = modelName == null ? "" : modelName;
        finishReason = finishReason == null ? "UNKNOWN" : finishReason;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
