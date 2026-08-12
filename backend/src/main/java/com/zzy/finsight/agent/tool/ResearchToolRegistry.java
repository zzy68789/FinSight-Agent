package com.zzy.finsight.agent.tool;

import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * 注册并限制 Planner 可以访问的投研工具集合。
 */
@Component
public class ResearchToolRegistry {
    private static final List<String> FORBIDDEN_NAMES = List.of("trade", "order", "position", "broker", "下单", "交易");
    private final Map<String, ResearchTool> tools;

    public ResearchToolRegistry(List<ResearchTool> tools) {
        Map<String, ResearchTool> registered = new LinkedHashMap<>();
        for (ResearchTool tool : tools == null ? List.<ResearchTool>of() : tools) {
            String name = normalize(tool.name());
            rejectTradingCapability(name);
            if (!tool.readOnly()) {
                throw new IllegalStateException("第一版 Research Agent 禁止注册非只读工具：" + name);
            }
            if (registered.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("Research Tool 名称重复：" + name);
            }
        }
        Map<String, ResearchTool> sorted = new LinkedHashMap<>();
        registered.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        this.tools = java.util.Collections.unmodifiableMap(sorted);
    }

    /** 查询指定白名单工具。 */
    public ResearchTool require(String name) {
        ResearchTool tool = tools.get(normalize(name));
        if (tool == null) {
            throw new IllegalArgumentException("UNAUTHORIZED_TOOL：" + name);
        }
        return tool;
    }

    /** 返回注册工具的稳定列表。 */
    public Collection<ResearchTool> all() {
        return tools.values();
    }

    /** 返回适合放入 Planner Prompt 的工具目录。 */
    public List<Map<String, Object>> catalog() {
        return tools.values().stream()
                .map(tool -> Map.<String, Object>of(
                        "name", tool.name(),
                        "description", tool.description(),
                        "allowParallel", tool.allowParallel(),
                        "idempotent", tool.idempotent(),
                        "readOnly", tool.readOnly(),
                        "producesEvidence", tool.producesEvidence(),
                        "allowedArguments", tool.allowedArguments()
                ))
                .toList();
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    private void rejectTradingCapability(String name) {
        if (FORBIDDEN_NAMES.stream().anyMatch(name::contains)) {
            throw new IllegalStateException("禁止注册交易执行类工具：" + name);
        }
    }
}
