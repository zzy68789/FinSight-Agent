package com.zzy.finsight.agent.tool;

/**
 * 表示已经过 schema 解码与校验、可安全交给执行器的工具调用。
 *
 * @param tool 目标工具模块。
 * @param arguments 类型化参数。
 */
public record PreparedToolCall(ResearchTool<?> tool, ToolArguments arguments) {
}
