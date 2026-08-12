package com.zzy.finsight.dto.agent;

import com.zzy.finsight.domain.AgentToolCallRecord;
import com.zzy.finsight.domain.AgentTurnRecord;
import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.domain.AgentPlannerCallRecord;

import java.util.List;

/**
 * 表示 Research Agent 的动态决策和工具调用轨迹。
 * @param taskId 任务标识。
 * @param status 当前任务状态。
 * @param stage 当前运行阶段。
 * @param turns Planner决策轮次。
 * @param toolCalls 白名单工具调用记录。
 * @param events 供实时 SSE 与历史回放共用的版本化 Agent 事件。
 * @param plannerCalls Planner模型与成本调用明细。
 * @param plannerPerformance Planner结构、路由、Token与延迟基线。
 */
public record ResearchRunTraceResponse(
        long taskId,
        String status,
        String stage,
        List<AgentTurnRecord> turns,
        List<AgentToolCallRecord> toolCalls,
        List<AgentEvent> events,
        List<AgentPlannerCallRecord> plannerCalls,
        PlannerPerformanceSummary plannerPerformance
) {
    public ResearchRunTraceResponse {
        turns = turns == null ? List.of() : List.copyOf(turns);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        events = events == null ? List.of() : List.copyOf(events);
        plannerCalls = plannerCalls == null ? List.of() : List.copyOf(plannerCalls);
        plannerPerformance = plannerPerformance == null
                ? PlannerPerformanceSummary.from(List.of()) : plannerPerformance;
    }
}
