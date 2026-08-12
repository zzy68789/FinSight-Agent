package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.AgentAction;
import com.zzy.finsight.agent.planning.AgentActionType;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.agent.quality.QualityGateDecision;
import com.zzy.finsight.agent.quality.QualityGateFailureType;
import com.zzy.finsight.agent.quality.QualityGateIssue;
import com.zzy.finsight.agent.quality.QualityGateRoute;
import com.zzy.finsight.agent.quality.QualityGateSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;

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

    @Test
    void guardRequiresNovelEvidenceToolWhileRecoveryIsPending() {
        ResearchTool evidenceTool = evidenceTool("search_public_evidence");
        ResearchTool calculationTool = tool("calculate_financial_metrics");
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(evidenceTool, calculationTool));
        ToolPolicyGuard guard = new ToolPolicyGuard(registry, new ObjectMapper());
        AgentState state = new AgentState();
        state.beginEvidenceRecovery(evidenceRecoveryDecision());

        assertThatThrownBy(() -> guard.validate(
                AgentAction.synthesize("继续综合"), state, 3, 2
        )).hasMessageContaining("EVIDENCE_RECOVERY_ACTION_REQUIRED");

        assertThatThrownBy(() -> guard.validate(
                AgentAction.call("calculate_financial_metrics", "先计算"), state, 3, 2
        )).hasMessageContaining("EVIDENCE_RECOVERY_TOOL_REQUIRED");

        assertThatCode(() -> guard.validate(
                new AgentAction(
                        AgentActionType.CALL_TOOL,
                        List.of(new ToolInvocation(
                                "search_public_evidence", Map.of("query", "新的交叉验证查询")
                        )),
                        "补充证据"
                ),
                state,
                3,
                2
        )).doesNotThrowAnyException();
    }

    @Test
    void guardAllowsOneDeterministicReexecutionAfterEvidenceChanges() {
        ResearchTool calculationTool = tool("calculate_financial_metrics");
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(calculationTool));
        ToolPolicyGuard guard = new ToolPolicyGuard(registry, new ObjectMapper());
        AgentState state = new AgentState();
        ToolInvocation invocation = new ToolInvocation("calculate_financial_metrics", Map.of());
        state.setExecutedCallHashes(Set.of(guard.callHash(invocation)));
        state.invalidateDerivedResultsAfterEvidenceChange();

        assertThatCode(() -> guard.validate(
                AgentAction.call("calculate_financial_metrics", "基于新证据重算"), state, 3, 2
        )).doesNotThrowAnyException();
    }

    private ResearchTool<?> tool(String name) {
        return new ResearchTool<RawToolArguments>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "测试工具";
            }

            @Override
            public ToolResult execute(ToolContext context, RawToolArguments arguments) {
                return ToolResult.success("完成");
            }
        };
    }

    private ResearchTool<?> evidenceTool(String name) {
        return new ResearchTool<RawToolArguments>() {
            @Override
            public String name() {
                return name;
            }

            @Override
            public String description() {
                return "测试证据工具";
            }

            @Override
            public Set<String> allowedArguments() {
                return Set.of("query");
            }

            @Override
            public boolean producesEvidence() {
                return true;
            }

            @Override
            public ToolResult execute(ToolContext context, RawToolArguments arguments) {
                return ToolResult.success("完成");
            }
        };
    }

    private QualityGateDecision evidenceRecoveryDecision() {
        QualityGateIssue issue = new QualityGateIssue(
                QualityGateFailureType.EVIDENCE_INSUFFICIENT,
                QualityGateSource.CITATION_REVIEW,
                "EVIDENCE_INSUFFICIENT",
                "需要补充证据"
        );
        return new QualityGateDecision(
                "FAIL",
                QualityGateRoute.COLLECT_MORE_EVIDENCE,
                List.of(issue),
                "EVIDENCE_INSUFFICIENT：需要补充证据"
        );
    }
}
