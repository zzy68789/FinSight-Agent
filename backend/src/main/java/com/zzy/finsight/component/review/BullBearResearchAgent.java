package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialRiskDimension;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.ResearchClaim;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 以两个独立研究角色对同一确定性指标集进行正反论证，并强制绑定原始证据编号。
 */
@Component
public class BullBearResearchAgent {
    public static final String POLICY_VERSION = "bull-bear-research-v1-evidence-bound";
    private static final int MAX_CLAIMS_PER_SIDE = 3;

    /** 基于已计算指标和风险维度生成多空对照，不让模型重新计算金融数字。 */
    public BullBearResearchResult analyze(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment
    ) {
        if (snapshot == null || metrics == null) {
            return BullBearResearchResult.empty();
        }
        List<ResearchClaim> bullCases = new ArrayList<>();
        List<ResearchClaim> bearCases = new ArrayList<>();
        for (FinancialMetricResult metric : metrics) {
            if (!"OK".equals(metric.status()) || metric.value() == null) {
                addMissingClaim(bearCases, metric);
                continue;
            }
            Signal signal = classify(metric);
            if (signal == Signal.BULL && bullCases.size() < MAX_CLAIMS_PER_SIDE) {
                bullCases.add(metricClaim("BULL", "正向指标", metric, snapshot,
                        "当前快照中“%s”为 %s，构成可继续验证的正向条件。"));
            } else if (signal == Signal.BEAR && bearCases.size() < MAX_CLAIMS_PER_SIDE) {
                bearCases.add(metricClaim("BEAR", "压力指标", metric, snapshot,
                        "当前快照中“%s”为 %s，构成需要优先复核的风险条件。"));
            }
        }
        appendRiskClaims(bearCases, riskAssessment, snapshot);
        ensureSideHasClaim(bullCases, "BULL", snapshot,
                "正向证据待补齐", "现有确定性指标尚不足以形成稳定的正向论据。", "LOW");
        ensureSideHasClaim(bearCases, "BEAR", snapshot,
                "反向证据待补齐", "现有确定性指标尚不足以形成稳定的反向论据，仍需关注数据缺口。", "LOW");
        String synthesis = "多空角色只对同一证据快照做条件化解释；应结合证据缺口和风险评分复核，不构成投资建议。";
        return new BullBearResearchResult(bullCases, bearCases, synthesis, "COMPLETED");
    }

    private Signal classify(FinancialMetricResult metric) {
        BigDecimal value = metric.value();
        return switch (metric.metricName()) {
            case "营收同比", "毛利率", "净利率", "ROE", "ETF涨跌幅" -> value.signum() > 0
                    ? Signal.BULL : value.signum() < 0 ? Signal.BEAR : Signal.NEUTRAL;
            case "经营现金流 / 净利润" -> value.compareTo(BigDecimal.ONE) >= 0 ? Signal.BULL : Signal.BEAR;
            case "资产负债率" -> value.compareTo(new BigDecimal("60")) >= 0 ? Signal.BEAR : Signal.NEUTRAL;
            case "ETF折溢价率" -> value.abs().compareTo(BigDecimal.ONE) > 0 ? Signal.BEAR : Signal.NEUTRAL;
            default -> Signal.NEUTRAL;
        };
    }

    private ResearchClaim metricClaim(
            String side,
            String title,
            FinancialMetricResult metric,
            FinancialSnapshot snapshot,
            String template
    ) {
        List<String> refs = evidenceRefs(snapshot, metric.evidenceRefs());
        String strength = refs.size() >= 2 ? "HIGH" : refs.isEmpty() ? "LOW" : "MEDIUM";
        return new ResearchClaim(side, title,
                template.formatted(metric.metricName(), metric.displayValue()), refs, strength);
    }

    private void addMissingClaim(List<ResearchClaim> bearCases, FinancialMetricResult metric) {
        if (bearCases.size() >= MAX_CLAIMS_PER_SIDE || "OK".equals(metric.status())) {
            return;
        }
        bearCases.add(new ResearchClaim(
                "BEAR",
                "数据缺口",
                "“%s”未能计算：%s。".formatted(metric.metricName(), metric.reason()),
                List.of(),
                "LOW"
        ));
    }

    private void appendRiskClaims(
            List<ResearchClaim> bearCases,
            FinancialRiskAssessment riskAssessment,
            FinancialSnapshot snapshot
    ) {
        if (riskAssessment == null) {
            return;
        }
        for (FinancialRiskDimension dimension : riskAssessment.dimensions()) {
            if (bearCases.size() >= MAX_CLAIMS_PER_SIDE || dimension.score() < 6) {
                continue;
            }
            List<String> metricRefs = dimension.evidenceRef() == null
                    ? List.of()
                    : java.util.Arrays.stream(dimension.evidenceRef().split("[,，]"))
                            .map(String::trim)
                            .filter(value -> !value.isBlank())
                            .toList();
            bearCases.add(new ResearchClaim(
                    "BEAR",
                    dimension.name(),
                    dimension.reason(),
                    evidenceRefs(snapshot, metricRefs),
                    dimension.score() >= 8 ? "HIGH" : "MEDIUM"
            ));
        }
    }

    private List<String> evidenceRefs(FinancialSnapshot snapshot, List<String> metricNames) {
        Set<String> names = new LinkedHashSet<>(metricNames == null ? List.of() : metricNames);
        List<String> refs = new ArrayList<>();
        for (int index = 0; index < snapshot.evidenceItems().size(); index++) {
            FinancialEvidenceItem item = snapshot.evidenceItems().get(index);
            if (item.effective() && names.contains(item.metricName())) {
                refs.add("E" + (index + 1));
            }
        }
        return List.copyOf(refs);
    }

    private void ensureSideHasClaim(
            List<ResearchClaim> claims,
            String side,
            FinancialSnapshot snapshot,
            String title,
            String statement,
            String strength
    ) {
        if (!claims.isEmpty()) {
            return;
        }
        List<String> refs = new ArrayList<>();
        for (int index = 0; index < snapshot.evidenceItems().size(); index++) {
            if (snapshot.evidenceItems().get(index).effective()) {
                refs.add("E" + (index + 1));
                break;
            }
        }
        claims.add(new ResearchClaim(side, title, statement, refs, strength));
    }

    private enum Signal {
        BULL,
        BEAR,
        NEUTRAL
    }
}
