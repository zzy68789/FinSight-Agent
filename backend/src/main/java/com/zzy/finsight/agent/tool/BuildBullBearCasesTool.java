package com.zzy.finsight.agent.tool;

import com.zzy.finsight.component.review.BullBearCaseBuilder;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 将确定性多空条件分析包装为研究工具。
 */
@Component
public class BuildBullBearCasesTool implements ResearchTool {
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
    public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
        if (context.state().getSnapshot() == null || context.state().getMetrics().isEmpty()) {
            return ToolResult.failure("多空条件分析需要快照和指标", "METRICS_REQUIRED", false);
        }
        BullBearResearchResult result = delegate.analyze(
                context.state().getSnapshot(),
                context.state().getMetrics(),
                context.state().getRiskAssessment()
        );
        context.state().setBullBearResearch(result);
        return ToolResult.success(
                "已形成 %d 条正向条件和 %d 条风险条件".formatted(
                        result.bullCases().size(), result.bearCases().size()
                ),
                Map.of("research", result, "policyVersion", BullBearCaseBuilder.POLICY_VERSION)
        );
    }
}
