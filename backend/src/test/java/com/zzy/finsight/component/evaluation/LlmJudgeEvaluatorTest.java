package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.llm.LlmGenerationResult;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LlmJudgeEvaluatorTest {
    private final LlmJudgeEvaluator evaluator = new LlmJudgeEvaluator(
            (prompt, modelType) -> "unused",
            new ObjectMapper()
    );

    @Test
    void parsesStructuredJudgeScoresAsAdvisoryMetrics() {
        LlmJudgeEvaluationResult result = evaluator.parse(
                "case-1",
                new LlmGenerationResult(
                        """
                                {"relevance":0.95,"faithfulness":0.90,"completeness":0.75,
                                "research_helpfulness":0.85,"issues":["关键点不足"],"rationale":["证据一致"]}
                                """,
                        "judge-model",
                        100,
                        20,
                        120,
                        "STOP",
                        300
                )
        );

        assertThat(result.status()).isEqualTo("WARN");
        assertThat(result.metricScores()).hasSize(4);
        assertThat(result.metricScores()).allMatch(score -> score.gateLevel()
                == com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore.GateLevel.ADVISORY);
        assertThat(result.totalTokens()).isEqualTo(120);
    }

    @Test
    void marksInvalidJudgeJsonAsErrorInsteadOfPassing() {
        LlmJudgeEvaluationResult result = evaluator.parse(
                "case-2",
                new LlmGenerationResult("not-json", "judge-model", 0, 0, 0, "UNKNOWN", 10)
        );

        assertThat(result.status()).isEqualTo("ERROR");
        assertThat(result.errorMessage()).contains("解析失败");
    }
}
