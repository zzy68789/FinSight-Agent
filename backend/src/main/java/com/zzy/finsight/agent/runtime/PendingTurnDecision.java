package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.PlannerOutput;

/**
 * 表示恢复时从未完成 turn 读取的权威 Planner 动作。
 * @param turnId 已持久化轮次标识。
 * @param turnNo 已持久化轮次序号。
 * @param output 原始 Planner 动作及调用元数据。
 * @param routeCorrect 原动作是否通过确定性路由校验。
 */
public record PendingTurnDecision(
        long turnId,
        int turnNo,
        PlannerOutput<AgentAction> output,
        boolean routeCorrect
) {
}
