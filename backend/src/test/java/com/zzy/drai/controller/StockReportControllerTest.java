package com.zzy.drai.controller;

import com.zzy.drai.auth.AuthService;
import com.zzy.drai.auth.UserContext;
import com.zzy.drai.financial.StockReportRequest;
import com.zzy.drai.financial.StockReportService;
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

@WebMvcTest(value = StockReportController.class, properties = "drai.auth.enabled=false")
class StockReportControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    StockReportService stockReportService;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @Test
    void stockReportEndpointStartsSseWorkflowForCurrentUser() throws Exception {
        when(userContext.currentUserId()).thenReturn(7L);

        mockMvc.perform(post("/api/stock-reports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticker": "600519",
                                  "thread_id": "thread-1",
                                  "report_period": "latest",
                                  "search_mode": "hybrid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted());

        ArgumentCaptor<StockReportRequest> requestCaptor = ArgumentCaptor.forClass(StockReportRequest.class);
        verify(stockReportService).run(org.mockito.ArgumentMatchers.eq(7L), requestCaptor.capture(), any(SseEmitter.class));
        assertThat(requestCaptor.getValue().getTicker()).isEqualTo("600519");
        assertThat(requestCaptor.getValue().getThreadId()).isEqualTo("thread-1");
    }
}
