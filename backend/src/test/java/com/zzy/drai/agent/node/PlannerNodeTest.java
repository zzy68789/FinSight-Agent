package com.zzy.drai.agent.node;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.agent.tool.PlannerAgent;
import com.zzy.drai.llm.LlmClient;
import com.zzy.drai.llm.PlanParser;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlannerNodeTest {

    private final PlanParser planParser = new PlanParser(new ObjectMapper());

    @Test
    void firstPassUsesAgentToPlan() {
        PlannerAgent agent = mock(PlannerAgent.class);
        LlmClient llmClient = mock(LlmClient.class);
        when(agent.plan("agent workflow")).thenReturn("子问题A\n子问题B\n子问题C");
        PlannerNode node = new PlannerNode(agent, llmClient, planParser);

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow"
        )));

        @SuppressWarnings("unchecked")
        List<String> plan = (List<String>) result.get(ResearchState.PLAN);
        assertThat(plan).containsExactly("子问题A", "子问题B", "子问题C");
        verify(llmClient, never()).generate(anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void retryRoundWithCritiqueUsesDeterministicPromptNotAgent() {
        PlannerAgent agent = mock(PlannerAgent.class);
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generate(anyString(), eq(LlmClient.ModelType.FAST)))
                .thenReturn("修订子问题1\n修订子问题2");
        PlannerNode node = new PlannerNode(agent, llmClient, planParser);

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow",
                ResearchState.CRITIQUE, "需要补充风险方向"
        )));

        @SuppressWarnings("unchecked")
        List<String> plan = (List<String>) result.get(ResearchState.PLAN);
        assertThat(plan).containsExactly("修订子问题1", "修订子问题2");
        verify(agent, never()).plan(anyString());
    }

    @Test
    void fallsBackToDeterministicPlanWhenAgentThrows() {
        PlannerAgent agent = mock(PlannerAgent.class);
        LlmClient llmClient = mock(LlmClient.class);
        when(agent.plan(anyString())).thenThrow(new RuntimeException("LLM error"));
        when(llmClient.generate(anyString(), eq(LlmClient.ModelType.FAST)))
                .thenReturn("降级子问题1\n降级子问题2");
        PlannerNode node = new PlannerNode(agent, llmClient, planParser);

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow"
        )));

        @SuppressWarnings("unchecked")
        List<String> plan = (List<String>) result.get(ResearchState.PLAN);
        assertThat(plan).containsExactly("降级子问题1", "降级子问题2");
    }

    @Test
    void worksWithoutAgentConfigured() {
        LlmClient llmClient = mock(LlmClient.class);
        when(llmClient.generate(anyString(), eq(LlmClient.ModelType.FAST)))
                .thenReturn("无agent子问题1\n无agent子问题2");
        PlannerNode node = new PlannerNode(null, llmClient, planParser);

        Map<String, Object> result = node.apply(new ResearchState(Map.of(
                ResearchState.QUERY, "agent workflow"
        )));

        @SuppressWarnings("unchecked")
        List<String> plan = (List<String>) result.get(ResearchState.PLAN);
        assertThat(plan).containsExactly("无agent子问题1", "无agent子问题2");
    }
}
