package com.zzy.drai.agent.node;

import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.llm.LlmClient;
import com.zzy.drai.llm.PromptTemplates;
import com.zzy.drai.service.ReportService;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class RefinerNode {
    private final LlmClient llmClient;
    private final ReportService reportService;

    public RefinerNode(LlmClient llmClient, ReportService reportService) {
        this.llmClient = llmClient;
        this.reportService = reportService;
    }

    public Map<String, Object> apply(ResearchState state) {
        String oldReport = reportService.findLatestByThread(state.threadId()).orElse("");
        String newReport = llmClient.generate(PromptTemplates.refine(oldReport, state.query()), LlmClient.ModelType.FAST);
        reportService.saveLatest(state.threadId(), taskId(state), newReport, "PASS", "");
        return Map.of(
                ResearchState.FINAL_REPORT, newReport,
                ResearchState.REVIEW_STATUS, "PASS"
        );
    }

    private long taskId(ResearchState state) {
        return state.value(ResearchState.TASK_ID)
                .map(value -> value instanceof Number number ? number.longValue() : Long.parseLong(value.toString()))
                .orElse(0L);
    }
}
