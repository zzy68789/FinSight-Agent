package com.zzy.finsight.component.evaluation;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationBaselineComparatorTest {
    private final EvaluationBaselineComparator comparator = new EvaluationBaselineComparator();

    @Test
    void blocksCoreRetrievalRegressionOverFivePercentAndWarnsJudgeAndPerformance() {
        EvaluationBaselineComparison comparison = comparator.compare(
                Map.of(
                        "recall_at_3", new BigDecimal("0.89"),
                        "judge_faithfulness", new BigDecimal("0.84"),
                        "p95_duration_ms", new BigDecimal("1300")
                ),
                Map.of(
                        "recall_at_3", BigDecimal.ONE,
                        "judge_faithfulness", new BigDecimal("0.95"),
                        "p95_duration_ms", new BigDecimal("1000")
                )
        );

        assertThat(comparison.status()).isEqualTo("FAIL");
        assertThat(comparison.failures()).anyMatch(reason -> reason.contains("recall_at_3"));
        assertThat(comparison.warnings()).anyMatch(reason -> reason.contains("judge_faithfulness"));
        assertThat(comparison.warnings()).anyMatch(reason -> reason.contains("p95_duration_ms"));
    }
}
