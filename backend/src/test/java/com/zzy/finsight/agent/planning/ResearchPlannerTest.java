package com.zzy.finsight.agent.planning;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.tool.ResearchToolRegistry;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
