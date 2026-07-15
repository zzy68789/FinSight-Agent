package com.zzy.finsight.controller;

import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.service.AuthService;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.dto.PageResponse;
import com.zzy.finsight.dto.ReportResponse;
import com.zzy.finsight.dto.ReportIndexResponse;
import com.zzy.finsight.dto.TaskDetailResponse;
import com.zzy.finsight.dto.TaskSummaryResponse;
import com.zzy.finsight.dto.report.ExportedReport;
import com.zzy.finsight.service.ReportExportService;
import com.zzy.finsight.service.TaskQueryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = TaskQueryController.class, properties = "finsight.auth.enabled=false")
class TaskQueryControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    TaskQueryService taskQueryService;

    @MockitoBean
    ReportExportService reportExportService;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @Test
    void listTasksReturnsPagedApiResponse() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        TaskSummaryResponse task = new TaskSummaryResponse(1L, "thread-1", "A股股票投研报告：600519", "stock-hybrid", "COMPLETED", 0, now, now);
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.listTasks(7L, 1, 10, "COMPLETED", "600519"))
                .thenReturn(new PageResponse<>(List.of(task), 1, 10, 1L));

        mockMvc.perform(get("/api/tasks")
                        .param("page", "1")
                        .param("size", "10")
                        .param("status", "COMPLETED")
                        .param("keyword", "600519"))
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
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.getTask(7L, 1L))
                .thenReturn(new TaskDetailResponse(1L, "thread-1", "A股股票投研报告：600519", "stock-hybrid", "COMPLETED", 0, now, now));

        mockMvc.perform(get("/api/tasks/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.status").value("COMPLETED"));
    }

    @Test
    void getTaskLogsReturnsAgentStepLogs() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        AgentStepLogResponse log = new AgentStepLogResponse(11L, 1L, "stock_resolve", null, "{\"ticker\":\"600519.SH\"}", "SUCCESS", null, now);
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.getTaskLogs(7L, 1L)).thenReturn(List.of(log));

        mockMvc.perform(get("/api/tasks/1/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(11))
                .andExpect(jsonPath("$.data[0].stepName").value("stock_resolve"));
    }

    @Test
    void getThreadReportsReturnsReportVersions() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        ReportResponse report = new ReportResponse(21L, 1L, "thread-1", "report", 1, "PASS", "", now, false, null);
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.getThreadReports(7L, "thread-1")).thenReturn(List.of(report));

        mockMvc.perform(get("/api/threads/thread-1/reports"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value(21))
                .andExpect(jsonPath("$.data[0].version").value(1));
    }

    @Test
    void getReportReturnsReportDetail() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.getReport(7L, 21L))
                .thenReturn(new ReportResponse(21L, 1L, "thread-1", "report", 1, "PASS", "", now, false, null));

        mockMvc.perform(get("/api/reports/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value(21))
                .andExpect(jsonPath("$.data.content").value("report"));
    }

    @Test
    void listReportsReturnsReportLibrary() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.listReports(7L, "agent", true))
                .thenReturn(List.of(new ReportResponse(21L, 1L, "thread-1", "report", 1, "PASS", "", now, true, null)));

        mockMvc.perform(get("/api/reports")
                        .param("keyword", "agent")
                        .param("favoriteOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].favorite").value(true));
    }

    @Test
    void exportReportReturnsDownloadableFile() throws Exception {
        ReportResponse report = new ReportResponse(21L, 1L, "thread-1", "# Report", 1, "PASS", "", LocalDateTime.now(), false, null);
        ExportedReport exported = new ExportedReport(
                "report-thread-1-v1.pdf",
                "application/pdf",
                "%PDF".getBytes()
        );
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.getReport(7L, 21L)).thenReturn(report);
        when(reportExportService.export(report, "pdf")).thenReturn(exported);

        mockMvc.perform(get("/api/reports/21/export").param("format", "pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report-thread-1-v1.pdf\""));
    }

    @Test
    void updateFavoriteUsesCurrentUser() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 24, 10, 0);
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.updateFavorite(7L, 21L, true))
                .thenReturn(new ReportResponse(21L, 1L, "thread-1", "report", 1, "PASS", "", now, true, null));

        mockMvc.perform(post("/api/reports/21/favorite").param("favorite", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.favorite").value(true));
    }

    @Test
    void deleteReportUsesCurrentUser() throws Exception {
        when(userContext.currentUserId()).thenReturn(7L);

        mockMvc.perform(delete("/api/reports/21"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    void indexReportUsesCurrentUser() throws Exception {
        when(userContext.currentUserId()).thenReturn(7L);
        when(taskQueryService.indexReportToKnowledgeBase(7L, 21L))
                .thenReturn(new ReportIndexResponse(21L, 2, "indexed"));

        mockMvc.perform(post("/api/reports/21/knowledge-base"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(21))
                .andExpect(jsonPath("$.data.chunksStored").value(2));
    }
}
