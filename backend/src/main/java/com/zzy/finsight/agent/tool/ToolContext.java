package com.zzy.finsight.agent.tool;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.domain.stock.BullBearResearchResult;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;

import java.util.List;

/**
 * 表示工具执行时可访问的受限上下文。
 * @param ownerId 当前用户标识。
 * @param taskId 当前任务标识。
 * @param request 不可变研究请求投影。
 * @param subject 当前证券主体只读视图。
 * @param snapshot 当前金融快照只读视图。
 * @param metrics 当前指标只读视图。
 * @param riskAssessment 当前风险结果只读视图。
 * @param bullBearResearch 当前多空研究只读视图。
 */
public record ToolContext(
        long ownerId,
        long taskId,
        ToolResearchRequest request,
        StockSubject subject,
        FinancialSnapshot snapshot,
        List<FinancialMetricResult> metrics,
        FinancialRiskAssessment riskAssessment,
        BullBearResearchResult bullBearResearch
) {
    public ToolContext {
        metrics = metrics == null ? List.of() : List.copyOf(metrics);
    }

    /** 从当前 AgentState 创建不暴露可变状态的工具上下文。 */
    public static ToolContext from(long ownerId, AgentState state) {
        return new ToolContext(
                ownerId,
                state.getTaskId(),
                ToolResearchRequest.from(state.getRequest()),
                state.getSubject(),
                state.getSnapshot(),
                state.getMetrics(),
                state.getRiskAssessment(),
                state.getBullBearResearch()
        );
    }
}
