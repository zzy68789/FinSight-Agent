package com.zzy.finsight.agent.quality;

import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceIssue;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将引用、合规和评测结果统一为稳定失败类型与单一路由决策。
 */
@Component
public class QualityGateDecisionEngine {
    public static final String POLICY_VERSION = "quality-gate-routing-v1";
    private static final Set<String> VALID_REVIEW_STATUSES = Set.of("PASS", "FAIL");

    /** 根据三类审查结果和证据快照生成 fail-closed 路由。 */
    public QualityGateDecision decide(
            CitationReviewResult citation,
            FinancialComplianceReviewResult compliance,
            FinancialEvaluationResult evaluation,
            FinancialSnapshot snapshot
    ) {
        List<QualityGateIssue> issues = new ArrayList<>();
        collectInvalidOutputIssues(citation, compliance, evaluation, issues);
        collectCitationIssues(citation, issues);
        collectComplianceIssues(compliance, issues);
        collectEvaluationIssues(evaluation, issues);
        if (issues.isEmpty()) {
            return QualityGateDecision.pass();
        }
        QualityGateRoute route = selectRoute(issues, effectiveEvidenceCount(snapshot));
        return new QualityGateDecision("FAIL", route, issues, summarize(issues));
    }

    private void collectInvalidOutputIssues(
            CitationReviewResult citation,
            FinancialComplianceReviewResult compliance,
            FinancialEvaluationResult evaluation,
            List<QualityGateIssue> issues
    ) {
        validateStatus(citation == null ? null : citation.status(), QualityGateSource.CITATION_REVIEW, issues);
        validateStatus(compliance == null ? null : compliance.status(), QualityGateSource.COMPLIANCE_REVIEW, issues);
        validateStatus(evaluation == null ? null : evaluation.status(), QualityGateSource.EVALUATION, issues);
    }

    private void validateStatus(
            String status,
            QualityGateSource source,
            List<QualityGateIssue> issues
    ) {
        if (status == null || !VALID_REVIEW_STATUSES.contains(status)) {
            issues.add(new QualityGateIssue(
                    QualityGateFailureType.REVIEW_OUTPUT_INVALID,
                    source,
                    "INVALID_REVIEW_STATUS",
                    "审查结果缺失或状态非法"
            ));
        }
    }

    private void collectCitationIssues(CitationReviewResult citation, List<QualityGateIssue> issues) {
        if (citation == null || !"FAIL".equals(citation.status())) {
            return;
        }
        String code = normalizedCode(citation.code(), "CITATION_REVIEW_FAILED");
        issues.add(new QualityGateIssue(
                citationFailureType(code),
                QualityGateSource.CITATION_REVIEW,
                code,
                citation.reason()
        ));
    }

    private void collectComplianceIssues(
            FinancialComplianceReviewResult compliance,
            List<QualityGateIssue> issues
    ) {
        if (compliance == null || !"FAIL".equals(compliance.status())) {
            return;
        }
        List<FinancialComplianceIssue> complianceIssues = compliance.issues();
        for (FinancialComplianceIssue issue : complianceIssues) {
            if ("citation".equalsIgnoreCase(issue.category())) {
                continue;
            }
            String category = normalizedCode(issue.category(), "COMPLIANCE");
            issues.add(new QualityGateIssue(
                    QualityGateFailureType.COMPLIANCE_VIOLATION,
                    QualityGateSource.COMPLIANCE_REVIEW,
                    "COMPLIANCE_" + category,
                    issue.description()
            ));
        }
        if (complianceIssues.stream().allMatch(issue -> "citation".equalsIgnoreCase(issue.category()))
                && issues.stream().noneMatch(issue -> issue.source() == QualityGateSource.CITATION_REVIEW)) {
            issues.add(new QualityGateIssue(
                    QualityGateFailureType.REVIEW_OUTPUT_INVALID,
                    QualityGateSource.COMPLIANCE_REVIEW,
                    "COMPLIANCE_FAILURE_WITHOUT_ISSUE",
                    "合规审查失败但没有独立可路由问题"
            ));
        }
    }

    private void collectEvaluationIssues(
            FinancialEvaluationResult evaluation,
            List<QualityGateIssue> issues
    ) {
        if (evaluation == null || !"FAIL".equals(evaluation.status())) {
            return;
        }
        List<FinancialEvaluationMetricScore> failedMetrics = evaluation.metricScores().stream()
                .filter(metric -> metric.gateLevel() == FinancialEvaluationMetricScore.GateLevel.HARD)
                .filter(metric -> "FAIL".equals(metric.status()) || "ERROR".equals(metric.status()))
                .toList();
        for (FinancialEvaluationMetricScore metric : failedMetrics) {
            String code = normalizedCode(metric.metricName(), "EVALUATION_METRIC");
            issues.add(new QualityGateIssue(
                    evaluationFailureType(code),
                    QualityGateSource.EVALUATION,
                    code,
                    metric.reason()
            ));
        }
        if (failedMetrics.isEmpty()) {
            issues.add(new QualityGateIssue(
                    QualityGateFailureType.REVIEW_OUTPUT_INVALID,
                    QualityGateSource.EVALUATION,
                    "EVALUATION_FAILURE_WITHOUT_HARD_METRIC",
                    "评测状态失败但没有失败的硬门禁指标"
            ));
        }
    }

    private QualityGateFailureType citationFailureType(String code) {
        return switch (code) {
            case "EVIDENCE_INSUFFICIENT", "VALUATION_SEMANTIC_INVALID" ->
                    QualityGateFailureType.EVIDENCE_INSUFFICIENT;
            case "EVIDENCE_SEMANTIC_INVALID", "EVIDENCE_CONFLICT" -> QualityGateFailureType.EVIDENCE_INVALID;
            case "NUMERIC_CONSISTENCY_FAIL", "NUMERIC_FACT_UNSUPPORTED", "METRIC_EVIDENCE_MISSING" ->
                    QualityGateFailureType.NUMERIC_MISMATCH;
            case "PERIOD_MIXED", "PERIOD_SEMANTIC_INVALID", "MARKET_SNAPSHOT_MIXED" ->
                    QualityGateFailureType.REPORT_SEMANTIC_INVALID;
            case "CITATION_SECTION_MISSING", "BODY_CITATION_MISSING", "CITATION_REFERENCE_INVALID",
                    "CITATION_REFERENCE_INEFFECTIVE", "METRIC_BODY_CITATION_MISSING", "CITATION_MISSING" ->
                    QualityGateFailureType.CITATION_INVALID;
            case "DIRECTIONAL_CLAIM_UNSUPPORTED" -> QualityGateFailureType.COMPLIANCE_VIOLATION;
            default -> QualityGateFailureType.REVIEW_OUTPUT_INVALID;
        };
    }

    private QualityGateFailureType evaluationFailureType(String code) {
        return switch (code) {
            case "NUMERIC_CONSISTENCY_RATE" -> QualityGateFailureType.NUMERIC_MISMATCH;
            case "CITATION_HIT_RATE", "BODY_CITATION_COVERAGE", "LOW_QUALITY_EVIDENCE_RATE" ->
                    QualityGateFailureType.CITATION_INVALID;
            case "UNSUPPORTED_CLAIM_RATE", "CONTRADICTION_RATE", "DIRECTIONAL_CLAIM_SUPPORT_RATE" ->
                    QualityGateFailureType.COMPLIANCE_VIOLATION;
            case "PERIOD_SEMANTIC_CONSISTENCY" -> QualityGateFailureType.REPORT_SEMANTIC_INVALID;
            default -> QualityGateFailureType.EVALUATION_FAILED;
        };
    }

    private QualityGateRoute selectRoute(List<QualityGateIssue> issues, long effectiveEvidenceCount) {
        Set<QualityGateFailureType> types = issues.stream()
                .map(QualityGateIssue::type)
                .collect(Collectors.toSet());
        if (types.contains(QualityGateFailureType.REVIEW_OUTPUT_INVALID)) {
            return QualityGateRoute.STOP_FAILED;
        }
        if (types.contains(QualityGateFailureType.EVIDENCE_INSUFFICIENT)
                || types.contains(QualityGateFailureType.EVIDENCE_INVALID)) {
            return QualityGateRoute.COLLECT_MORE_EVIDENCE;
        }
        if (types.contains(QualityGateFailureType.NUMERIC_MISMATCH)) {
            return QualityGateRoute.RECALCULATE_DETERMINISTIC_RESULTS;
        }
        if (types.contains(QualityGateFailureType.CITATION_INVALID) && effectiveEvidenceCount < 3) {
            return QualityGateRoute.COLLECT_MORE_EVIDENCE;
        }
        return QualityGateRoute.REWRITE_REPORT;
    }

    private long effectiveEvidenceCount(FinancialSnapshot snapshot) {
        return snapshot == null ? 0L : snapshot.evidenceItems().stream()
                .filter(FinancialEvidenceItem::effective)
                .count();
    }

    private String summarize(List<QualityGateIssue> issues) {
        return issues.stream()
                .map(issue -> issue.code() + "：" + issue.detail())
                .distinct()
                .collect(Collectors.joining("；"));
    }

    private String normalizedCode(String value, String fallback) {
        String normalized = value == null ? "" : value.trim().toUpperCase(Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        return normalized.isBlank() ? fallback : normalized;
    }
}
