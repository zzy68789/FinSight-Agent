package com.zzy.finsight.controller;

import com.zzy.finsight.service.AuthService;
import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.service.StockReportService;
import com.zzy.finsight.service.SseService;
import com.zzy.finsight.service.impl.SseServiceImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = StockReportController.class, properties = "finsight.auth.enabled=false")
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

    @Test
    void stockReportEndpointStreamsBusinessErrorInsteadOfHttp500() throws Exception {
        when(userContext.currentUserId()).thenReturn(7L);
        doAnswer(invocation -> {
            SseEmitter emitter = invocation.getArgument(2);
            new SseServiceImpl().error(emitter, new IllegalArgumentException("当前仅支持沪深 A 股普通股票代码"));
            return null;
        }).when(stockReportService).run(any(Long.class), any(StockReportRequest.class), any(SseEmitter.class));

        MvcResult result = mockMvc.perform(post("/api/stock-reports")
                        .contentType("application/json")
                        .content("""
                                {
                                  "ticker": "588200",
                                  "thread_id": "thread-1",
                                  "report_period": "latest",
                                  "search_mode": "hybrid"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(result))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"step\":\"error\"")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("\"message\"")));
    }
}
