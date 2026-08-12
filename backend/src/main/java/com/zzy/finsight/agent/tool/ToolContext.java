package com.zzy.finsight.agent.tool;

import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.dto.agent.ResearchRunRequest;

/**
 * 表示工具执行时可访问的受限上下文。
 * @param ownerId 当前用户标识。
 * @param taskId 当前任务标识。
 * @param request 原始研究请求。
 * @param state 当前 Agent 状态。
 */
public record ToolContext(long ownerId, long taskId, ResearchRunRequest request, AgentState state) {
}
