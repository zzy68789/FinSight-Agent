package com.zzy.finsight.component.evaluation;

import java.math.BigDecimal;
import java.util.List;

/**
 * 表示版本化离线评测数据集。
 * @param name 数据集名称。
 * @param version 数据集版本。
 * @param defaultAbsoluteTolerance 金融数值默认绝对误差。
 * @param cases 冻结报告与快照样例。
 * @param retrievalCases 检索相关性标注样例。
 */
public record EvaluationDataset(
        String name,
        String version,
        BigDecimal defaultAbsoluteTolerance,
        List<EvaluationCaseFixture> cases,
        List<RetrievalCaseFixture> retrievalCases
) {
    public EvaluationDataset {
        defaultAbsoluteTolerance = defaultAbsoluteTolerance == null
                ? new BigDecimal("0.01")
                : defaultAbsoluteTolerance;
        cases = cases == null ? List.of() : List.copyOf(cases);
        retrievalCases = retrievalCases == null ? List.of() : List.copyOf(retrievalCases);
    }
}
