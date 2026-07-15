package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialRiskDimension;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/**
 * 根据指标和证据计算多维风险评分。
 */
@Component
public class FinancialRiskScorer {
    private static final int FUNDAMENTAL_WEIGHT = 30;
    private static final int TECHNICAL_WEIGHT = 25;
    private static final int SENTIMENT_WEIGHT = 20;
    private static final int NEWS_WEIGHT = 15;
    private static final int MARKET_WEIGHT = 10;

    /** 汇总指标和证据，生成五维风险评估。 */
    public FinancialRiskAssessment assess(List<FinancialMetricResult> metrics, List<FinancialEvidenceItem> evidenceItems) {
        List<FinancialRiskDimension> dimensions = List.of(
                fundamentalRisk(metrics, evidenceItems),
                evidenceRisk("技术面风险", TECHNICAL_WEIGHT, evidenceItems, "TECHNICAL_SIGNAL", "缺少技术指标证据，按中性偏谨慎处理。"),
                evidenceRisk("情绪面风险", SENTIMENT_WEIGHT, evidenceItems, "SENTIMENT_SIGNAL", "缺少情绪面证据，按中性偏谨慎处理。"),
                evidenceRisk("消息面风险", NEWS_WEIGHT, evidenceItems, "NEWS_SUMMARY", "缺少新闻摘要证据，按中性偏谨慎处理。"),
                evidenceRisk("市场环境风险", MARKET_WEIGHT, evidenceItems, "MARKET_REGIME", "缺少市场环境证据，按中性偏谨慎处理。")
        );
        BigDecimal finalScore = dimensions.stream()
                .map(dimension -> BigDecimal.valueOf(dimension.score())
                        .multiply(BigDecimal.valueOf(dimension.weight()))
                        .divide(BigDecimal.valueOf(100), 6, RoundingMode.HALF_UP))
                .reduce(BigDecimal.ZERO, BigDecimal::add)
                .setScale(2, RoundingMode.HALF_UP);
        List<String> warnings = dimensions.stream()
                .filter(dimension -> dimension.reason().contains("缺少"))
                .map(dimension -> dimension.name() + "：" + dimension.reason())
                .toList();
        return new FinancialRiskAssessment(finalScore, riskLevel(finalScore), dimensions, warnings);
    }

    private FinancialRiskDimension fundamentalRisk(
            List<FinancialMetricResult> metrics,
            List<FinancialEvidenceItem> evidenceItems
    ) {
        Optional<BigDecimal> debtRatio = metricValue(metrics, "资产负债率");
        Optional<BigDecimal> roe = metricValue(metrics, "ROE");
        Optional<BigDecimal> cashFlowRatio = metricValue(metrics, "经营现金流 / 净利润");
        if (debtRatio.isEmpty() && roe.isEmpty() && cashFlowRatio.isEmpty()) {
            return new FinancialRiskDimension("基本面风险", 6, FUNDAMENTAL_WEIGHT, "缺少核心财务指标，按中性偏谨慎处理。", "MISSING_INPUT");
        }
        int score = 5;
        List<String> reasons = new ArrayList<>();
        if (debtRatio.isPresent()) {
            BigDecimal value = debtRatio.get();
            if (value.compareTo(new BigDecimal("60")) > 0) {
                score += 3;
                reasons.add("资产负债率高于60%");
            } else if (value.compareTo(new BigDecimal("40")) > 0) {
                score += 1;
                reasons.add("资产负债率处于中位");
            } else {
                score -= 1;
                reasons.add("资产负债率低于40%");
            }
        }
        if (roe.isPresent() && interimFinancialPeriod(evidenceItems)) {
            reasons.add("阶段性ROE未年化，不与全年阈值比较");
        } else if (roe.isPresent()) {
            BigDecimal value = roe.get();
            if (value.compareTo(new BigDecimal("15")) >= 0) {
                score -= 1;
                reasons.add("ROE不低于15%");
            } else if (value.compareTo(BigDecimal.ZERO) < 0) {
                score += 3;
                reasons.add("ROE为负");
            }
        }
        if (cashFlowRatio.isPresent()) {
            BigDecimal value = cashFlowRatio.get();
            if (value.compareTo(BigDecimal.ONE) >= 0) {
                score -= 1;
                reasons.add("经营现金流覆盖净利润");
            } else if (value.compareTo(BigDecimal.ZERO) < 0) {
                score += 2;
                reasons.add("经营现金流为负");
            }
        }
        int boundedScore = Math.max(1, Math.min(10, score));
        return new FinancialRiskDimension(
                "基本面风险",
                boundedScore,
                FUNDAMENTAL_WEIGHT,
                reasons.isEmpty() ? "已有部分财务指标，但信号有限。" : String.join("；", reasons),
                "ROE,资产负债率,经营现金流 / 净利润"
        );
    }

    /** 判断当前结构化财务证据是否采用未覆盖完整年度的阶段性口径。 */
    private boolean interimFinancialPeriod(List<FinancialEvidenceItem> evidenceItems) {
        return evidenceItems.stream()
                .filter(FinancialEvidenceItem::effective)
                .filter(item -> List.of(
                        FinancialMetricInputNames.OPERATING_REVENUE,
                        FinancialMetricInputNames.OPERATING_COST,
                        FinancialMetricInputNames.NET_PROFIT,
                        FinancialMetricInputNames.TOTAL_ASSETS,
                        FinancialMetricInputNames.TOTAL_LIABILITIES,
                        FinancialMetricInputNames.OPERATING_CASH_FLOW
                ).contains(item.metricName()))
                .map(FinancialEvidenceItem::reportPeriod)
                .filter(period -> period != null && period.matches("\\d{8}"))
                .max(String::compareTo)
                .map(period -> period.endsWith("0331") || period.endsWith("0630") || period.endsWith("0930"))
                .orElse(false);
    }

    private FinancialRiskDimension evidenceRisk(
            String name,
            int weight,
            List<FinancialEvidenceItem> evidenceItems,
            String metricName,
            String missingReason
    ) {
        Optional<FinancialEvidenceItem> evidence = evidenceItems.stream()
                .filter(FinancialEvidenceItem::effective)
                .filter(item -> metricName.equals(item.metricName()))
                .findFirst();
        if (evidence.isEmpty()) {
            return new FinancialRiskDimension(name, 6, weight, missingReason, metricName);
        }
        String text = evidence.get().excerpt() == null ? "" : evidence.get().excerpt().toLowerCase(Locale.ROOT);
        if (containsAny(text, "利好", "平稳", "中性", "景气", "震荡", "bullish", "neutral")) {
            return new FinancialRiskDimension(name, 4, weight, "证据片段显示中性或偏正面信号。", metricName);
        }
        if (containsAny(text, "利空", "下跌", "悲观", "流出", "监管", "风险", "bearish")) {
            return new FinancialRiskDimension(name, 7, weight, "证据片段包含偏负面风险信号。", metricName);
        }
        return new FinancialRiskDimension(name, 5, weight, "已有证据但方向不强，按中性处理。", metricName);
    }

    private boolean containsAny(String text, String... tokens) {
        for (String token : tokens) {
            if (text.contains(token)) {
                return true;
            }
        }
        return false;
    }

    private Optional<BigDecimal> metricValue(List<FinancialMetricResult> metrics, String metricName) {
        return metrics.stream()
                .filter(metric -> metricName.equals(metric.metricName()))
                .filter(metric -> "OK".equals(metric.status()))
                .map(FinancialMetricResult::value)
                .findFirst();
    }

    private String riskLevel(BigDecimal finalScore) {
        if (finalScore.compareTo(new BigDecimal("3.00")) <= 0) {
            return "低风险";
        }
        if (finalScore.compareTo(new BigDecimal("6.00")) <= 0) {
            return "中等风险";
        }
        if (finalScore.compareTo(new BigDecimal("8.00")) <= 0) {
            return "高风险";
        }
        return "极高风险";
    }
}
