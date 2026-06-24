package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.llm.LlmClient;
import com.zzy.drai.llm.PromptTemplates;
import com.zzy.drai.service.ReportService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WriterNode {
    private final LlmClient llmClient;
    private final ReportService reportService;

    public WriterNode(LlmClient llmClient, ReportService reportService) {
        this.llmClient = llmClient;
        this.reportService = reportService;
    }

    public Map<String, Object> apply(ResearchState state) {
        String report = llmClient.generate(
                PromptTemplates.writer(state.query(), state.searchResults(), state.critique()),
                LlmClient.ModelType.FAST
        );
        reportService.saveLatest(state.threadId(), taskId(state), report, state.reviewStatus(), state.critique());
        return Map.of(ResearchState.FINAL_REPORT, report);
    }

    private long taskId(ResearchState state) {
        return state.value(ResearchState.TASK_ID)
                .map(value -> value instanceof Number number ? number.longValue() : Long.parseLong(value.toString()))
                .orElse(0L);
    }
}
