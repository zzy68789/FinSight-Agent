package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.MathContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class CitationReviewer {
    private static final BigDecimal ABSOLUTE_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.005");

    public CitationReviewResult review(String report, FinancialSnapshot snapshot, List<FinancialMetricResult> metrics) {
        long effectiveEvidenceCount = snapshot.evidenceItems().stream().filter(FinancialEvidenceItem::effective).count();
        if (effectiveEvidenceCount < 3) {
            return CitationReviewResult.fail("EVIDENCE_INSUFFICIENT: 有效证据少于 3 条");
        }
        if (report == null || !report.contains("引用与数据快照")) {
            return CitationReviewResult.fail("CITATION_SECTION_MISSING: 报告缺少引用与数据快照章节");
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
            if (!value.isBlank() && !report.toLowerCase(Locale.ROOT).contains(value)) {
                return CitationReviewResult.fail("NUMERIC_CONSISTENCY_FAIL: 报告未包含指标值 " + metric.metricName());
            }
        }
        return CitationReviewResult.pass();
    }

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
