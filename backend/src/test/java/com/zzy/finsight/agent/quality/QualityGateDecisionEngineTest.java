package com.zzy.finsight.agent.quality;

import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceIssue;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QualityGateDecisionEngineTest {
    private final QualityGateDecisionEngine engine = new QualityGateDecisionEngine();

    @Test
    void passesOnlyWhenAllReviewersPass() {
        QualityGateDecision decision = engine.decide(
                CitationReviewResult.pass(),
                compliancePass(),
                evaluationPass(),
                snapshot(3)
        );

        assertThat(decision.passed()).isTrue();
        assertThat(decision.route()).isEqualTo(QualityGateRoute.PASS);
        assertThat(decision.issues()).isEmpty();
    }

    @Test
    void routesInsufficientEvidenceBackToCollection() {
        CitationReviewResult citation = CitationReviewResult.fail(
                "EVIDENCE_INSUFFICIENT: 有效证据少于 3 条"
        );
        QualityGateDecision decision = engine.decide(
                citation,
                compliancePass(),
                evaluationPass(),
                snapshot(2)
        );

        assertThat(citation.code()).isEqualTo("EVIDENCE_INSUFFICIENT");
        assertThat(decision.route()).isEqualTo(QualityGateRoute.COLLECT_MORE_EVIDENCE);
        assertThat(decision.issues()).extracting(QualityGateIssue::type)
                .contains(QualityGateFailureType.EVIDENCE_INSUFFICIENT);
    }

    @Test
    void prioritizesEvidenceCollectionWhenNumericAndEvidenceFailuresCoexist() {
        QualityGateDecision decision = engine.decide(
                CitationReviewResult.fail("EVIDENCE_SEMANTIC_INVALID: ROE 口径冲突"),
                compliancePass(),
                evaluationFail("numeric_consistency_rate"),
                snapshot(3)
        );

        assertThat(decision.route()).isEqualTo(QualityGateRoute.COLLECT_MORE_EVIDENCE);
    }

    @Test
    void routesUnresolvedEvidenceConflictBackToCollection() {
        QualityGateDecision decision = engine.decide(
                CitationReviewResult.fail("EVIDENCE_CONFLICT", "EVIDENCE_CONFLICT: 净利润来源冲突"),
                compliancePass(),
                evaluationPass(),
                snapshot(3)
        );

        assertThat(decision.route()).isEqualTo(QualityGateRoute.COLLECT_MORE_EVIDENCE);
        assertThat(decision.issues()).singleElement().satisfies(issue -> {
            assertThat(issue.type()).isEqualTo(QualityGateFailureType.EVIDENCE_INVALID);
            assertThat(issue.code()).isEqualTo("EVIDENCE_CONFLICT");
        });
    }

    @Test
    void routesNumericMismatchToDeterministicRecalculation() {
        QualityGateDecision decision = engine.decide(
                CitationReviewResult.pass(),
                compliancePass(),
                evaluationFail("numeric_consistency_rate"),
                snapshot(3)
        );

        assertThat(decision.route()).isEqualTo(QualityGateRoute.RECALCULATE_DETERMINISTIC_RESULTS);
        assertThat(decision.summary()).contains("NUMERIC_CONSISTENCY_RATE");
    }

    @Test
    void rewritesCitationWhenEnoughEffectiveEvidenceAlreadyExists() {
        QualityGateDecision decision = engine.decide(
                CitationReviewResult.fail("BODY_CITATION_MISSING: 正文缺少有效证据编号"),
                compliancePass(),
                evaluationPass(),
                snapshot(3)
        );

        assertThat(decision.route()).isEqualTo(QualityGateRoute.REWRITE_REPORT);
        assertThat(decision.issues()).extracting(QualityGateIssue::type)
                .contains(QualityGateFailureType.CITATION_INVALID);
    }

    @Test
    void rewritesComplianceViolationsInsteadOfCollectingUnrelatedEvidence() {
        FinancialComplianceReviewResult compliance = new FinancialComplianceReviewResult(
                "FAIL",
                new BigDecimal("75.00"),
                List.of(new FinancialComplianceIssue(
                        "critical", "recommendation", "报告包含直接荐股表达", "删除直接荐股表达"
                ))
        );

        QualityGateDecision decision = engine.decide(
                CitationReviewResult.pass(), compliance, evaluationPass(), snapshot(3)
        );

        assertThat(decision.route()).isEqualTo(QualityGateRoute.REWRITE_REPORT);
        assertThat(decision.issues()).extracting(QualityGateIssue::type)
                .contains(QualityGateFailureType.COMPLIANCE_VIOLATION);
    }

    @Test
    void stopsFailClosedWhenReviewerOutputIsInvalid() {
        QualityGateDecision decision = engine.decide(null, compliancePass(), evaluationPass(), snapshot(3));

        assertThat(decision.route()).isEqualTo(QualityGateRoute.STOP_FAILED);
        assertThat(decision.issues()).extracting(QualityGateIssue::type)
                .contains(QualityGateFailureType.REVIEW_OUTPUT_INVALID);
    }

    private FinancialComplianceReviewResult compliancePass() {
        return new FinancialComplianceReviewResult("PASS", new BigDecimal("100.00"), List.of());
    }

    private FinancialEvaluationResult evaluationPass() {
        return new FinancialEvaluationResult(
                "600519", "贵州茅台", BigDecimal.ONE, "PASS", List.of(), List.of()
        );
    }

    private FinancialEvaluationResult evaluationFail(String metricName) {
        FinancialEvaluationMetricScore score = new FinancialEvaluationMetricScore(
                metricName,
                BigDecimal.ZERO,
                new BigDecimal("0.95"),
                "FAIL",
                "硬门禁未通过",
                FinancialEvaluationMetricScore.Category.RULE,
                FinancialEvaluationMetricScore.GateLevel.HARD,
                FinancialEvaluationMetricScore.Direction.HIGHER_BETTER
        );
        return new FinancialEvaluationResult(
                "600519", "贵州茅台", BigDecimal.ZERO, "FAIL", List.of(score), List.of("硬门禁未通过")
        );
    }

    private FinancialSnapshot snapshot(int effectiveEvidenceCount) {
        List<FinancialEvidenceItem> evidence = java.util.stream.IntStream.range(0, effectiveEvidenceCount)
                .mapToObj(index -> new FinancialEvidenceItem(
                        "PUBLIC_MARKET",
                        "测试来源" + index,
                        "",
                        null,
                        "latest",
                        "METRIC_" + index,
                        null,
                        null,
                        "测试证据" + index,
                        new BigDecimal("0.90"),
                        LocalDateTime.of(2026, 8, 12, 10, 0),
                        ""
                ))
                .toList();
        return new FinancialSnapshot(null, "latest", "hybrid", evidence, LocalDateTime.now());
    }
}
