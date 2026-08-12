package com.zzy.finsight.agent.tool;

import java.util.Map;

/**
 * 兼容尚未迁移的测试工具适配器所使用的原始参数。
 *
 * @param values 原始 JSON 参数。
 */
public record RawToolArguments(Map<String, Object> values) implements ToolArguments {
    public RawToolArguments {
        values = values == null ? Map.of() : Map.copyOf(values);
    }
}
