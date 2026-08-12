package com.zzy.finsight.agent.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.quality.QualityGateDecision;
import com.zzy.finsight.agent.quality.QualityGateFailureType;
import com.zzy.finsight.agent.quality.QualityGateIssue;
import com.zzy.finsight.agent.quality.QualityGateRoute;
import com.zzy.finsight.agent.quality.QualityGateSource;
import com.zzy.finsight.agent.tool.ResearchTool;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.agent.tool.ToolContext;
import com.zzy.finsight.agent.tool.ToolResult;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ResearchPlannerTest {

    @Test
    void fallbackPlanChangesEvidenceNeedsWithResearchQuestion() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());

        ResearchPlan financial = planner.createPlan(request("最近两个季度毛利率为何变化"), registry).value();
        ResearchPlan market = planner.createPlan(request("近期价格波动和估值发生了什么变化"), registry).value();

        assertThat(financial.plannerMode()).isEqualTo("DETERMINISTIC_FALLBACK");
        assertThat(financial.requiredEvidence()).contains("财务报表及同比口径");
        assertThat(market.requiredEvidence()).contains("行情和估值快照");
    }

    @Test
    void fallbackActionSelectsQuestionSpecificToolInsteadOfFixedAllProviders() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());
        AgentState state = new AgentState();
        state.setRequest(request("分析近期价格波动和市场风险"));
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        state.setCompletedTools(java.util.Set.of("get_company_profile"));

        AgentAction action = planner.nextAction(state, registry).value();

        assertThat(action.type()).isEqualTo(AgentActionType.CALL_TOOL);
        assertThat(action.toolCalls()).extracting(ToolInvocation::toolName)
                .containsExactly("get_market_snapshot");
    }

    @Test
    void fallbackActionUsesQuestionFocusedPublicSearchAfterMarketSnapshot() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = mock(ResearchToolRegistry.class);
        when(registry.catalog()).thenReturn(List.of());
        AgentState state = new AgentState();
        state.setRequest(request("分析近期公告事件对经营的影响原因"));
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        state.setCompletedTools(java.util.Set.of(
                "get_company_profile", "get_financial_statements", "get_market_snapshot", "retrieve_uploaded_reports"
        ));

        AgentAction action = planner.nextAction(state, registry).value();

        assertThat(action.toolCalls()).extracting(ToolInvocation::toolName)
                .containsExactly("search_public_evidence");
    }

    @Test
    void fallbackEvidenceRecoveryChangesSearchQueryAfterPreviousSourceWasUsed() {
        ResearchPlanner planner = new ResearchPlanner(unavailableLlm(), new ObjectMapper().findAndRegisterModules());
        ResearchToolRegistry registry = new ResearchToolRegistry(List.of(publicEvidenceTool()));
        AgentState state = new AgentState();
        state.setRequest(request("分析近期公告事件对经营的影响原因"));
        state.setSubject(new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"));
        state.setCompletedTools(Set.of(
                "get_company_profile",
                "get_financial_statements",
                "get_market_snapshot",
                "retrieve_uploaded_reports",
                "search_public_evidence"
        ));
        ToolInvocation previous = new ToolInvocation(
                "search_public_evidence", Map.of("query", "原始公告影响查询")
        );
        state.setToolInvocationHistory(List.of(previous));
        state.beginEvidenceRecovery(evidenceRecoveryDecision());

        PlannerOutput<AgentAction> output = planner.nextAction(state, registry);
        AgentAction action = output.value();

        assertThat(output.degraded()).isTrue();
        assertThat(action.type()).isEqualTo(AgentActionType.CALL_TOOL);
        assertThat(action.toolCalls()).hasSize(1);
        ToolInvocation recovery = action.toolCalls().get(0);
        assertThat(recovery.toolName()).isEqualTo("search_public_evidence");
        assertThat(recovery.arguments().get("query"))
                .isNotEqualTo(previous.arguments().get("query"))
                .asString()
                .contains("第1轮第1次");

        state.recordEvidenceRecoveryAttempt(recovery, 0L);
        state.setToolInvocationHistory(List.of(previous, recovery));
        ToolInvocation secondRecovery = planner.nextAction(state, registry).value().toolCalls().get(0);

        assertThat(secondRecovery.arguments().get("query"))
                .isNotEqualTo(recovery.arguments().get("query"))
                .asString()
                .contains("第1轮第2次");
    }

    private ResearchRunRequest request(String question) {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion(question);
        request.setSearchMode("hybrid");
        return request;
    }

    private LlmClient unavailableLlm() {
        return (prompt, modelType) -> {
            throw new IllegalStateException("LLM is not configured");
        };
    }

    private ResearchTool publicEvidenceTool() {
        return new ResearchTool() {
            @Override
            public String name() {
                return "search_public_evidence";
            }

            @Override
            public String description() {
                return "测试公开证据检索";
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
            public ToolResult execute(ToolContext context, Map<String, Object> arguments) {
                return ToolResult.success("完成", Map.of());
            }
        };
    }

    private QualityGateDecision evidenceRecoveryDecision() {
        QualityGateIssue issue = new QualityGateIssue(
                QualityGateFailureType.EVIDENCE_INVALID,
                QualityGateSource.CITATION_REVIEW,
                "EVIDENCE_CONFLICT",
                "同一指标存在证据冲突"
        );
        return new QualityGateDecision(
                "FAIL",
                QualityGateRoute.COLLECT_MORE_EVIDENCE,
                List.of(issue),
                "EVIDENCE_CONFLICT：同一指标存在证据冲突"
        );
    }
}
