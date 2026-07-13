package com.zzy.finsight.dto;

/**
 * 表示一个 SSE 工作流事件。
 * @param step SSE 步骤名称。
 * @param data 响应数据。
 */
public record SseEvent(String step, Object data) {
}
