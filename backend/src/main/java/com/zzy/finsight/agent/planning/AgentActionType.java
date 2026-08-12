package com.zzy.finsight.agent.planning;

/**
 * 限定 Planner 可以要求 Runtime 执行的动作类型。
 */
public enum AgentActionType {
    CALL_TOOL,
    CALL_TOOLS_PARALLEL,
    REPLAN,
    SYNTHESIZE,
    STOP_INSUFFICIENT_EVIDENCE
}
