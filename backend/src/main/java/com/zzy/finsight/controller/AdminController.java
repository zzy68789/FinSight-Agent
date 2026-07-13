package com.zzy.finsight.controller;

import com.zzy.finsight.auth.AuthenticatedUser;
import com.zzy.finsight.auth.UserContext;
import com.zzy.finsight.dto.AdminReportResponse;
import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.dto.AdminTaskResponse;
import com.zzy.finsight.dto.AdminUserResponse;
import com.zzy.finsight.dto.AgentStepLogResponse;
import com.zzy.finsight.dto.ApiResponse;
import com.zzy.finsight.service.AdminService;
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

/**
 * 提供管理员用户、任务、报告和系统状态接口。
 */
@RestController
@RequestMapping("/api/admin")
public class AdminController {
    private final AdminService adminService;
    private final UserContext userContext;

    public AdminController(AdminService adminService, UserContext userContext) {
        this.adminService = adminService;
        this.userContext = userContext;
    }

    /** 查询用户列表。 */
    @GetMapping("/users")
    public ApiResponse<List<AdminUserResponse>> listUsers(@RequestParam(required = false) String keyword) {
        requireAdmin();
        return ApiResponse.success(adminService.listUsers(keyword));
    }

    /** 修改指定用户的角色。 */
    @PatchMapping("/users/{userId}/role")
    public ApiResponse<AdminUserResponse> updateUserRole(@PathVariable long userId, @RequestParam String role) {
        AuthenticatedUser admin = requireAdmin();
        return ApiResponse.success(adminService.updateUserRole(admin.userId(), userId, role));
    }

    /** 修改指定用户的启用状态。 */
    @PatchMapping("/users/{userId}/status")
    public ApiResponse<AdminUserResponse> updateUserStatus(@PathVariable long userId, @RequestParam String status) {
        AuthenticatedUser admin = requireAdmin();
        return ApiResponse.success(adminService.updateUserStatus(admin.userId(), userId, status));
    }

    /** 按条件查询全站任务。 */
    @GetMapping("/tasks")
    public ApiResponse<List<AdminTaskResponse>> listTasks(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String keyword
    ) {
        requireAdmin();
        return ApiResponse.success(adminService.listTasks(status, ownerId, keyword));
    }

    /** 查询指定任务的步骤日志。 */
    @GetMapping("/tasks/{taskId}/logs")
    public ApiResponse<List<AgentStepLogResponse>> getTaskLogs(@PathVariable long taskId) {
        requireAdmin();
        return ApiResponse.success(adminService.getTaskLogs(taskId));
    }

    /** 按条件查询全站报告。 */
    @GetMapping("/reports")
    public ApiResponse<List<AdminReportResponse>> listReports(
            @RequestParam(required = false) Long ownerId,
            @RequestParam(required = false) String keyword
    ) {
        requireAdmin();
        return ApiResponse.success(adminService.listReports(ownerId, keyword));
    }

    /** 删除指定报告并记录管理员操作。 */
    @DeleteMapping("/reports/{reportId}")
    public ApiResponse<Void> deleteReport(@PathVariable long reportId) {
        AuthenticatedUser admin = requireAdmin();
        adminService.deleteReport(admin.userId(), reportId);
        return ApiResponse.success(null);
    }

    /** 查询数据库和外部依赖的系统状态。 */
    @GetMapping("/system/health")
    public ApiResponse<AdminSystemHealthResponse> systemHealth() {
        requireAdmin();
        return ApiResponse.success(adminService.systemHealth());
    }

    /** 校验当前用户是否具有管理员权限。 */
    private AuthenticatedUser requireAdmin() {
        AuthenticatedUser user = userContext.currentUser();
        if (!"ADMIN".equalsIgnoreCase(user.role())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Admin role required");
        }
        return user;
    }
}
