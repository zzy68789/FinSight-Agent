package com.zzy.finsight.agent.tool;

/**
 * 表示不接受参数的工具命令。
 */
public record NoToolArguments() implements ToolArguments {
    public static final NoToolArguments INSTANCE = new NoToolArguments();
}
