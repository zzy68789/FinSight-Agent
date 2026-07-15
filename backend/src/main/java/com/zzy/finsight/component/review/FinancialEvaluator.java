package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.FinancialEvaluationCase;
import com.zzy.finsight.domain.stock.FinancialEvaluationMetricScore;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationSet;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 按默认样例和规则评估金融报告质量。
 */
@Component
public class FinancialEvaluator {
    public static final String POLICY_VERSION = "financial-evaluation-v3-body-quality-gates";
    private static final String DEFAULT_EVAL_SET = "financial-eval-set.json";
    private static final BigDecimal PASS_THRESHOLD = new BigDecimal("0.80");
    private static final String CITATION_HEADING = "## 引用与数据快照";
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[E(\\d+)]");
    private static final List<String> DIRECTIONAL_TOKENS = List.of(
            "股价修复可期", "上涨空间明确", "分红托底", "建议买入", "直接买入", "看涨", "看好"
    );

    private final ObjectMapper objectMapper;

    public FinancialEvaluator(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 加载内置金融报告评测集。 */
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

    /** 按指定用例评估报告的指标、引用和合规结果。 */
    public FinancialEvaluationResult evaluate(
            FinancialEvaluationCase evalCase,
            String report,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        String text = report == null ? "" : report;
        List<FinancialMetricResult> metricResults = metrics == null ? List.of() : metrics;
        List<FinancialEvidenceItem> evidenceItems = snapshot == null ? List.of() : snapshot.evidenceItems();

        String body = reportBody(text);
        List<FinancialEvaluationMetricScore> scores = List.of(
                score("claim_support_rate", claimSupportScore(body, evalCase, evidenceItems, metricResults), PASS_THRESHOLD, "关键结论需覆盖要点并提供正文就近引用"),
                score("unsupported_claim_rate", unsupportedClaimScore(text), BigDecimal.ONE, "不得出现无依据荐股、保证收益或绝对化承诺"),
                score("contradiction_rate", contradictionScore(text), BigDecimal.ONE, "不得同时表达数据缺失和确定性投资结论"),
                score("numeric_consistency_rate", numericConsistencyScore(body, metricResults), new BigDecimal("0.95"), "正文中的指标展示值需和确定性指标结果一致"),
                score("citation_hit_rate", citationHitScore(body, evidenceItems), new BigDecimal("0.95"), "正文引用必须命中有效证据"),
                score("body_citation_coverage", bodyCitationCoverage(body, evidenceItems, metricResults), new BigDecimal("0.90"), "正文关键指标需就近引用对应原始证据"),
                score("source_quality_rate", sourceQualityScore(evidenceItems), new BigDecimal("0.75"), "公开网页有效正文占比不得低于阈值"),
                score("period_semantic_consistency", periodSemanticScore(body, evidenceItems, metricResults), BigDecimal.ONE, "阶段性 ROE 必须说明报告期且标注未年化"),
                score("directional_claim_support_rate", directionalClaimSupportScore(body, evidenceItems), BigDecimal.ONE, "方向性判断必须引用有效证据"),
                score("low_quality_evidence_rate", lowQualityReferenceScore(body, evidenceItems), BigDecimal.ONE, "正文不得引用低质量证据"),
                score("keypoint_coverage", keypointCoverageScore(body, evalCase), PASS_THRESHOLD, "报告需覆盖评测样例要求的关键点"),
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

    /** 使用内置用例评估报告，无匹配用例时返回空。 */
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

    private BigDecimal claimSupportScore(
            String report,
            FinancialEvaluationCase evalCase,
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialMetricResult> metrics
    ) {
        BigDecimal keypoint = keypointCoverageScore(report, evalCase);
        BigDecimal citation = bodyCitationCoverage(report, evidenceItems, metrics);
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
        Set<Integer> references = citationReferences(report);
        if (references.isEmpty()) {
            return BigDecimal.ZERO;
        }
        long hits = references.stream()
                .filter(reference -> reference > 0 && reference <= evidenceItems.size())
                .filter(reference -> evidenceItems.get(reference - 1).effective())
                .count();
        return ratio(hits, references.size());
    }

    /** 统计正文关键指标获得对应原始证据就近引用的比例。 */
    private BigDecimal bodyCitationCoverage(
            String body,
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialMetricResult> metrics
    ) {
        List<FinancialMetricResult> okMetrics = metrics.stream()
                .filter(metric -> "OK".equals(metric.status()))
                .filter(metric -> metric.displayValue() != null && !metric.displayValue().isBlank())
                .toList();
        if (okMetrics.isEmpty()) {
            return citationReferences(body).isEmpty() ? BigDecimal.ZERO : BigDecimal.ONE;
        }
        long supported = okMetrics.stream()
                .filter(metric -> metricLineSupported(body, evidenceItems, metric))
                .count();
        return ratio(supported, okMetrics.size());
    }

    private boolean metricLineSupported(
            String body,
            List<FinancialEvidenceItem> evidenceItems,
            FinancialMetricResult metric
    ) {
        String line = body.lines()
                .filter(value -> value.contains(metric.displayValue()))
                .findFirst()
                .orElse("");
        Set<Integer> references = citationReferences(line);
        Set<String> metricNames = new HashSet<>(metric.evidenceRefs());
        for (Integer reference : references) {
            if (reference > 0 && reference <= evidenceItems.size()) {
                FinancialEvidenceItem item = evidenceItems.get(reference - 1);
                if (item.effective() && metricNames.contains(item.metricName())) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 统计公开网页证据中通过质量门控的正文比例。 */
    private BigDecimal sourceQualityScore(List<FinancialEvidenceItem> evidenceItems) {
        List<FinancialEvidenceItem> publicItems = evidenceItems.stream()
                .filter(item -> "PUBLIC_MARKET".equals(item.sourceType()))
                .toList();
        if (publicItems.isEmpty()) {
            return BigDecimal.ONE;
        }
        long effective = publicItems.stream().filter(FinancialEvidenceItem::effective).count();
        return ratio(effective, publicItems.size());
    }

    /** 检查阶段性 ROE 的报告期和未年化语义。 */
    private BigDecimal periodSemanticScore(
            String body,
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialMetricResult> metrics
    ) {
        boolean hasRoe = metrics.stream().anyMatch(metric -> "ROE".equals(metric.metricName()) && "OK".equals(metric.status()));
        if (!hasRoe) {
            return BigDecimal.ONE;
        }
        String period = evidenceItems.stream()
                .filter(FinancialEvidenceItem::effective)
                .map(FinancialEvidenceItem::reportPeriod)
                .filter(value -> value != null && value.matches("\\d{8}"))
                .filter(value -> value.endsWith("0331") || value.endsWith("0630") || value.endsWith("0930"))
                .max(String::compareTo)
                .orElse("");
        if (period.isBlank()) {
            return BigDecimal.ONE;
        }
        String stage = period.endsWith("0331") ? "一季度" : period.endsWith("0630") ? "半年度" : "前三季度";
        return body.contains(stage) && body.contains("ROE") && body.contains("未年化")
                ? BigDecimal.ONE
                : BigDecimal.ZERO;
    }

    private BigDecimal directionalClaimSupportScore(String body, List<FinancialEvidenceItem> evidenceItems) {
        List<String> directionalLines = body.lines().filter(this::containsDirectionalClaim).toList();
        if (directionalLines.isEmpty()) {
            return BigDecimal.ONE;
        }
        long supported = directionalLines.stream()
                .filter(line -> citationReferences(line).stream()
                        .anyMatch(reference -> reference > 0
                                && reference <= evidenceItems.size()
                                && evidenceItems.get(reference - 1).effective()))
                .count();
        return ratio(supported, directionalLines.size());
    }

    private BigDecimal lowQualityReferenceScore(String body, List<FinancialEvidenceItem> evidenceItems) {
        boolean hasLowQualityReference = citationReferences(body).stream()
                .filter(reference -> reference > 0 && reference <= evidenceItems.size())
                .map(reference -> evidenceItems.get(reference - 1))
                .anyMatch(item -> !item.effective());
        return hasLowQualityReference ? BigDecimal.ZERO : BigDecimal.ONE;
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

    private String reportBody(String report) {
        int citationIndex = report.indexOf(CITATION_HEADING);
        return citationIndex < 0 ? report : report.substring(0, citationIndex);
    }

    private Set<Integer> citationReferences(String text) {
        Set<Integer> references = new HashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            references.add(Integer.parseInt(matcher.group(1)));
        }
        return references;
    }

    private boolean containsDirectionalClaim(String line) {
        return DIRECTIONAL_TOKENS.stream().anyMatch(line::contains);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT);
    }
}
