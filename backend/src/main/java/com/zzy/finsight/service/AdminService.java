package com.zzy.finsight.service;

import com.zzy.finsight.dto.AdminReportResponse;
import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.dto.AdminTaskResponse;
import com.zzy.finsight.dto.AdminUserResponse;
import com.zzy.finsight.dto.AgentStepLogResponse;

import java.util.List;

public interface AdminService {
    List<AdminUserResponse> listUsers(String keyword);

    AdminUserResponse updateUserRole(long adminUserId, long userId, String role);

    AdminUserResponse updateUserStatus(long adminUserId, long userId, String status);

    List<AdminTaskResponse> listTasks(String status, Long ownerId, String keyword);

    List<AgentStepLogResponse> getTaskLogs(long taskId);

    List<AdminReportResponse> listReports(Long ownerId, String keyword);

    void deleteReport(long adminUserId, long reportId);

    AdminSystemHealthResponse systemHealth();
}
