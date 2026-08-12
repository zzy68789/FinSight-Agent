package com.zzy.finsight.dto.agent;

import com.zzy.finsight.domain.AgentToolCallRecord;
import com.zzy.finsight.domain.AgentTurnRecord;

import java.util.List;

/**
 * 表示 Research Agent 的动态决策和工具调用轨迹。
 * @param taskId 任务标识。
 * @param status 当前任务状态。
 * @param stage 当前运行阶段。
 * @param turns Planner决策轮次。
 * @param toolCalls 白名单工具调用记录。
 */
public record ResearchRunTraceResponse(
        long taskId,
        String status,
        String stage,
        List<AgentTurnRecord> turns,
        List<AgentToolCallRecord> toolCalls
) {
    public ResearchRunTraceResponse {
        turns = turns == null ? List.of() : List.copyOf(turns);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
