package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.planning.ToolInvocation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 注册并限制 Planner 可以访问的投研工具集合。
 */
@Component
public class ResearchToolRegistry {
    private static final List<String> FORBIDDEN_NAMES = List.of("trade", "order", "position", "broker", "下单", "交易");
    private final Map<String, ResearchTool<?>> tools;
    private final ObjectMapper objectMapper;

    @Autowired
    public ResearchToolRegistry(List<ResearchTool<?>> tools, ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        Map<String, ResearchTool<?>> registered = new LinkedHashMap<>();
        for (ResearchTool<?> tool : tools == null ? List.<ResearchTool<?>>of() : tools) {
            String name = normalize(tool.name());
            rejectTradingCapability(name);
            if (!tool.readOnly()) {
                throw new IllegalStateException("第一版 Research Agent 禁止注册非只读工具：" + name);
            }
            if (registered.putIfAbsent(name, tool) != null) {
                throw new IllegalStateException("Research Tool 名称重复：" + name);
            }
        }
        Map<String, ResearchTool<?>> sorted = new LinkedHashMap<>();
        registered.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> sorted.put(entry.getKey(), entry.getValue()));
        this.tools = java.util.Collections.unmodifiableMap(sorted);
    }

    /** 供不启动 Spring 容器的契约测试创建注册表。 */
    public ResearchToolRegistry(List<ResearchTool<?>> tools) {
        this(tools, new ObjectMapper().findAndRegisterModules());
    }

    /** 查询指定白名单工具。 */
    public ResearchTool<?> require(String name) {
        ResearchTool<?> tool = tools.get(normalize(name));
        if (tool == null) {
            throw new IllegalArgumentException("UNAUTHORIZED_TOOL：" + name);
        }
        return tool;
    }

    /** 返回注册工具的稳定列表。 */
    public Collection<ResearchTool<?>> all() {
        return tools.values();
    }

    /** 按工具自己的 contract 解码并校验 Planner 参数。 */
    public PreparedToolCall prepare(ToolInvocation invocation) {
        if (invocation == null) {
            throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS：工具调用不能为空");
        }
        ResearchTool<?> tool = require(invocation.toolName());
        Set<String> unknownArguments = new java.util.HashSet<>(invocation.arguments().keySet());
        unknownArguments.removeAll(tool.definition().argumentNames());
        if (!unknownArguments.isEmpty()) {
            throw new IllegalArgumentException(
                    "INVALID_TOOL_ARGUMENTS：%s 不支持参数 %s".formatted(tool.name(), unknownArguments)
            );
        }
        try {
            return prepareTyped(tool, invocation.arguments());
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw new IllegalArgumentException(
                    "INVALID_TOOL_ARGUMENTS：%s 参数解码失败：%s".formatted(tool.name(), exception.getMessage()),
                    exception
            );
        }
    }

    /** 执行准备完成的调用，并由工具模块负责异常分类。 */
    public ToolResult execute(PreparedToolCall prepared, ToolContext context) {
        return executeTyped(prepared.tool(), context, prepared.arguments());
    }

    /** 返回适合放入 Planner Prompt 的工具目录。 */
    public List<Map<String, Object>> catalog() {
        return tools.values().stream()
                .map(tool -> tool.definition().plannerCatalogEntry())
                .toList();
    }

    private <A extends ToolArguments> PreparedToolCall prepareTyped(
            ResearchTool<A> tool,
            Map<String, Object> rawArguments
    ) {
        A decoded = tool.decode(rawArguments, objectMapper);
        if (decoded == null) {
            throw new IllegalArgumentException("INVALID_TOOL_ARGUMENTS：工具参数解码结果不能为空");
        }
        tool.validate(decoded);
        return new PreparedToolCall(tool, decoded);
    }

    @SuppressWarnings("unchecked")
    private <A extends ToolArguments> ToolResult executeTyped(
            ResearchTool<?> rawTool,
            ToolContext context,
            ToolArguments rawArguments
    ) {
        ResearchTool<A> tool = (ResearchTool<A>) rawTool;
        try {
            return tool.execute(context, (A) rawArguments);
        } catch (Throwable throwable) {
            ToolFailure failure = tool.classify(throwable);
            return ToolResult.failure(failure.message(), failure.errorCode(), failure.retryable());
        }
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
