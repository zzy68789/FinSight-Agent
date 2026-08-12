package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.event.AgentEventDraft;

import java.util.List;

/**
 * 表示一次完整 Agent turn 的原子提交命令。
 *
 * @param ownerId 用户标识。
 * @param turnId 轮次数据库标识。
 * @param state 本轮归约后的内存状态。
 * @param observation 轮次观察摘要。
 * @param status 轮次状态。
 * @param durationMs 轮次耗时毫秒数。
 * @param toolCalls 本轮需要提交的工具 journal 结果。
 * @param events 与本轮状态一起提交的事件草稿。
 */
public record AgentTurnCommit(
        long ownerId,
        long turnId,
        AgentState state,
        String observation,
        String status,
        long durationMs,
        List<CommittedToolCall> toolCalls,
        List<AgentEventDraft> events
) {
    public AgentTurnCommit {
        observation = observation == null ? "" : observation;
        status = status == null || status.isBlank() ? "SUCCESS" : status;
        durationMs = Math.max(0L, durationMs);
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        events = events == null ? List.of() : List.copyOf(events);
    }

    /** 兼容暂不携带事务事件的调用方。 */
    public AgentTurnCommit(
            long ownerId,
            long turnId,
            AgentState state,
            String observation,
            String status,
            long durationMs,
            List<CommittedToolCall> toolCalls
    ) {
        this(ownerId, turnId, state, observation, status, durationMs, toolCalls, List.of());
    }
}
