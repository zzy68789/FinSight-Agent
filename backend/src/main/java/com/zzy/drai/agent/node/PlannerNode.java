package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.llm.LlmClient;
import com.zzy.drai.llm.PromptTemplates;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.Map;

@Component
public class PlannerNode {
    private final LlmClient llmClient;

    public PlannerNode(LlmClient llmClient) {
        this.llmClient = llmClient;
    }

    public Map<String, Object> apply(ResearchState state) {
        String raw = llmClient.generate(PromptTemplates.planner(state.query(), state.critique()), LlmClient.ModelType.FAST);
        List<String> plan = Arrays.stream(raw.split("[,，\\n]"))
                .map(String::trim)
                .filter(item -> !item.isBlank())
                .limit(5)
                .toList();
        if (plan.isEmpty()) {
            plan = List.of(state.query(), state.query() + " 最新进展", state.query() + " 技术挑战");
        }
        return Map.of(ResearchState.PLAN, plan);
    }
}
