package com.zzy.finsight.controller;

import com.zzy.finsight.auth.AuthService;
import com.zzy.finsight.auth.AuthenticatedUser;
import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.AdminReportResponse;
import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.dto.AdminTaskResponse;
import com.zzy.finsight.dto.AdminUserResponse;
import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.service.AdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(value = AdminController.class, properties = "finsight.auth.enabled=false")
class AdminControllerTest {

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    AdminService adminService;

    @MockitoBean
    UserContext userContext;

    @MockitoBean
    AuthService authService;

    @Test
    void normalUserCannotAccessAdminApis() throws Exception {
        when(userContext.currentUser()).thenReturn(new AuthenticatedUser(7L, "alice", "alice@example.com", "USER", ""));

        mockMvc.perform(get("/api/admin/users"))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminCanListUsers() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        when(userContext.currentUser()).thenReturn(new AuthenticatedUser(1L, "root", "root@example.com", "ADMIN", ""));
        when(adminService.listUsers("ali"))
                .thenReturn(List.of(new AdminUserResponse(7L, "alice", "alice@example.com", "USER", "ACTIVE", now, now, null)));

        mockMvc.perform(get("/api/admin/users").param("keyword", "ali"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].id").value(7))
                .andExpect(jsonPath("$.data[0].username").value("alice"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    @Test
    void adminCanChangeUserRoleAndStatus() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        when(userContext.currentUser()).thenReturn(new AuthenticatedUser(1L, "root", "root@example.com", "ADMIN", ""));
        when(adminService.updateUserRole(1L, 7L, "ADMIN"))
                .thenReturn(new AdminUserResponse(7L, "alice", "alice@example.com", "ADMIN", "ACTIVE", now, now, null));
        when(adminService.updateUserStatus(1L, 7L, "DISABLED"))
                .thenReturn(new AdminUserResponse(7L, "alice", "alice@example.com", "ADMIN", "DISABLED", now, now, null));

        mockMvc.perform(patch("/api/admin/users/7/role").param("role", "ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.role").value("ADMIN"));

        mockMvc.perform(patch("/api/admin/users/7/status").param("status", "DISABLED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("DISABLED"));
    }

    @Test
    void adminCanMonitorGlobalTasksAndLogs() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        when(userContext.currentUser()).thenReturn(new AuthenticatedUser(1L, "root", "root@example.com", "ADMIN", ""));
        when(adminService.listTasks("FAILED", 7L, "agent"))
                .thenReturn(List.of(new AdminTaskResponse(11L, 7L, "alice", "thread-1", "agent", "hybrid", "FAILED", 0, now, now)));
        when(adminService.getTaskLogs(11L))
                .thenReturn(List.of(new AgentStepLogResponse(31L, 11L, "writer", null, "{}", "FAILED", "boom", now)));

        mockMvc.perform(get("/api/admin/tasks")
                        .param("status", "FAILED")
                        .param("ownerId", "7")
                        .param("keyword", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ownerUsername").value("alice"))
                .andExpect(jsonPath("$.data[0].status").value("FAILED"));

        mockMvc.perform(get("/api/admin/tasks/11/logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].stepName").value("writer"));
    }

    @Test
    void adminCanManageReportsAndReadSystemHealth() throws Exception {
        LocalDateTime now = LocalDateTime.of(2026, 6, 26, 10, 0);
        when(userContext.currentUser()).thenReturn(new AuthenticatedUser(1L, "root", "root@example.com", "ADMIN", ""));
        when(adminService.listReports(7L, "agent"))
                .thenReturn(List.of(new AdminReportResponse(21L, 7L, "alice", 11L, "thread-1", "agent report", 2, "PASS", false, null, now)));
        when(adminService.systemHealth())
                .thenReturn(new AdminSystemHealthResponse(Map.of(
                        "mysql", "UP",
                        "redis", "UNKNOWN",
                        "chroma", "UNKNOWN",
                        "llm", "CONFIGURED",
                        "tavily", "MISSING"
                )));

        mockMvc.perform(get("/api/admin/reports")
                        .param("ownerId", "7")
                        .param("keyword", "agent"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].ownerUsername").value("alice"))
                .andExpect(jsonPath("$.data[0].version").value(2));

        mockMvc.perform(delete("/api/admin/reports/21"))
                .andExpect(status().isOk());
        verify(adminService).deleteReport(1L, 21L);

        mockMvc.perform(get("/api/admin/system/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.components.mysql").value("UP"));
    }
}
