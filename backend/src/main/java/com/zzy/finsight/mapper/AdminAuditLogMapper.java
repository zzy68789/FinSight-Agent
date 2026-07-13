package com.zzy.finsight.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

/**
 * 定义管理员审计日志的 MyBatis 数据访问操作。
 */
@Mapper
public interface AdminAuditLogMapper {
    default void save(long adminUserId, String action, String targetType, long targetId, String detail) {
        insert(adminUserId, action, targetType, targetId, detail, LocalDateTime.now());
    }

    int insert(
            @Param("adminUserId") long adminUserId,
            @Param("action") String action,
            @Param("targetType") String targetType,
            @Param("targetId") long targetId,
            @Param("detail") String detail,
            @Param("createdAt") LocalDateTime createdAt
    );
}
