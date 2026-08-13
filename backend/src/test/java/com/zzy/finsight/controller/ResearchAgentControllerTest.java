package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchRunCreatedResponse;
import com.zzy.finsight.service.AuthService;
import com.zzy.finsight.service.ResearchAgentService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@WebMvcTest(value = ResearchAgentController.class, properties = "finsight.auth.enabled=false")
class ResearchAgentControllerTest {
    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    ResearchAgentService researchAgentService;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @Test
    void createsResearchRunWithNaturalLanguageQuestion() throws Exception {
        when(userContext.currentUserId()).thenReturn(7L);
        when(researchAgentService.create(any(Long.class), any(ResearchRunRequest.class), any(String.class)))
                .thenReturn(new ResearchRunCreatedResponse(19L, "agent-thread", "CREATED", false));

        mockMvc.perform(post("/api/research-runs")
                        .header("Idempotency-Key", "request-12345678")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticker": "600519",
                                  "research_question": "最近两个季度毛利率变化的主要原因是什么？",
                                  "research_intent": "FINANCIAL_QUALITY",
                                  "thread_id": "agent-thread",
                                  "as_of_date": "2026-08-12",
                                  "time_horizon": "2Y",
                                  "research_depth": "standard",
                                  "search_mode": "hybrid"
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.data.taskId").value(19L))
                .andExpect(jsonPath("$.data.threadId").value("agent-thread"))
                .andExpect(jsonPath("$.data.reused").value(false));

        ArgumentCaptor<ResearchRunRequest> captor = ArgumentCaptor.forClass(ResearchRunRequest.class);
        verify(researchAgentService).create(
                org.mockito.ArgumentMatchers.eq(7L), captor.capture(),
                org.mockito.ArgumentMatchers.eq("request-12345678")
        );
        assertThat(captor.getValue().getResearchQuestion()).contains("毛利率");
        assertThat(captor.getValue().getResearchIntent().name()).isEqualTo("FINANCIAL_QUALITY");
        assertThat(captor.getValue().getAsOfDate()).hasToString("2026-08-12");
    }

    @Test
    void rejectsUnknownResearchIntent() throws Exception {
        mockMvc.perform(post("/api/research-runs")
                        .header("Idempotency-Key", "request-12345678")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticker": "600519",
                                  "research_question": "分析盈利质量",
                                  "research_intent": "UNKNOWN_INTENT"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsResearchRunWithoutIdempotencyKey() throws Exception {
        mockMvc.perform(post("/api/research-runs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticker": "600519",
                                  "research_question": "分析盈利质量"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void resumesAgentEventsFromLastEventId() throws Exception {
        when(userContext.currentUserId()).thenReturn(7L);
        when(researchAgentService.subscribe(7L, 19L, 8L)).thenReturn(new SseEmitter(0L));

        mockMvc.perform(get("/api/research-runs/19/events")
                        .header("Last-Event-ID", "8")
                        .param("afterSequence", "5"))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        verify(researchAgentService).subscribe(7L, 19L, 8L);
    }
}
