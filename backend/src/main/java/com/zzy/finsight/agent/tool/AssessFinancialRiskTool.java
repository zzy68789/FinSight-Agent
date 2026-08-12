package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 基于当前指标和证据计算结构化研究风险。
 */
@Component
public class AssessFinancialRiskTool implements ResearchTool<NoToolArguments> {
    private final FinancialRiskScorer riskScorer;

    public AssessFinancialRiskTool(FinancialRiskScorer riskScorer) {
        this.riskScorer = riskScorer;
    }

    @Override
    public String name() {
        return "assess_financial_risk";
    }

    @Override
    public String description() {
        return "根据已计算指标和原始证据生成研究风险维度，不输出买卖或仓位建议。";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.readOnly(
                "assess_financial_risk",
                "根据已计算指标和原始证据生成研究风险维度，不输出买卖或仓位建议。",
                Map.of("riskAssessment", "FinancialRiskAssessment")
        );
    }

    @Override
    public NoToolArguments decode(Map<String, Object> arguments, ObjectMapper objectMapper) {
        return ToolDecoders.noArguments(arguments);
    }

    @Override
    public ToolResult execute(ToolContext context, NoToolArguments arguments) {
        if (context.state().getSnapshot() == null || context.state().getMetrics().isEmpty()) {
            return ToolResult.failure("风险评估需要快照和已计算指标", "METRICS_REQUIRED", false);
        }
        FinancialRiskAssessment assessment = riskScorer.assess(
                context.state().getMetrics(), context.state().getSnapshot().evidenceItems()
        );
        context.state().setRiskAssessment(assessment);
        return ToolResult.success(
                "风险等级 %s，综合分 %s".formatted(assessment.riskLevel(), assessment.finalScore()),
                Map.of("riskAssessment", assessment)
        );
    }
}
