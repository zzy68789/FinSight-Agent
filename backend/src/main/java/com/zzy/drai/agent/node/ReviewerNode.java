package com.zzy.drai.agent.node;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.llm.LlmClient;
import com.zzy.drai.llm.PromptTemplates;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class ReviewerNode {
    private final LlmClient llmClient;
    private final ObjectMapper objectMapper;

    public ReviewerNode(LlmClient llmClient, ObjectMapper objectMapper) {
        this.llmClient = llmClient;
        this.objectMapper = objectMapper;
    }

    public Map<String, Object> apply(ResearchState state) {
        String raw = llmClient.generate(
                PromptTemplates.reviewer(state.query(), state.finalReport().orElse("")),
                LlmClient.ModelType.SMART
        );
        Review review = parse(raw);
        return Map.of(
                ResearchState.REVIEW_STATUS, review.status(),
                ResearchState.CRITIQUE, review.feedback(),
                ResearchState.REVISION_NUMBER, state.revisionNumber() + 1
        );
    }

    private Review parse(String raw) {
        try {
            String json = raw.substring(raw.indexOf('{'), raw.lastIndexOf('}') + 1);
            JsonNode node = objectMapper.readTree(json);
            String status = node.path("status").asText("FAIL");
            String feedback = node.path("feedback").asText("");
            return new Review(status, feedback);
        } catch (Exception e) {
            return new Review("FAIL", "审查器输出格式异常，请重新生成结构更清晰的报告。");
        }
    }

    private record Review(String status, String feedback) {
    }
}
