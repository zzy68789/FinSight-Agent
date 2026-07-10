package com.zzy.drai.financial;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Component
public class FinancialEvaluationService {
    public static final String POLICY_VERSION = "financial-evaluation-v2";
    private static final String DEFAULT_EVAL_SET = "financial-eval-set.json";
    private static final BigDecimal PASS_THRESHOLD = new BigDecimal("0.80");

    private final ObjectMapper objectMapper;

    public FinancialEvaluationService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public FinancialEvaluationSet loadDefaultSet() {
        try (InputStream input = Thread.currentThread()
                .getContextClassLoader()
                .getResourceAsStream(DEFAULT_EVAL_SET)) {
            if (input == null) {
                throw new IllegalStateException("未找到默认金融评测集：" + DEFAULT_EVAL_SET);
            }
            return objectMapper.readValue(input, FinancialEvaluationSet.class);
        } catch (IOException e) {
            throw new IllegalStateException("读取默认金融评测集失败", e);
        }
    }

    public FinancialEvaluationResult evaluate(
            FinancialEvaluationCase evalCase,
            String report,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        String text = report == null ? "" : report;
        List<FinancialMetricResult> metricResults = metrics == null ? List.of() : metrics;
        List<FinancialEvidenceItem> evidenceItems = snapshot == null ? List.of() : snapshot.evidenceItems();

        List<FinancialEvaluationMetricScore> scores = List.of(
                score("claim_support_rate", claimSupportScore(text, evalCase, evidenceItems), PASS_THRESHOLD, "关键结论需覆盖要点并提供证据引用"),
                score("unsupported_claim_rate", unsupportedClaimScore(text), BigDecimal.ONE, "不得出现无依据荐股、保证收益或绝对化承诺"),
                score("contradiction_rate", contradictionScore(text), BigDecimal.ONE, "不得同时表达数据缺失和确定性投资结论"),
                score("numeric_consistency_rate", numericConsistencyScore(text, metricResults), new BigDecimal("0.95"), "报告中的指标展示值需和确定性指标结果一致"),
                score("citation_hit_rate", citationHitScore(text, evidenceItems), PASS_THRESHOLD, "报告需包含引用与数据快照并命中有效证据"),
                score("keypoint_coverage", keypointCoverageScore(text, evalCase), PASS_THRESHOLD, "报告需覆盖评测样例要求的关键点"),
                score("evidence_effective_rate", evidenceEffectiveScore(evidenceItems), PASS_THRESHOLD, "有效证据占比不得低于阈值")
        );

        BigDecimal overall = scores.stream()
                .map(FinancialEvaluationMetricScore::score)
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .divide(BigDecimal.valueOf(scores.size()), 2, RoundingMode.HALF_UP);
        List<String> failedReasons = scores.stream()
                .filter(item -> "FAIL".equals(item.status()))
                .map(item -> item.metricName() + "：" + item.reason())
                .toList();
        String status = overall.compareTo(PASS_THRESHOLD) >= 0 && failedReasons.isEmpty() ? "PASS" : "FAIL";
        return new FinancialEvaluationResult(evalCase.ticker(), evalCase.name(), overall, status, scores, failedReasons);
    }

    public Optional<FinancialEvaluationResult> evaluateDefaultCase(
            String report,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        if (snapshot == null || snapshot.subject() == null) {
            return Optional.empty();
        }
        String ticker = snapshot.subject().ticker();
        return loadDefaultSet().cases().stream()
                .filter(evalCase -> evalCase.ticker().equals(ticker))
                .findFirst()
                .map(evalCase -> evaluate(evalCase, report, snapshot, metrics));
    }

    private FinancialEvaluationMetricScore score(String metricName, BigDecimal value, BigDecimal threshold, String reason) {
        BigDecimal normalized = value.setScale(2, RoundingMode.HALF_UP);
        String status = normalized.compareTo(threshold) >= 0 ? "PASS" : "FAIL";
        return new FinancialEvaluationMetricScore(metricName, normalized, threshold, status, reason);
    }

    private BigDecimal claimSupportScore(String report, FinancialEvaluationCase evalCase, List<FinancialEvidenceItem> evidenceItems) {
        BigDecimal keypoint = keypointCoverageScore(report, evalCase);
        BigDecimal citation = citationHitScore(report, evidenceItems);
        return keypoint.add(citation).divide(BigDecimal.valueOf(2), 4, RoundingMode.HALF_UP);
    }

    private BigDecimal unsupportedClaimScore(String report) {
        return containsUnsupportedRecommendation(report) ? BigDecimal.ZERO : BigDecimal.ONE;
    }

    private BigDecimal contradictionScore(String report) {
        String normalized = normalize(report);
        boolean dataMissing = normalized.contains("数据缺失") || normalized.contains("data_missing") || normalized.contains("missing_input");
        boolean certainty = containsUnsupportedRecommendation(report);
        return dataMissing && certainty ? BigDecimal.ZERO : BigDecimal.ONE;
    }

    private BigDecimal numericConsistencyScore(String report, List<FinancialMetricResult> metrics) {
        List<FinancialMetricResult> okMetrics = metrics.stream()
                .filter(metric -> "OK".equals(metric.status()))
                .filter(metric -> metric.displayValue() != null && !metric.displayValue().isBlank())
                .toList();
        if (okMetrics.isEmpty()) {
            return BigDecimal.ONE;
        }
        long hits = okMetrics.stream()
                .filter(metric -> report.contains(metric.displayValue()))
                .count();
        return ratio(hits, okMetrics.size());
    }

    private BigDecimal citationHitScore(String report, List<FinancialEvidenceItem> evidenceItems) {
        long effectiveCount = evidenceItems.stream()
                .filter(FinancialEvidenceItem::effective)
                .count();
        if (effectiveCount == 0) {
            return BigDecimal.ZERO;
        }
        int hits = 0;
        int evidenceIndex = 1;
        for (FinancialEvidenceItem item : evidenceItems) {
            if (item.effective() && report.contains("[E" + evidenceIndex + "]")) {
                hits++;
            }
            evidenceIndex++;
        }
        if (hits == 0 && report.contains("引用与数据快照")) {
            hits = 1;
        }
        return ratio(hits, effectiveCount);
    }

    private BigDecimal keypointCoverageScore(String report, FinancialEvaluationCase evalCase) {
        List<String> keypoints = evalCase.requiredKeypoints();
        if (keypoints.isEmpty()) {
            return BigDecimal.ONE;
        }
        long hits = keypoints.stream()
                .filter(keypoint -> report.contains(keypoint))
                .count();
        return ratio(hits, keypoints.size());
    }

    private BigDecimal evidenceEffectiveScore(List<FinancialEvidenceItem> evidenceItems) {
        if (evidenceItems.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long effective = evidenceItems.stream().filter(FinancialEvidenceItem::effective).count();
        return ratio(effective, evidenceItems.size());
    }

    private BigDecimal ratio(long numerator, long denominator) {
        if (denominator <= 0) {
            return BigDecimal.ONE;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 4, RoundingMode.HALF_UP);
    }

    private boolean containsUnsupportedRecommendation(String report) {
        String normalized = normalize(report);
        List<String> tokens = new ArrayList<>();
        tokens.add("保证收益");
        tokens.add("承诺收益");
        tokens.add("稳赚");
        tokens.add("必涨");
        tokens.add("直接买入");
        tokens.add("可以直接买入");
        tokens.add("guaranteed return");
        tokens.add("risk-free return");
        return tokens.stream().anyMatch(normalized::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
