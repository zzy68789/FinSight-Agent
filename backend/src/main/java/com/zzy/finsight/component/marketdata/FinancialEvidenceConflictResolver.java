package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceArbitration;
import com.zzy.finsight.domain.stock.FinancialEvidenceArbitrationCandidate;
import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 对同一指标同一报告期的多来源数值执行确定性冲突仲裁。
 */
@Component
public class FinancialEvidenceConflictResolver {
    public static final String POLICY_VERSION = "financial-evidence-arbitration-v2-subject-attribution";
    private static final BigDecimal ABSOLUTE_TOLERANCE = new BigDecimal("0.01");
    private static final BigDecimal RELATIVE_TOLERANCE = new BigDecimal("0.005");

    private final FinancialEvidenceSourcePriorityPolicy sourcePriorityPolicy;

    public FinancialEvidenceConflictResolver() {
        this(new FinancialEvidenceSourcePriorityPolicy());
    }

    public FinancialEvidenceConflictResolver(FinancialEvidenceSourcePriorityPolicy sourcePriorityPolicy) {
        this.sourcePriorityPolicy = sourcePriorityPolicy;
    }

    /** 清除旧的临时仲裁标记，使补充新证据后能够对全部候选来源重新仲裁。 */
    public List<FinancialEvidenceItem> clearPreviousDecisions(List<FinancialEvidenceItem> sourceItems) {
        if (sourceItems == null || sourceItems.isEmpty()) {
            return List.of();
        }
        return sourceItems.stream()
                .map(item -> FinancialEvidenceIssueCodes.arbitration(item.issueCode()) ? withIssue(item, "") : item)
                .toList();
    }

    /** 对已经通过基础语义校验的证据生成唯一输入选择和结构化仲裁结论。 */
    public Resolution resolve(List<FinancialEvidenceItem> sourceItems) {
        List<FinancialEvidenceItem> items = new ArrayList<>(sourceItems == null ? List.of() : sourceItems);
        Map<String, List<IndexedEvidence>> groups = numericGroups(items);
        List<FinancialEvidenceArbitration> arbitrations = new ArrayList<>();
        for (List<IndexedEvidence> group : groups.values()) {
            if (group.size() < 2) {
                continue;
            }
            arbitrations.add(resolveGroup(items, group));
        }
        return new Resolution(List.copyOf(items), List.copyOf(arbitrations));
    }

    private FinancialEvidenceArbitration resolveGroup(
            List<FinancialEvidenceItem> items,
            List<IndexedEvidence> group
    ) {
        List<IndexedEvidence> ranked = group.stream().sorted(ranking()).toList();
        IndexedEvidence selected = ranked.get(0);
        int highestPriority = sourcePriorityPolicy.priority(selected.item());
        List<IndexedEvidence> highestPriorityItems = ranked.stream()
                .filter(candidate -> sourcePriorityPolicy.priority(candidate.item()) == highestPriority)
                .toList();
        boolean highestPriorityConflict = highestPriorityItems.stream()
                .anyMatch(candidate -> !withinTolerance(selected.item().normalizedValue(), candidate.item().normalizedValue()));
        if (highestPriorityConflict) {
            return unresolved(items, ranked, selected.item(), highestPriority);
        }

        boolean allConsistent = ranked.stream()
                .allMatch(candidate -> withinTolerance(selected.item().normalizedValue(), candidate.item().normalizedValue()));
        List<FinancialEvidenceArbitrationCandidate> candidates = new ArrayList<>();
        for (IndexedEvidence candidate : ranked) {
            if (candidate.index() == selected.index()) {
                candidates.add(candidate(candidate.item(), FinancialEvidenceArbitrationCandidate.Decision.SELECTED));
                continue;
            }
            boolean corroborating = withinTolerance(
                    selected.item().normalizedValue(), candidate.item().normalizedValue()
            );
            String issueCode = corroborating
                    ? FinancialEvidenceIssueCodes.SOURCE_CORROBORATING
                    : FinancialEvidenceIssueCodes.SOURCE_CONFLICT_REJECTED;
            items.set(candidate.index(), withIssue(candidate.item(), issueCode));
            candidates.add(candidate(
                    candidate.item(),
                    corroborating
                            ? FinancialEvidenceArbitrationCandidate.Decision.CORROBORATING
                            : FinancialEvidenceArbitrationCandidate.Decision.REJECTED
            ));
        }
        FinancialEvidenceArbitration.Status status = allConsistent
                ? FinancialEvidenceArbitration.Status.CONSISTENT
                : FinancialEvidenceArbitration.Status.RESOLVED;
        String reason = allConsistent
                ? "候选数值处于绝对 0.01 或相对 0.5% 容差内；按来源优先级选择"
                : "候选数值超出容差；唯一最高优先级来源胜出，低优先级冲突值不参与指标计算";
        reason += sourceLabel(selected.item(), highestPriority);
        return arbitration(group.get(0).item(), status, selected.item(), candidates, reason);
    }

    private FinancialEvidenceArbitration unresolved(
            List<FinancialEvidenceItem> items,
            List<IndexedEvidence> ranked,
            FinancialEvidenceItem representative,
            int highestPriority
    ) {
        List<FinancialEvidenceArbitrationCandidate> candidates = new ArrayList<>();
        for (IndexedEvidence candidate : ranked) {
            items.set(candidate.index(), withIssue(candidate.item(), FinancialEvidenceIssueCodes.EVIDENCE_CONFLICT));
            candidates.add(candidate(candidate.item(), FinancialEvidenceArbitrationCandidate.Decision.CONFLICT));
        }
        String reason = "最高来源优先级 " + highestPriority
                + " 存在多个超出绝对 0.01 或相对 0.5% 容差的数值，无法安全选择，必须补充或核对证据";
        return new FinancialEvidenceArbitration(
                representative.metricName(),
                representative.reportPeriod(),
                FinancialEvidenceArbitration.Status.CONFLICT,
                "",
                "",
                null,
                candidates,
                reason,
                POLICY_VERSION + "+" + FinancialEvidenceSourcePriorityPolicy.POLICY_VERSION
        );
    }

    private FinancialEvidenceArbitration arbitration(
            FinancialEvidenceItem representative,
            FinancialEvidenceArbitration.Status status,
            FinancialEvidenceItem selected,
            List<FinancialEvidenceArbitrationCandidate> candidates,
            String reason
    ) {
        return new FinancialEvidenceArbitration(
                representative.metricName(),
                representative.reportPeriod(),
                status,
                selected.sourceType(),
                selected.sourceName(),
                selected.normalizedValue(),
                candidates,
                reason,
                POLICY_VERSION + "+" + FinancialEvidenceSourcePriorityPolicy.POLICY_VERSION
        );
    }

    private Map<String, List<IndexedEvidence>> numericGroups(List<FinancialEvidenceItem> items) {
        Map<String, List<IndexedEvidence>> groups = new LinkedHashMap<>();
        for (int index = 0; index < items.size(); index++) {
            FinancialEvidenceItem item = items.get(index);
            if (!item.effective() || item.normalizedValue() == null || blank(item.metricName())) {
                continue;
            }
            String key = safe(item.subjectCode()) + "|" + safe(item.metricName()) + "|" + safe(item.reportPeriod());
            groups.computeIfAbsent(key, ignored -> new ArrayList<>()).add(new IndexedEvidence(index, item));
        }
        return groups;
    }

    private Comparator<IndexedEvidence> ranking() {
        return Comparator
                .comparingInt((IndexedEvidence candidate) -> sourcePriorityPolicy.priority(candidate.item()))
                .reversed()
                .thenComparing(
                        candidate -> candidate.item().confidence(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(
                        candidate -> candidate.item().asOf(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                )
                .thenComparing(candidate -> safe(candidate.item().sourceType()))
                .thenComparing(candidate -> safe(candidate.item().sourceName()))
                .thenComparingInt(IndexedEvidence::index);
    }

    private FinancialEvidenceArbitrationCandidate candidate(
            FinancialEvidenceItem item,
            FinancialEvidenceArbitrationCandidate.Decision decision
    ) {
        return new FinancialEvidenceArbitrationCandidate(
                item.sourceType(),
                item.sourceName(),
                item.normalizedValue(),
                sourcePriorityPolicy.priority(item),
                item.confidence(),
                decision
        );
    }

    private boolean withinTolerance(BigDecimal left, BigDecimal right) {
        BigDecimal difference = left.subtract(right).abs();
        if (difference.compareTo(ABSOLUTE_TOLERANCE) <= 0) {
            return true;
        }
        BigDecimal baseline = left.abs().max(right.abs());
        return baseline.signum() != 0
                && difference.divide(baseline, 8, RoundingMode.HALF_UP).compareTo(RELATIVE_TOLERANCE) <= 0;
    }

    private String sourceLabel(FinancialEvidenceItem item, int priority) {
        String sourceName = item.sourceName() == null ? "" : item.sourceName().trim();
        return " " + sourceName + "（" + sourcePriorityPolicy.describe(item)
                + "，优先级 " + priority + "）作为确定性输入";
    }

    private FinancialEvidenceItem withIssue(FinancialEvidenceItem item, String issueCode) {
        return new FinancialEvidenceItem(
                item.sourceType(),
                item.sourceName(),
                item.url(),
                item.pageNumber(),
                item.reportPeriod(),
                item.metricName(),
                item.rawValue(),
                item.normalizedValue(),
                item.excerpt(),
                item.confidence(),
                item.asOf(),
                issueCode,
                item.subjectCode(),
                item.comparisonSnapshotId()
        );
    }

    private boolean blank(String value) {
        return value == null || value.isBlank();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }

    /**
     * 表示证据仲裁后的证据列表和结构化结论。
     *
     * @param evidenceItems 写入仲裁问题码后的证据列表。
     * @param arbitrations 同指标同报告期的仲裁结论。
     */
    public record Resolution(
            List<FinancialEvidenceItem> evidenceItems,
            List<FinancialEvidenceArbitration> arbitrations
    ) {
    }

    /**
     * 保存候选证据在原始列表中的位置。
     *
     * @param index 原始证据索引。
     * @param item 候选证据。
     */
    private record IndexedEvidence(int index, FinancialEvidenceItem item) {
    }
}
