package com.zzy.finsight.agent.tool;

import com.zzy.finsight.component.analysis.FinancialMetricEngine;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 使用 Java BigDecimal 计算当前证据快照的金融指标。
 */
@Component
public class CalculateFinancialMetricsTool implements ResearchTool {
    private final FinancialMetricEngine metricEngine;

    public CalculateFinancialMetricsTool(FinancialMetricEngine metricEngine) {
        this.metricEngine = metricEngine;
    }

    @Override
    public String name() {
        return "calculate_financial_metrics";
    }

    @Override
    public String description() {
        return "基于当前证据快照用 Java BigDecimal 计算财务指标，模型不得自行计算关键数字。";
    }

    @Override
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        if (context.state().getSnapshot() == null) {
            return ToolResult.failure("当前没有可计算的金融快照", "SNAPSHOT_REQUIRED", false);
        }
        List<FinancialMetricResult> metrics = metricEngine.compute(context.state().getSnapshot());
        context.state().setMetrics(metrics);
        return ToolResult.success(
                "已确定性计算 %d 个金融指标".formatted(metrics.size()),
                Map.of("metrics", metrics)
        );
    }
}
