package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.AgentActionType;
import com.zzy.finsight.agent.planning.ToolInvocation;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ToolPolicyGuardTest {

    @Test
    void registryRejectsTradingCapability() {
        assertThatThrownBy(() -> new ResearchToolRegistry(List.of(tool("trade_executor"))))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("禁止注册交易执行类工具");
    }

    @Test
    void guardRejectsUnknownAndRepeatedCalls() {
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(tool("get_market_snapshot")));
        ToolPolicyGuard guard = new ToolPolicyGuard(registry, new ObjectMapper());
        AgentState state = new AgentState();
        ToolInvocation invocation = new ToolInvocation("get_market_snapshot", Map.of("range", "1Y"));

        assertThatThrownBy(() -> guard.validate(
                new AgentAction(AgentActionType.CALL_TOOL,
                        List.of(new ToolInvocation("unknown_tool", Map.of())), ""),
                state, 3, 2
        )).hasMessageContaining("UNAUTHORIZED_TOOL");

        assertThatThrownBy(() -> guard.validate(
                new AgentAction(AgentActionType.CALL_TOOL, List.of(invocation), ""),
                state, 3, 2
        )).hasMessageContaining("INVALID_TOOL_ARGUMENTS");

        ToolInvocation noArguments = new ToolInvocation("get_market_snapshot", Map.of());
        state.setExecutedCallHashes(Set.of(guard.callHash(noArguments)));
        assertThatThrownBy(() -> guard.validate(
                new AgentAction(AgentActionType.CALL_TOOL, List.of(noArguments), ""),
                state, 3, 2
        )).hasMessageContaining("DUPLICATE_TOOL_CALL");
    }

    private ResearchTool tool(String name) {
        return new ResearchTool() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "测试工具";
            }

            @Override
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                return ToolResult.success("完成", Map.of());
            }
        };
    }
}
