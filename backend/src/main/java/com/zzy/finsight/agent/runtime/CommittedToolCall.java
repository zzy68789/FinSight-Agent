package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.tool.ToolResult;

/**
 * 表示等待随 Agent turn 一并原子提交的工具 journal 结果。
 *
 * @param databaseId 工具调用数据库标识。
 * @param invocation 原始工具调用。
 * @param result 结构化结果。
 * @param durationMs 执行耗时毫秒数。
 */
public record CommittedToolCall(
        long databaseId,
        ToolInvocation invocation,
        ToolResult result,
        long durationMs
) {
}
