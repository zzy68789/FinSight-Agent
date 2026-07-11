package com.zzy.finsight.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class AdminAuditLogRepository {
    private final JdbcClient jdbcClient;

    public AdminAuditLogRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(long adminUserId, String action, String targetType, long targetId, String detail) {
        jdbcClient.sql("""
                        INSERT INTO admin_audit_log(admin_user_id, action, target_type, target_id, detail, created_at)
                        VALUES (:adminUserId, :action, :targetType, :targetId, :detail, :createdAt)
                        """)
                .param("adminUserId", adminUserId)
                .param("action", action)
                .param("targetType", targetType)
                .param("targetId", targetId)
                .param("detail", detail)
                .param("createdAt", LocalDateTime.now())
                .update();
    }
}
