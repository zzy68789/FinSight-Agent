package com.zzy.finsight.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

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
