package com.zzy.finsight.agent.tool;

/**
 * 描述单个工具参数的稳定类型约束。
 *
 * @param name 参数名称。
 * @param type JSON 数据类型。
 * @param required 是否必填。
 * @param description 参数用途说明。
 * @param minLength 字符串最小长度，零表示不限制。
 * @param maxLength 字符串最大长度，零表示不限制。
 */
public record ToolParameterSchema(
        String name,
        String type,
        boolean required,
        String description,
        int minLength,
        int maxLength
) {
    public ToolParameterSchema {
        name = name == null ? "" : name.trim();
        type = type == null || type.isBlank() ? "string" : type.trim();
        description = description == null ? "" : description.trim();
        minLength = Math.max(0, minLength);
        maxLength = Math.max(0, maxLength);
    }

    /** 创建可选字符串参数定义。 */
    public static ToolParameterSchema optionalString(String name, String description, int maxLength) {
        return new ToolParameterSchema(name, "string", false, description, 0, maxLength);
    }
}
