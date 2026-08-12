package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;
import java.util.Set;

/**
 * 定义 Planner 可选择的只读投研工具协议。
 */
public interface ResearchTool<A extends ToolArguments> {
    /** 返回稳定工具名称。 */
    String name();

    /** 返回提供给 Planner 的能力说明。 */
    String description();

    /** 返回包含参数与结果结构的稳定工具定义。 */
    default ToolDefinition definition() {
        return new ToolDefinition(
                name(), description(), allowParallel(), idempotent(), readOnly(), producesEvidence(),
                allowedArguments().stream()
                        .map(argument -> ToolParameterSchema.optionalString(argument, "兼容参数", 0))
                        .toList(),
                Map.of()
        );
    }

    /** 将 Planner JSON 边界参数解码为本工具的 Java 命令。 */
    @SuppressWarnings("unchecked")
    default A decode(Map<String, Object> arguments, ObjectMapper objectMapper) {
        return (A) new RawToolArguments(arguments);
    }

    /** 校验解码后的业务约束。 */
    default void validate(A arguments) {
    }

    /** 执行已完成解码和校验的工具命令。 */
    ToolResult execute(ToolContext context, A arguments);

    /** 将未捕获异常归类为稳定工具错误。 */
    default ToolFailure classify(Throwable throwable) {
        String message = throwable == null || throwable.getMessage() == null
                ? "工具执行失败" : throwable.getMessage();
        return new ToolFailure("TOOL_EXECUTION_FAILED", message, definition().idempotent());
    }

    /** 返回工具是否支持相互独立的并行调用。 */
    default boolean allowParallel() {
        return false;
    }

    /** 返回工具调用是否具备幂等性。 */
    default boolean idempotent() {
        return true;
    }

    /** 返回工具是否只读。 */
    default boolean readOnly() {
        return true;
    }

    /** 返回兼容参数名称，新工具应通过 definition 提供完整 schema。 */
    default Set<String> allowedArguments() {
        return Set.of();
    }

    /** 返回工具是否会向证据账本追加原始证据。 */
    default boolean producesEvidence() {
        return false;
    }
}
