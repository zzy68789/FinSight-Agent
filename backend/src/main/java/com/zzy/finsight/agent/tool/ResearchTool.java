package com.zzy.finsight.agent.tool;

import java.util.Map;
import java.util.Set;

/**
 * 定义 Planner 可选择的只读投研工具协议。
 */
public interface ResearchTool {
    /** 返回稳定工具名称。 */
    String name();

    /** 返回提供给 Planner 的能力说明。 */
    String description();

    /** 返回工具是否支持相互独立的并行调用。 */
    default boolean allowParallel() {
        return false;
    }

    /** 返回工具调用是否具备幂等性。 */
    default boolean idempotent() {
        return true;
    }

    /** 返回工具是否只读；第一版 Agent 只允许只读工具。 */
    default boolean readOnly() {
        return true;
    }

    /** 返回允许的参数名称，未声明的参数由策略层拒绝。 */
    default Set<String> allowedArguments() {
        return Set.of();
    }

    /** 返回工具是否会向证据账本追加原始证据。 */
    default boolean producesEvidence() {
        return false;
    }

    /** 校验并执行工具。 */
    ToolResult execute(ToolContext context, Map<String, Object> arguments);
}
