package com.zzy.finsight.component.evaluation;

import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.rag.RagDocument;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 根据带等级的相关分片标注计算 RAG 检索质量指标。
 */
@Component
public class RetrievalEvaluator {
    private static final BigDecimal RECALL_AT_3_THRESHOLD = new BigDecimal("0.90");
    private static final BigDecimal MRR_THRESHOLD = new BigDecimal("0.80");
    private static final BigDecimal NDCG_AT_3_THRESHOLD = new BigDecimal("0.85");

    /** 对按相关性排序的检索结果计算 Top-1、Top-3 和 Top-5 指标。 */
    public RetrievalEvaluationResult evaluate(
            String query,
            List<RagDocument> rankedDocuments,
            Map<String, Integer> relevanceGrades
    ) {
        List<RagDocument> documents = rankedDocuments == null ? List.of() : rankedDocuments;
        Map<String, Integer> grades = relevanceGrades == null ? Map.of() : new LinkedHashMap<>(relevanceGrades);
        List<FinancialEvaluationMetricScore> scores = List.of(
                metric("recall_at_1", recall(documents, grades, 1), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY),
                metric("recall_at_3", recall(documents, grades, 3), RECALL_AT_3_THRESHOLD,
                        FinancialEvaluationMetricScore.GateLevel.HARD),
                metric("recall_at_5", recall(documents, grades, 5), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY),
                metric("precision_at_1", precision(documents, grades, 1), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY),
                metric("precision_at_3", precision(documents, grades, 3), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY),
                metric("precision_at_5", precision(documents, grades, 5), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY),
                metric("mrr", reciprocalRank(documents, grades), MRR_THRESHOLD,
                        FinancialEvaluationMetricScore.GateLevel.HARD),
                metric("ndcg_at_1", ndcg(documents, grades, 1), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY),
                metric("ndcg_at_3", ndcg(documents, grades, 3), NDCG_AT_3_THRESHOLD,
                        FinancialEvaluationMetricScore.GateLevel.HARD),
                metric("ndcg_at_5", ndcg(documents, grades, 5), BigDecimal.ZERO,
                        FinancialEvaluationMetricScore.GateLevel.ADVISORY)
        );
        List<String> failures = scores.stream()
                .filter(score -> score.gateLevel() == FinancialEvaluationMetricScore.GateLevel.HARD)
                .filter(score -> "FAIL".equals(score.status()))
                .map(score -> score.metricName() + " 未达到阈值 " + score.threshold())
                .toList();
        return new RetrievalEvaluationResult(query, scores, failures.isEmpty() ? "PASS" : "FAIL", failures);
    }

    private BigDecimal recall(List<RagDocument> documents, Map<String, Integer> grades, int topK) {
        long relevantCount = grades.values().stream().filter(grade -> grade != null && grade > 0).count();
        if (relevantCount == 0) {
            return BigDecimal.ZERO;
        }
        long hits = documents.stream()
                .limit(topK)
                .map(RagDocument::chunkId)
                .filter(chunkId -> grades.getOrDefault(chunkId, 0) > 0)
                .distinct()
                .count();
        return ratio(hits, relevantCount);
    }

    private BigDecimal precision(List<RagDocument> documents, Map<String, Integer> grades, int topK) {
        long hits = documents.stream()
                .limit(topK)
                .filter(document -> grades.getOrDefault(document.chunkId(), 0) > 0)
                .count();
        return ratio(hits, topK);
    }

    private BigDecimal reciprocalRank(List<RagDocument> documents, Map<String, Integer> grades) {
        for (int index = 0; index < documents.size(); index++) {
            if (grades.getOrDefault(documents.get(index).chunkId(), 0) > 0) {
                return BigDecimal.ONE.divide(BigDecimal.valueOf(index + 1L), 6, RoundingMode.HALF_UP);
            }
        }
        return BigDecimal.ZERO;
    }

    private BigDecimal ndcg(List<RagDocument> documents, Map<String, Integer> grades, int topK) {
        List<Integer> actual = documents.stream()
                .limit(topK)
                .map(document -> grades.getOrDefault(document.chunkId(), 0))
                .toList();
        List<Integer> ideal = grades.values().stream()
                .filter(grade -> grade != null && grade > 0)
                .sorted(Comparator.reverseOrder())
                .limit(topK)
                .toList();
        double idealDcg = dcg(ideal);
        return idealDcg == 0.0d ? BigDecimal.ZERO : decimal(dcg(actual) / idealDcg);
    }

    private double dcg(List<Integer> grades) {
        double value = 0.0d;
        for (int index = 0; index < grades.size(); index++) {
            int grade = Math.max(0, grades.get(index));
            value += (Math.pow(2.0d, grade) - 1.0d) / (Math.log(index + 2.0d) / Math.log(2.0d));
        }
        return value;
    }

    private FinancialEvaluationMetricScore metric(
            String name,
            BigDecimal value,
            BigDecimal threshold,
            FinancialEvaluationMetricScore.GateLevel gateLevel
    ) {
        BigDecimal score = value.setScale(4, RoundingMode.HALF_UP);
        String status = score.compareTo(threshold) >= 0
                ? "PASS"
                : gateLevel == FinancialEvaluationMetricScore.GateLevel.HARD ? "FAIL" : "WARN";
        return new FinancialEvaluationMetricScore(
                name,
                score,
                threshold,
                status,
                "检索结果需命中人工标注的相关分片",
                FinancialEvaluationMetricScore.Category.RETRIEVAL,
                gateLevel,
                FinancialEvaluationMetricScore.Direction.HIGHER_BETTER
        );
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 6, RoundingMode.HALF_UP);
    }

    private BigDecimal decimal(double value) {
        return BigDecimal.valueOf(value).setScale(6, RoundingMode.HALF_UP);
    }
}
