package com.zzy.finsight.service;

import com.zzy.finsight.dto.AdminReportResponse;
import com.zzy.finsight.dto.AdminSystemHealthResponse;
import com.zzy.finsight.dto.AdminTaskResponse;
import com.zzy.finsight.dto.AdminUserResponse;
import com.zzy.finsight.dto.AgentStepLogResponse;

import java.util.List;

/**
 * 定义管理员后台业务操作。
 */
public interface AdminService {
    /** 查询后台用户列表。 */
    List<AdminUserResponse> listUsers(String keyword);

    /** 修改指定用户角色并记录审计日志。 */
    AdminUserResponse updateUserRole(long adminUserId, long userId, String role);

    /** 修改指定用户状态并记录审计日志。 */
    AdminUserResponse updateUserStatus(long adminUserId, long userId, String status);

    /** 按条件查询全局任务列表。 */
    List<AdminTaskResponse> listTasks(String status, Long ownerId, String keyword);

    /** 查询指定任务的步骤日志。 */
    List<AgentStepLogResponse> getTaskLogs(long taskId);

    /** 按条件查询全局报告列表。 */
    List<AdminReportResponse> listReports(Long ownerId, String keyword);

    /** 软删除指定报告并记录审计日志。 */
    void deleteReport(long adminUserId, long reportId);

    /** 汇总数据库和外部依赖配置状态。 */
    AdminSystemHealthResponse systemHealth();
}
