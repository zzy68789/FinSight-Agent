package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.review.BullBearCaseBuilder;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将确定性多空条件分析包装为研究工具。
 */
@Component
public class BuildBullBearCasesTool implements ResearchTool<NoToolArguments> {
    private final BullBearCaseBuilder delegate;

    public BuildBullBearCasesTool(BullBearCaseBuilder delegate) {
        this.delegate = delegate;
    }

    @Override
    public String name() {
        return "build_bull_bear_cases";
    }

    @Override
    public String description() {
        return "基于同一证据和指标生成正反条件化论据；只做研究解释，不给交易指令。";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.readOnly(
                "build_bull_bear_cases",
                "基于同一证据和指标生成正反条件化论据；只做研究解释，不给交易指令。",
                Map.of("research", "BullBearResearchResult", "policyVersion", "string")
        );
    }

    @Override
    public NoToolArguments decode(Map<String, Object> arguments, ObjectMapper objectMapper) {
        return ToolDecoders.noArguments(arguments);
    }

    @Override
    public ToolResult execute(ToolContext context, NoToolArguments arguments) {
        if (context.snapshot() == null || context.metrics().isEmpty()) {
            return ToolResult.failure("多空条件分析需要快照和指标", "METRICS_REQUIRED", false);
        }
        BullBearResearchResult result = delegate.analyze(
                context.snapshot(),
                context.metrics(),
                context.riskAssessment()
        );
        return ToolResult.success(
                "已形成 %d 条正向条件和 %d 条风险条件".formatted(
                        result.bullCases().size(), result.bearCases().size()
                ),
                new ToolPayload.BullBear(result, BullBearCaseBuilder.POLICY_VERSION)
        );
    }
}
