package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

        mockMvc.perform(post("/api/research-runs")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticker": "600519",
                                  "research_question": "最近两个季度毛利率变化的主要原因是什么？",
                                  "thread_id": "agent-thread",
                                  "as_of_date": "2026-08-12",
                                  "time_horizon": "2Y",
                                  "research_depth": "standard",
                                  "search_mode": "hybrid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<ResearchRunRequest> captor = ArgumentCaptor.forClass(ResearchRunRequest.class);
        verify(researchAgentService).run(
                org.mockito.ArgumentMatchers.eq(7L), captor.capture(), any(SseEmitter.class)
        );
        assertThat(captor.getValue().getResearchQuestion()).contains("毛利率");
        assertThat(captor.getValue().getAsOfDate()).hasToString("2026-08-12");
    }
}
