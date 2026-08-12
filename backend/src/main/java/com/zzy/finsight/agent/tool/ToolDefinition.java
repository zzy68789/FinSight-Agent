package com.zzy.finsight.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 向 Planner 暴露工具能力、参数和结果 schema。
 *
 * @param name 工具稳定名称。
 * @param description 工具用途说明。
 * @param allowParallel 是否允许并行调用。
 * @param idempotent 是否可安全重试。
 * @param readOnly 是否只读。
 * @param producesEvidence 是否产生金融证据。
 * @param arguments 参数 schema。
 * @param resultSchema 结果字段及类型说明。
 */
public record ToolDefinition(
        String name,
        String description,
        boolean allowParallel,
        boolean idempotent,
        boolean readOnly,
        boolean producesEvidence,
        List<ToolParameterSchema> arguments,
        Map<String, String> resultSchema
) {
    public ToolDefinition {
        name = name == null ? "" : name.trim();
        description = description == null ? "" : description.trim();
        arguments = arguments == null ? List.of() : List.copyOf(arguments);
        resultSchema = resultSchema == null ? Map.of() : Map.copyOf(resultSchema);
    }

    /** 创建无参数只读工具定义。 */
    public static ToolDefinition readOnly(String name, String description, Map<String, String> resultSchema) {
        return new ToolDefinition(name, description, false, true, true, false, List.of(), resultSchema);
    }

    /** 返回所有声明过的参数名称。 */
    public Set<String> argumentNames() {
        return arguments.stream().map(ToolParameterSchema::name).collect(Collectors.toUnmodifiableSet());
    }

    /** 转换为稳定的 Planner 工具目录项。 */
    public Map<String, Object> plannerCatalogEntry() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("name", name);
        entry.put("description", description);
        entry.put("allowParallel", allowParallel);
        entry.put("idempotent", idempotent);
        entry.put("readOnly", readOnly);
        entry.put("producesEvidence", producesEvidence);
        entry.put("arguments", arguments);
        entry.put("resultSchema", resultSchema);
        return Map.copyOf(entry);
    }
}
