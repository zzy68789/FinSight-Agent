package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.AgentActionType;
import com.zzy.finsight.agent.planning.ToolInvocation;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashSet;
import java.util.Set;

/**
 * 在工具执行前校验白名单、并行约束、重复调用和预算。
 */
@Component
public class ToolPolicyGuard {
    private final ResearchToolRegistry registry;
    private final ObjectMapper objectMapper;

    public ToolPolicyGuard(ResearchToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    /** 校验 Planner 动作并返回本轮稳定调用摘要。 */
    public void validate(AgentAction action, AgentState state, int remainingToolCalls, int maxParallelTools) {
        if (action == null) {
            throw new IllegalArgumentException("Planner 未返回动作");
        }
        if (action.type() != AgentActionType.CALL_TOOL && action.type() != AgentActionType.CALL_TOOLS_PARALLEL) {
            if (!action.toolCalls().isEmpty()) {
                throw new IllegalArgumentException("非工具动作不得携带 toolCalls");
            }
            return;
        }
        if (action.toolCalls().isEmpty()) {
            throw new IllegalArgumentException("工具动作缺少 toolCalls");
        }
        if (action.type() == AgentActionType.CALL_TOOL && action.toolCalls().size() != 1) {
            throw new IllegalArgumentException("CALL_TOOL 只允许一次工具调用");
        }
        if (action.toolCalls().size() > Math.max(1, maxParallelTools)) {
            throw new IllegalArgumentException("单轮并行工具数超过限制");
        }
        if (action.toolCalls().size() > remainingToolCalls) {
            throw new IllegalStateException("BUDGET_EXHAUSTED：工具调用预算不足");
        }
        Set<String> currentHashes = new HashSet<>();
        for (ToolInvocation invocation : action.toolCalls()) {
            ResearchTool tool = registry.require(invocation.toolName());
            Set<String> unknownArguments = new HashSet<>(invocation.arguments().keySet());
            unknownArguments.removeAll(tool.allowedArguments());
            if (!unknownArguments.isEmpty()) {
                throw new IllegalArgumentException(
                        "INVALID_TOOL_ARGUMENTS：%s 不支持参数 %s".formatted(tool.name(), unknownArguments)
                );
            }
            if (action.type() == AgentActionType.CALL_TOOLS_PARALLEL && !tool.allowParallel()) {
                throw new IllegalArgumentException("工具不允许并行执行：" + tool.name());
            }
            String callHash = callHash(invocation);
            if (!currentHashes.add(callHash) || state.getExecutedCallHashes().contains(callHash)) {
                throw new IllegalArgumentException("DUPLICATE_TOOL_CALL：" + invocation.toolName());
            }
        }
    }

    /** 计算规范化工具调用摘要。 */
    public String callHash(ToolInvocation invocation) {
        try {
            String canonical = invocation.toolName().trim().toLowerCase(java.util.Locale.ROOT)
                    + "|" + objectMapper.writeValueAsString(new java.util.TreeMap<>(invocation.arguments()));
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (JsonProcessingException | NoSuchAlgorithmException exception) {
            throw new IllegalStateException("无法生成工具调用摘要", exception);
        }
    }
}
