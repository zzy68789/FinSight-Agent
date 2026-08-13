package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.ComparisonSecuritySnapshot;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 使用 Java BigDecimal 为每个可比证券计算口径明确的比较指标。
 */
@Component
public class ComparisonMetricEngine {
    public static final String FORMULA_VERSION = "comparison-metric-v1";

    /** 根据可比快照索引和证券归属证据生成独立指标。 */
    public List<FinancialMetricResult> compute(FinancialSnapshot snapshot) {
        if (snapshot == null || snapshot.comparisonSnapshots().isEmpty()) {
            return List.of();
        }
        List<FinancialMetricResult> results = new ArrayList<>();
        for (ComparisonSecuritySnapshot comparison : snapshot.comparisonSnapshots()) {
            List<FinancialEvidenceItem> evidence = snapshot.evidenceItems().stream()
                    .filter(item -> comparison.subject().fullCode().equalsIgnoreCase(item.subjectCode()))
                    .filter(FinancialEvidenceItem::effective)
                    .toList();
            if (comparison.subject().assetType() == StockAssetType.ETF) {
                results.add(direct(comparison, evidence, FinancialMetricInputNames.ETF_CLOSE, "收盘价", ""));
                results.add(direct(comparison, evidence, FinancialMetricInputNames.ETF_UNIT_NAV, "单位净值", ""));
                results.add(direct(
                        comparison,
                        evidence,
                        FinancialMetricInputNames.ETF_PREMIUM_DISCOUNT_RATE,
                        "折溢价率",
                        "%"
                ));
            } else {
                results.add(direct(comparison, evidence, "PE_TTM", "市盈率TTM", "倍"));
                results.add(direct(comparison, evidence, "PB", "市净率", "倍"));
                results.add(revenueGrowth(comparison, evidence));
            }
        }
        return List.copyOf(results);
    }

    private FinancialMetricResult direct(
            ComparisonSecuritySnapshot comparison,
            List<FinancialEvidenceItem> evidence,
            String inputName,
            String displayName,
            String suffix
    ) {
        FinancialEvidenceItem item = latest(evidence, inputName);
        String metricName = comparison.subject().fullCode() + " " + displayName;
        if (item == null || item.normalizedValue() == null) {
            return missing(metricName, "缺少 " + inputName, List.of(inputName));
        }
        BigDecimal value = item.normalizedValue().setScale(2, RoundingMode.HALF_UP);
        return new FinancialMetricResult(
                metricName,
                value,
                value.stripTrailingZeros().toPlainString() + suffix,
                "来源直接披露 " + inputName,
                FORMULA_VERSION,
                "OK",
                "",
                List.of(inputName)
        );
    }

    private FinancialMetricResult revenueGrowth(
            ComparisonSecuritySnapshot comparison,
            List<FinancialEvidenceItem> evidence
    ) {
        String metricName = comparison.subject().fullCode() + " 营收同比";
        FinancialEvidenceItem current = latest(evidence, FinancialMetricInputNames.OPERATING_REVENUE);
        FinancialEvidenceItem prior = latest(evidence, FinancialMetricInputNames.OPERATING_REVENUE_PRIOR);
        List<String> refs = List.of(
                FinancialMetricInputNames.OPERATING_REVENUE,
                FinancialMetricInputNames.OPERATING_REVENUE_PRIOR
        );
        if (current == null || prior == null
                || current.normalizedValue() == null || prior.normalizedValue() == null) {
            return missing(metricName, "缺少当期或上年同期营业收入", refs);
        }
        if (BigDecimal.ZERO.compareTo(prior.normalizedValue()) == 0) {
            return missing(metricName, "上年同期营业收入为 0", refs);
        }
        BigDecimal value = current.normalizedValue().subtract(prior.normalizedValue())
                .divide(prior.normalizedValue(), 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(2, RoundingMode.HALF_UP);
        return new FinancialMetricResult(
                metricName,
                value,
                value.toPlainString() + "%",
                "(当期营业收入-上年同期营业收入)/上年同期营业收入×100%",
                FORMULA_VERSION,
                "OK",
                "",
                refs
        );
    }

    private FinancialMetricResult missing(String metricName, String reason, List<String> refs) {
        return new FinancialMetricResult(
                metricName,
                null,
                "DATA_MISSING",
                "确定性可比指标",
                FORMULA_VERSION,
                "DATA_MISSING",
                "DATA_MISSING：" + reason,
                refs
        );
    }

    private FinancialEvidenceItem latest(List<FinancialEvidenceItem> evidence, String metricName) {
        return evidence.stream()
                .filter(item -> metricName.equals(item.metricName()))
                .filter(item -> item.normalizedValue() != null)
                .max(Comparator
                        .comparing((FinancialEvidenceItem item) -> safe(item.reportPeriod()))
                        .thenComparing(item -> item.confidence(), Comparator.nullsFirst(Comparator.naturalOrder()))
                        .thenComparing(item -> item.asOf(), Comparator.nullsFirst(Comparator.naturalOrder())))
                .orElse(null);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
