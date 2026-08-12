package com.zzy.finsight.agent.event;

import java.util.Map;

/**
 * 表示尚未分配任务内序号的 Agent 事件草稿。
 * @param type 事件类型。
 * @param payload 事件负载。
 * @param durationMs 关联步骤耗时。
 * @param status 事件状态。
 * @param errorMessage 失败说明。
 */
public record AgentEventDraft(
        String type,
        Map<String, Object> payload,
        long durationMs,
        String status,
        String errorMessage
) {
    public AgentEventDraft {
        type = type == null ? "" : type;
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        durationMs = Math.max(0L, durationMs);
        status = status == null || status.isBlank() ? "SUCCESS" : status;
        errorMessage = errorMessage == null ? "" : errorMessage;
    }
}
