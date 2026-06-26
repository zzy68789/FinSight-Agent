package com.zzy.drai.controller;

import com.zzy.drai.auth.AuthenticatedUser;
import com.zzy.drai.auth.UserContext;
import com.zzy.drai.dto.AdminReportResponse;
import com.zzy.drai.dto.AdminSystemHealthResponse;
import com.zzy.drai.dto.AdminTaskResponse;
import com.zzy.drai.dto.AdminUserResponse;
import com.zzy.drai.dto.AgentStepLogResponse;
import com.zzy.drai.dto.ApiResponse;
import com.zzy.drai.service.AdminService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final UserContext userContext;

    public AdminController(AdminService adminService, UserContext userContext) {
        this.adminService = adminService;
        this.userContext = userContext;
    }

    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> listUsers(@RequestParam(required = false) String keyword) {
        requireAdmin();
        return ApiResponse.success(adminService.listUsers(keyword));
    }

    @PatchMapping("/users/{userId}/role")
    public ApiResponse<AdminUserResponse> updateUserRole(@PathVariable long userId, @RequestParam String role) {
        AuthenticatedUser admin = requireAdmin();
        return ApiResponse.success(adminService.updateUserRole(admin.userId(), userId, role));
    }

    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(@PathVariable long userId, @RequestParam String status) {
        AuthenticatedUser admin = requireAdmin();
        return ApiResponse.success(adminService.updateUserStatus(admin.userId(), userId, status));
    }

    @GetMapping("/tasks")
    public ApiResponse<List<AdminTaskResponse>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String keyword
    ) {
        requireAdmin();
        return ApiResponse.success(adminService.listTasks(status, ownerId, keyword));
    }

    @GetMapping("/tasks/{taskId}/logs")
    public ApiResponse<List<AgentStepLogResponse>> getTaskLogs(@PathVariable long taskId) {
        requireAdmin();
        return ApiResponse.success(adminService.getTaskLogs(taskId));
    }

    @GetMapping("/reports")
    public ApiResponse<List<AdminReportResponse>> listReports(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String keyword
    ) {
        requireAdmin();
        return ApiResponse.success(adminService.listReports(ownerId, keyword));
    }

    @DeleteMapping("/reports/{reportId}")
    public ApiResponse<Void> deleteReport(@PathVariable long reportId) {
        AuthenticatedUser admin = requireAdmin();
        adminService.deleteReport(admin.userId(), reportId);
        return ApiResponse.success(null);
    }

    @GetMapping("/system/health")
    public ApiResponse<AdminSystemHealthResponse> systemHealth() {
        requireAdmin();
        return ApiResponse.success(adminService.systemHealth());
    }

    private AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = userContext.currentUser();
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return user;
    }
}
