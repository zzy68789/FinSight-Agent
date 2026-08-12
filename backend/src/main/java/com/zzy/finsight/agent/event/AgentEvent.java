package com.zzy.finsight.agent.event;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 表示已经持久化、可由 SSE 与 Trace 共用的版本化 Agent 事件。
 * @param id outbox记录标识。
 * @param schemaVersion 事件结构版本。
 * @param taskId 任务标识。
 * @param threadId 会话线程标识。
 * @param sequence 任务内单调序号。
 * @param eventId 全局唯一事件标识。
 * @param turnNo Agent轮次。
 * @param type 事件类型。
 * @param status 事件状态。
 * @param payload 事件业务负载。
 * @param errorMessage 失败说明。
 * @param durationMs 关联步骤耗时。
 * @param publishedAt 投递时间。
 * @param createdAt 创建时间。
 */
public record AgentEvent(
        long id,
        String schemaVersion,
        long taskId,
        String threadId,
        long sequence,
        String eventId,
        int turnNo,
        String type,
        String status,
        Map<String, Object> payload,
        String errorMessage,
        long durationMs,
        LocalDateTime publishedAt,
        LocalDateTime createdAt
) {
    public static final String CURRENT_SCHEMA_VERSION = "agent-event-v1";

    public AgentEvent {
        payload = payload == null ? Map.of() : Map.copyOf(payload);
        errorMessage = errorMessage == null ? "" : errorMessage;
    }

    /** 返回兼容现有 SSE 字段、同时携带版本和序号的扁平负载。 */
    public Map<String, Object> ssePayload() {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schemaVersion", schemaVersion);
        data.put("taskId", taskId);
        data.put("threadId", threadId);
        data.put("sequence", sequence);
        data.put("eventId", eventId);
        data.put("turnNo", turnNo);
        data.put("timestamp", createdAt == null ? "" : createdAt.toString());
        data.put("status", status);
        data.putAll(payload);
        return Map.copyOf(data);
    }
}
