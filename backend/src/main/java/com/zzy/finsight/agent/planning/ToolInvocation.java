package com.zzy.finsight.agent.planning;

import java.util.Map;

/**
 * 表示 Planner 发起的一次白名单工具调用。
 * @param toolName 工具稳定名称。
 * @param arguments 结构化工具参数。
 */
public record ToolInvocation(String toolName, Map<String, Object> arguments) {
    public ToolInvocation {
        toolName = toolName == null ? "" : toolName.trim();
        arguments = arguments == null ? Map.of() : Map.copyOf(arguments);
    }
}
