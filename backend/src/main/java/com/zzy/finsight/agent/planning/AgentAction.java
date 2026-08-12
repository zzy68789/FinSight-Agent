package com.zzy.finsight.agent.planning;

import java.util.List;

/**
 * 表示 Planner 在某一轮选择的结构化动作。
 * @param type 动作类型。
 * @param toolCalls 工具调用列表。
 * @param reason 选择该动作的研究理由。
 */
public record AgentAction(AgentActionType type, List<ToolInvocation> toolCalls, String reason) {
    public AgentAction {
        type = type == null ? AgentActionType.STOP_INSUFFICIENT_EVIDENCE : type;
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        reason = reason == null ? "" : reason;
    }

    /** 创建单工具调用动作。 */
    public static AgentAction call(String toolName, String reason) {
        return new AgentAction(
                AgentActionType.CALL_TOOL,
                List.of(new ToolInvocation(toolName, java.util.Map.of())),
                reason
        );
    }

    /** 创建报告综合动作。 */
    public static AgentAction synthesize(String reason) {
        return new AgentAction(AgentActionType.SYNTHESIZE, List.of(), reason);
    }
}
