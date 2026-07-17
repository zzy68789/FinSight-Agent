package com.zzy.finsight.component.analysis;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.metric.MetricDefinition;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import com.zzy.finsight.domain.stock.metric.MetricDefinitionCatalog;


import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 使用确定性公式计算股票和 ETF 指标。
 */
@Component
public class FinancialMetricEngine {
    private static final int SCALE = 2;
    private final MetricDefinitionCatalog catalog;

    public FinancialMetricEngine() {
        this(new MetricDefinitionCatalog());
    }

    public FinancialMetricEngine(MetricDefinitionCatalog catalog) {
        this.catalog = catalog;
    }

    /** 依据固定公式计算可复核的金融指标。 */
    public List<FinancialMetricResult> compute(FinancialSnapshot snapshot) {
        Map<String, FinancialEvidenceItem> inputs = indexInputs(snapshot);
        if (snapshot != null && snapshot.subject() != null && snapshot.subject().isEtf()) {
            return List.of(
                    etfMetric("etf_close", inputs),
                    etfMetric("etf_pct_change", inputs),
                    etfMetric("etf_amount", inputs),
                    etfMetric("etf_unit_nav", inputs),
                    etfMetric("etf_premium_discount", inputs)
            );
        }
        return List.of(
                revenueYoY(inputs),
                grossMargin(inputs),
                netMargin(inputs),
                roe(inputs),
                debtRatio(inputs),
                cashFlowToNetProfit(inputs)
        );
    }

    private FinancialMetricResult etfMetric(String code, Map<String, FinancialEvidenceItem> inputs) {
        MetricDefinition definition = catalog.get(code);
        String name = definition.displayName();
        String inputName = definition.dependencies().get(0);
        FinancialEvidenceItem item = inputs.get(inputName);
        if (item == null || item.normalizedValue() == null) {
            return result(definition, null, "数据缺失", "MISSING_INPUT", "缺少输入：" + inputName);
        }
        BigDecimal value = item.normalizedValue();
        String display = value.setScale("ETF涨跌幅".equals(name) ? 2 : 3, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        if ("ETF涨跌幅".equals(name) || "ETF折溢价率".equals(name)) {
            display = display + "%";
        }
        return result(definition, value, display, "OK", "");
    }

    private FinancialMetricResult revenueYoY(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                catalog.get("revenue_yoy"),
                inputs,
                true,
                values -> values.get(0).subtract(values.get(1)).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult grossMargin(Map<String, FinancialEvidenceItem> inputs) {
        if (inputs.containsKey(FinancialMetricInputNames.GROSS_PROFIT)) {
            return ratio(
                    catalog.get("gross_margin_profit"),
                    inputs,
                    true,
                    values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
            );
        }
        return ratio(
                catalog.get("gross_margin_cost"),
                inputs,
                true,
                values -> values.get(0).subtract(values.get(1)).divide(values.get(0), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult netMargin(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                catalog.get("net_margin"),
                inputs,
                true,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    /** 优先使用年初和期末权益计算平均净资产，并兼容直接披露平均净资产的上传报告。 */
    private FinancialMetricResult roe(Map<String, FinancialEvidenceItem> inputs) {
        MetricDefinition definition = catalog.get("roe");
        List<String> required = definition.dependencies();
        boolean hasBeginningEquity = hasValue(inputs, FinancialMetricInputNames.BEGINNING_EQUITY);
        boolean hasEndingEquity = hasValue(inputs, FinancialMetricInputNames.ENDING_EQUITY);
        if ((!hasBeginningEquity || !hasEndingEquity) && hasValue(inputs, FinancialMetricInputNames.AVERAGE_EQUITY)) {
            return ratio(
                    catalog.get("roe_average"),
                    inputs,
                    true,
                    values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
            );
        }
        List<String> missing = required.stream().filter(key -> !hasValue(inputs, key)).toList();
        if (!missing.isEmpty()) {
            return result(definition, null, "数据缺失", "MISSING_INPUT", "缺少输入：" + String.join(", ", missing));
        }

        BigDecimal netProfit = inputs.get(FinancialMetricInputNames.NET_PROFIT).normalizedValue();
        BigDecimal beginningEquity = inputs.get(FinancialMetricInputNames.BEGINNING_EQUITY).normalizedValue();
        BigDecimal endingEquity = inputs.get(FinancialMetricInputNames.ENDING_EQUITY).normalizedValue();
        BigDecimal averageEquity = beginningEquity.add(endingEquity)
                .divide(BigDecimal.valueOf(2), 8, RoundingMode.HALF_UP);
        if (BigDecimal.ZERO.compareTo(averageEquity) == 0) {
            return result(definition, null, "数据缺失", "INVALID_DENOMINATOR", "平均净资产为 0，无法计算");
        }
        BigDecimal value = netProfit.divide(averageEquity, 8, RoundingMode.HALF_UP)
                .multiply(BigDecimal.valueOf(100))
                .setScale(SCALE, RoundingMode.HALF_UP);
        return result(definition, value, value.toPlainString() + "%", "OK", "");
    }

    private boolean hasValue(Map<String, FinancialEvidenceItem> inputs, String key) {
        return inputs.containsKey(key) && inputs.get(key).normalizedValue() != null;
    }

    private FinancialMetricResult debtRatio(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                catalog.get("debt_ratio"),
                inputs,
                true,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult cashFlowToNetProfit(Map<String, FinancialEvidenceItem> inputs) {
        return ratio(
                catalog.get("cashflow_profit"),
                inputs,
                false,
                values -> values.get(0).divide(values.get(1), 8, RoundingMode.HALF_UP)
        );
    }

    private FinancialMetricResult ratio(
            MetricDefinition definition,
            Map<String, FinancialEvidenceItem> inputs,
            boolean percent,
            RatioFormula ratioFormula
    ) {
        List<String> required = definition.dependencies();
        List<String> missing = required.stream()
                .filter(key -> !inputs.containsKey(key) || inputs.get(key).normalizedValue() == null)
                .toList();
        if (!missing.isEmpty()) {
            return result(definition, null, "数据缺失", "MISSING_INPUT", "缺少输入：" + String.join(", ", missing));
        }
        List<BigDecimal> values = required.stream().map(key -> inputs.get(key).normalizedValue()).toList();
        if (values.size() > 1 && BigDecimal.ZERO.compareTo(values.get(1)) == 0) {
            return result(definition, null, "数据缺失", "INVALID_DENOMINATOR", "分母为 0，无法计算");
        }
        BigDecimal value = ratioFormula.compute(values);
        BigDecimal displayValue = percent ? value.multiply(BigDecimal.valueOf(100)) : value;
        displayValue = displayValue.setScale(SCALE, RoundingMode.HALF_UP);
        return result(
                definition,
                displayValue,
                percent ? displayValue.toPlainString() + "%" : displayValue.toPlainString(),
                "OK",
                ""
        );
    }

    private FinancialMetricResult result(
            MetricDefinition definition,
            BigDecimal value,
            String displayValue,
            String status,
            String reason
    ) {
        return new FinancialMetricResult(
                definition.displayName(),
                value,
                displayValue,
                definition.formula(),
                definition.formulaVersion(),
                status,
                reason,
                definition.dependencies()
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
