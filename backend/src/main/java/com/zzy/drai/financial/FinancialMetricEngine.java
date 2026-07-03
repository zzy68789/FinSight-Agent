package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class FinancialMetricEngine {
    private static final int SCALE = 2;

    public List<FinancialMetricResult> compute(FinancialSnapshot snapshot) {
        Map<String, FinancialEvidenceItem> inputs = indexInputs(snapshot);
        return List.of(
                revenueYoY(inputs),
                grossMargin(inputs),
                netMargin(inputs),
                roe(inputs),
                debtRatio(inputs),
                cashFlowToNetProfit(inputs)
        );
    }

    private FinancialMetricResult revenueYoY(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                "营收同比",
                List.of(FinancialMetricInputNames.OPERATING_REVENUE, FinancialMetricInputNames.OPERATING_REVENUE_PRIOR),
                inputs,
                "（本期营业收入 - 上年同期营业收入）/ 上年同期营业收入",
                true,
                values -> values.get(0).subtract(values.get(1)).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult grossMargin(Map<String, FinancialEvidenceItem> inputs) {
        if (inputs.containsKey(FinancialMetricInputNames.GROSS_PROFIT)) {
            return ratio(
                    "毛利率",
                    List.of(FinancialMetricInputNames.GROSS_PROFIT, FinancialMetricInputNames.OPERATING_REVENUE),
                    inputs,
                    "毛利 / 营业收入",
                    true,
                    values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
            );
        }
        return ratio(
                "毛利率",
                List.of(FinancialMetricInputNames.OPERATING_REVENUE, FinancialMetricInputNames.OPERATING_COST),
                inputs,
                "（营业收入 - 营业成本）/ 营业收入",
                true,
                values -> values.get(0).subtract(values.get(1)).divide(values.get(0), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult netMargin(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                "净利率",
                List.of(FinancialMetricInputNames.NET_PROFIT, FinancialMetricInputNames.OPERATING_REVENUE),
                inputs,
                "净利润 / 营业收入",
                true,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult roe(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                "ROE",
                List.of(FinancialMetricInputNames.NET_PROFIT, FinancialMetricInputNames.AVERAGE_EQUITY),
                inputs,
                "净利润 / 平均净资产",
                true,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult debtRatio(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                "资产负债率",
                List.of(FinancialMetricInputNames.TOTAL_LIABILITIES, FinancialMetricInputNames.TOTAL_ASSETS),
                inputs,
                "总负债 / 总资产",
                true,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult cashFlowToNetProfit(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                "经营现金流 / 净利润",
                List.of(FinancialMetricInputNames.OPERATING_CASH_FLOW, FinancialMetricInputNames.NET_PROFIT),
                inputs,
                "经营活动现金流量净额 / 净利润",
                false,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult ratio(
            String name,
            List<String> required,
            Map<String, FinancialEvidenceItem> inputs,
            String formula,
            boolean percent,
            RatioFormula ratioFormula
    ) {
        List<String> missing = required.stream()
                .filter(key -> !inputs.containsKey(key) || inputs.get(key).normalizedValue() == null)
                .toList();
        if (!missing.isEmpty()) {
            return new FinancialMetricResult(name, null, "数据缺失", formula, "MISSING_INPUT", "缺少输入：" + String.join(", ", missing), required);
        }
        List<BigDecimal> values = required.stream().map(key -> inputs.get(key).normalizedValue()).toList();
        if (values.size() > 1 && BigDecimal.ZERO.compareTo(values.get(1)) == 0) {
            return new FinancialMetricResult(name, null, "数据缺失", formula, "INVALID_DENOMINATOR", "分母为 0，无法计算", required);
        }
        BigDecimal value = ratioFormula.compute(values);
        BigDecimal displayValue = percent ? value.multiply(BigDecimal.valueOf(100)) : value;
        displayValue = displayValue.setScale(SCALE, RoundingMode.HALF_UP);
        return new FinancialMetricResult(
                name,
                displayValue,
                percent ? displayValue.toPlainString() + "%" : displayValue.toPlainString(),
                formula,
                "OK",
                "",
                required
        );
    }

    private Map<String, FinancialEvidenceItem> indexInputs(FinancialSnapshot snapshot) {
        Map<String, FinancialEvidenceItem> inputs = new LinkedHashMap<>();
        for (FinancialEvidenceItem item : snapshot.evidenceItems()) {
            if (item.metricName() == null || item.normalizedValue() == null || !item.effective()) {
                continue;
            }
            inputs.putIfAbsent(item.metricName(), item);
        }
        return inputs;
    }

    @FunctionalInterface
    private interface RatioFormula {
        BigDecimal compute(List<BigDecimal> values);
    }
}
