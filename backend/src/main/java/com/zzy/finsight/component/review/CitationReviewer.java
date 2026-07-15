package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 检查报告引用、报告期和指标展示是否可信。
 */
@Component
public class CitationReviewer {
    public static final String POLICY_VERSION = "citation-policy-v3-body-evidence";
    private static final BigDecimal ABSOLUTE_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.005");
    private static final String CITATION_HEADING = "## 引用与数据快照";
    private static final Pattern CITATION_PATTERN = Pattern.compile("\\[E(\\d+)]");
    private static final List<String> DIRECTIONAL_TOKENS = List.of(
            "股价修复可期", "上涨空间明确", "分红托底", "值得买入", "建议买入", "看涨", "看好"
    );

    /** 检查报告引用、报告期和指标展示是否可追溯。 */
    public CitationReviewResult review(String report, FinancialSnapshot snapshot, List<FinancialMetricResult> metrics) {
        Optional<FinancialEvidenceItem> criticalIssue = snapshot.evidenceItems().stream()
                .filter(item -> FinancialEvidenceIssueCodes.critical(item.issueCode()))
                .findFirst();
        if (criticalIssue.isPresent()) {
            FinancialEvidenceItem item = criticalIssue.orElseThrow();
            return CitationReviewResult.fail(
                    "EVIDENCE_SEMANTIC_INVALID: " + item.metricName() + " 存在 " + item.issueCode()
            );
        }
        long effectiveEvidenceCount = snapshot.evidenceItems().stream().filter(FinancialEvidenceItem::effective).count();
        if (effectiveEvidenceCount < 3) {
            return CitationReviewResult.fail("EVIDENCE_INSUFFICIENT: 有效证据少于 3 条");
        }
        if (report == null || !report.contains(CITATION_HEADING)) {
            return CitationReviewResult.fail("CITATION_SECTION_MISSING: 报告缺少引用与数据快照章节");
        }
        String body = report.substring(0, report.indexOf(CITATION_HEADING));
        Map<Integer, FinancialEvidenceItem> indexedEvidence = indexEvidence(snapshot.evidenceItems());
        Set<Integer> bodyReferences = citationReferences(body);
        if (bodyReferences.isEmpty()) {
            return CitationReviewResult.fail("BODY_CITATION_MISSING: 正文缺少有效证据编号");
        }
        for (Integer reference : bodyReferences) {
            FinancialEvidenceItem item = indexedEvidence.get(reference);
            if (item == null) {
                return CitationReviewResult.fail("CITATION_REFERENCE_INVALID: 正文引用不存在的 E" + reference);
            }
            if (!item.effective()) {
                return CitationReviewResult.fail("CITATION_REFERENCE_INEFFECTIVE: 正文引用无效证据 E" + reference);
            }
        }
        Set<String> periods = snapshot.evidenceItems().stream()
                .map(FinancialEvidenceItem::reportPeriod)
                .filter(period -> period != null && !period.isBlank() && !"latest".equalsIgnoreCase(period))
                .collect(Collectors.toSet());
        if (periods.size() > 1 && !report.contains("报告期口径") && !report.contains("混用")) {
            return CitationReviewResult.fail("PERIOD_MIXED: 报告期混用但未显式说明");
        }
        for (FinancialMetricResult metric : metrics) {
            if (!"OK".equals(metric.status())) {
                continue;
            }
            if (metric.evidenceRefs().isEmpty()) {
                return CitationReviewResult.fail("METRIC_EVIDENCE_MISSING: " + metric.metricName() + " 缺少证据引用");
            }
            String value = metric.displayValue() == null ? "" : metric.displayValue().toLowerCase(Locale.ROOT);
            if (!value.isBlank() && !body.toLowerCase(Locale.ROOT).contains(value)) {
                return CitationReviewResult.fail("NUMERIC_CONSISTENCY_FAIL: 报告未包含指标值 " + metric.metricName());
            }
            String metricLine = lineContaining(body, metric.displayValue());
            Set<Integer> lineReferences = citationReferences(metricLine);
            Set<Integer> expectedReferences = expectedEvidenceReferences(snapshot.evidenceItems(), metric.evidenceRefs());
            if (lineReferences.stream().noneMatch(expectedReferences::contains)) {
                return CitationReviewResult.fail(
                        "METRIC_BODY_CITATION_MISSING: " + metric.metricName() + " 未就近引用对应原始证据"
                );
            }
        }
        Optional<String> unsupportedDirectionalLine = body.lines()
                .filter(this::containsDirectionalClaim)
                .filter(line -> citationReferences(line).isEmpty())
                .findFirst();
        if (unsupportedDirectionalLine.isPresent()) {
            return CitationReviewResult.fail("DIRECTIONAL_CLAIM_UNSUPPORTED: 方向性判断缺少就近引用");
        }
        Optional<String> periodIssue = interimPeriodIssue(body, snapshot, metrics);
        if (periodIssue.isPresent()) {
            return CitationReviewResult.fail(periodIssue.orElseThrow());
        }
        Optional<String> marketNarrativeIssue = marketNarrativeIssue(body, snapshot);
        if (marketNarrativeIssue.isPresent()) {
            return CitationReviewResult.fail(marketNarrativeIssue.orElseThrow());
        }
        return CitationReviewResult.pass();
    }

    private Map<Integer, FinancialEvidenceItem> indexEvidence(List<FinancialEvidenceItem> evidenceItems) {
        return java.util.stream.IntStream.range(0, evidenceItems.size())
                .boxed()
                .collect(Collectors.toMap(index -> index + 1, evidenceItems::get));
    }

    private Set<Integer> citationReferences(String text) {
        Set<Integer> references = new java.util.LinkedHashSet<>();
        Matcher matcher = CITATION_PATTERN.matcher(text == null ? "" : text);
        while (matcher.find()) {
            references.add(Integer.parseInt(matcher.group(1)));
        }
        return references;
    }

    /** 计算指定指标输入字段可使用的有效证据编号。 */
    private Set<Integer> expectedEvidenceReferences(
            List<FinancialEvidenceItem> evidenceItems,
            List<String> metricNames
    ) {
        Set<String> expectedNames = metricNames.stream().collect(Collectors.toSet());
        return java.util.stream.IntStream.range(0, evidenceItems.size())
                .filter(index -> evidenceItems.get(index).effective())
                .filter(index -> expectedNames.contains(evidenceItems.get(index).metricName()))
                .map(index -> index + 1)
                .boxed()
                .collect(Collectors.toSet());
    }

    private String lineContaining(String body, String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return body.lines().filter(line -> line.contains(value)).findFirst().orElse("");
    }

    private boolean containsDirectionalClaim(String line) {
        return DIRECTIONAL_TOKENS.stream().anyMatch(line::contains);
    }

    /** 检查阶段性 ROE 是否明确报告期并标注未年化。 */
    private Optional<String> interimPeriodIssue(
            String body,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics
    ) {
        boolean hasRoe = metrics.stream().anyMatch(metric -> "ROE".equals(metric.metricName()) && "OK".equals(metric.status()));
        if (!hasRoe) {
            return Optional.empty();
        }
        String period = snapshot.evidenceItems().stream()
                .filter(FinancialEvidenceItem::effective)
                .map(FinancialEvidenceItem::reportPeriod)
                .filter(value -> value != null && value.matches("\\d{8}"))
                .filter(value -> value.endsWith("0331") || value.endsWith("0630") || value.endsWith("0930"))
                .max(String::compareTo)
                .orElse("");
        if (period.isBlank()) {
            return Optional.empty();
        }
        String requiredStage = period.endsWith("0331")
                ? "一季度"
                : period.endsWith("0630") ? "半年度" : "前三季度";
        if (!body.contains(requiredStage) || !body.contains("ROE") || !body.contains("未年化")) {
            return Optional.of("PERIOD_SEMANTIC_INVALID: 阶段性 ROE 必须标注" + requiredStage + "口径且未年化");
        }
        return Optional.empty();
    }

    /** 检查估值口径和跨网页行情拼接等正文语义问题。 */
    private Optional<String> marketNarrativeIssue(String body, FinancialSnapshot snapshot) {
        if (body.contains("静态市盈率")) {
            boolean hasStaticPe = snapshot.evidenceItems().stream()
                    .filter(FinancialEvidenceItem::effective)
                    .anyMatch(item -> "PE_STATIC".equals(item.metricName()));
            if (!hasStaticPe) {
                return Optional.of("VALUATION_SEMANTIC_INVALID: 缺少静态市盈率结构化证据");
            }
        }
        boolean mixedTurnover = body.lines().anyMatch(line -> line.contains("成交额分别")
                || (line.contains("成交额") && line.contains("对应换手率") && line.contains("、")));
        if (mixedTurnover) {
            return Optional.of("MARKET_SNAPSHOT_MIXED: 不得拼接不同网页或时点的成交额与换手率");
        }
        return Optional.empty();
    }

    /** 判断报告数值是否处于允许误差范围。 */
    public boolean withinTolerance(BigDecimal expected, BigDecimal actual) {
        if (expected == null || actual == null) {
            return false;
        }
        BigDecimal diff = expected.subtract(actual).abs();
        if (diff.compareTo(ABSOLUTE_TOLERANCE) <= 0) {
            return true;
        }
        if (BigDecimal.ZERO.compareTo(expected) == 0) {
            return false;
        }
        BigDecimal relative = diff.divide(expected.abs(), MathContext.DECIMAL64);
        return relative.compareTo(RELATIVE_TOLERANCE) <= 0;
    }
}
