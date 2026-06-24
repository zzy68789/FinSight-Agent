package com.zzy.drai.controller;

import com.zzy.drai.dto.ApiResponse;
import com.zzy.drai.dto.AgentStepLogResponse;
import com.zzy.drai.dto.PageResponse;
import com.zzy.drai.dto.ReportResponse;
import com.zzy.drai.dto.TaskDetailResponse;
import com.zzy.drai.dto.TaskSummaryResponse;
import com.zzy.drai.service.TaskQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TaskQueryController.class)
class TaskQueryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskQueryService taskQueryService;

    @Test
    void listTasksReturnsPagedApiResponse() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        TaskSummaryResponse task = new TaskSummaryResponse(1L, "thread-1", "AI Agent", "hybrid", "COMPLETED", 0, now, now);
        when(taskQueryService.listTasks(1, 10, "COMPLETED", "agent"))
                .thenReturn(new PageResponse<>(List.of(task), 1, 10, 1L));

        mockMvc.perform(get("/api/tasks")
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "COMPLETED")
                        .param("keyword", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.message").value("success"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(10))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.items[0].id").value(1))
                .andExpect(jsonPath("$.data.items[0].threadId").value("thread-1"));
    }

    @Test
    void getTaskReturnsTaskDetail() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        when(taskQueryService.getTask(1L))
                .thenReturn(new TaskDetailResponse(1L, "thread-1", "AI Agent", "hybrid", "COMPLETED", 0, now, now));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void getTaskLogsReturnsAgentStepLogs() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        AgentStepLogResponse log = new AgentStepLogResponse(11L, 1L, "planner", null, "{\"plan\":[]}", "SUCCESS", null, now);
        when(taskQueryService.getTaskLogs(1L)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/tasks/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].stepName").value("planner"));
    }

    @Test
    void getThreadReportsReturnsReportVersions() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        ReportResponse report = new ReportResponse(21L, 1L, "thread-1", "report", 1, "PASS", "", now);
        when(taskQueryService.getThreadReports("thread-1")).thenReturn(List.of(report));

        mockMvc.perform(get("/api/threads/thread-1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].version").value(1));
    }

    @Test
    void getReportReturnsReportDetail() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        when(taskQueryService.getReport(21L))
                .thenReturn(new ReportResponse(21L, 1L, "thread-1", "report", 1, "PASS", "", now));

        mockMvc.perform(get("/api/reports/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.content").value("report"));
    }
}
