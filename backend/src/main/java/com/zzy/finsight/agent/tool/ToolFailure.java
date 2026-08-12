package com.zzy.finsight.agent.tool;

/**
 * 表示工具模块对异常给出的稳定分类。
 *
 * @param errorCode 稳定错误码。
 * @param message 可供 Planner 理解的短说明。
 * @param retryable 是否可重试。
 */
public record ToolFailure(String errorCode, String message, boolean retryable) {
    public ToolFailure {
        errorCode = errorCode == null || errorCode.isBlank() ? "TOOL_EXECUTION_FAILED" : errorCode;
        message = message == null || message.isBlank() ? "工具执行失败" : message;
    }
}
