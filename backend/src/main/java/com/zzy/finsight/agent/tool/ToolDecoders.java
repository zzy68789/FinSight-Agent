package com.zzy.finsight.agent.tool;

import java.util.Map;

/**
 * 提供无参数工具共用的严格解码逻辑。
 */
public final class ToolDecoders {
    private ToolDecoders() {
    }

    /** 解码无参数命令，拒绝任何残留字段。 */
    public static NoToolArguments noArguments(Map<String, Object> arguments) {
        if (arguments != null && !arguments.isEmpty()) {
            throw new IllegalArgumentException("工具不接受参数");
        }
        return NoToolArguments.INSTANCE;
    }
}
