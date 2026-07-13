package com.zzy.finsight.mapper;

import com.zzy.finsight.dto.AdminReportResponse;
import com.zzy.finsight.dto.AdminTaskResponse;
import com.zzy.finsight.dto.AdminUserResponse;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Mapper
public interface AdminMapper {
    List<AdminUserResponse> findUsers(@Param("keyword") String keyword);

    default Optional<AdminUserResponse> updateUserRole(long userId, String role) {
        updateRole(userId, role, LocalDateTime.now());
        return findUser(userId);
    }

    int updateRole(
            @Param("userId") long userId,
            @Param("role") String role,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    default Optional<AdminUserResponse> updateUserStatus(long userId, String status) {
        updateStatus(userId, status, LocalDateTime.now());
        return findUser(userId);
    }

    int updateStatus(
            @Param("userId") long userId,
            @Param("status") String status,
            @Param("updatedAt") LocalDateTime updatedAt
    );

    Optional<AdminUserResponse> findUser(@Param("userId") long userId);

    List<AdminTaskResponse> findTasks(
            @Param("status") String status,
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword
    );

    List<AdminReportResponse> findReports(
            @Param("ownerId") Long ownerId,
            @Param("keyword") String keyword
    );

    default void softDeleteReport(long reportId) {
        updateReportDeletedAt(reportId, LocalDateTime.now());
    }

    int updateReportDeletedAt(@Param("reportId") long reportId, @Param("deletedAt") LocalDateTime deletedAt);
}
