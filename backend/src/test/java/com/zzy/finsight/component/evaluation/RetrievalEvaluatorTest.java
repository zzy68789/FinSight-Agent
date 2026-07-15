package com.zzy.finsight.component.evaluation;

import com.zzy.finsight.rag.RagDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RetrievalEvaluatorTest {
    private final RetrievalEvaluator evaluator = new RetrievalEvaluator();

    @Test
    void computesRecallPrecisionMrrAndNdcgForRankedChunks() {
        RetrievalEvaluationResult result = evaluator.evaluate(
                "收入 风险",
                List.of(
                        new RagDocument("relevant-a", "a", "收入证据", 0.9),
                        new RagDocument("noise", "b", "无关证据", 0.8),
                        new RagDocument("relevant-b", "c", "风险证据", 0.7)
                ),
                Map.of("relevant-a", 2, "relevant-b", 1)
        );

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(score(result, "recall_at_3")).isEqualByComparingTo("1.0000");
        assertThat(score(result, "precision_at_3")).isEqualByComparingTo("0.6667");
        assertThat(score(result, "mrr")).isEqualByComparingTo("1.0000");
        assertThat(score(result, "ndcg_at_3")).isBetween(new BigDecimal("0.90"), BigDecimal.ONE);
    }

    @Test
    void failsHardGateWhenRelevantChunkFallsOutsideTopThree() {
        RetrievalEvaluationResult result = evaluator.evaluate(
                "收入",
                List.of(
                        new RagDocument("noise-a", "a", "无关一", 0.9),
                        new RagDocument("noise-b", "b", "无关二", 0.8),
                        new RagDocument("noise-c", "c", "无关三", 0.7),
                        new RagDocument("relevant", "d", "收入证据", 0.6)
                ),
                Map.of("relevant", 2)
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.failedReasons()).anyMatch(reason -> reason.contains("recall_at_3"));
    }

    private BigDecimal score(RetrievalEvaluationResult result, String metricName) {
        return result.metricScores().stream()
                .filter(metric -> metricName.equals(metric.metricName()))
                .findFirst()
                .orElseThrow()
                .score();
    }
}
