package com.zzy.finsight.component.evaluation;

import com.zzy.finsight.domain.stock.FinancialEvaluationResult;

import java.util.List;

/**
 * 表示一个冻结报告样例的期望校验结果。
 * @param caseId 样例标识。
 * @param scenario 场景标签。
 * @param expectedStatus 期望规则状态。
 * @param actualStatus 实际规则状态。
 * @param status 样例断言状态。
 * @param evaluation 详细规则评分。
 * @param failedReasons 期望不一致原因。
 */
public record EvaluationCaseResult(
        String caseId,
        String scenario,
        String expectedStatus,
        String actualStatus,
        String status,
        FinancialEvaluationResult evaluation,
        List<String> failedReasons
) {
    public EvaluationCaseResult {
        failedReasons = failedReasons == null ? List.of() : List.copyOf(failedReasons);
    }
}
