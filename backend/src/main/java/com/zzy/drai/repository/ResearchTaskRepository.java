package com.zzy.drai.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class ResearchTaskRepository {
    private final JdbcClient jdbcClient;

    public ResearchTaskRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public long create(String threadId, String query, String searchMode) {
        LocalDateTime now = LocalDateTime.now();
        jdbcClient.sql("""
                        INSERT INTO research_task(thread_id, query, search_mode, status, revision_number, created_at, updated_at)
                        VALUES (:threadId, :query, :searchMode, :status, 0, :now, :now)
                        """)
                .param("threadId", threadId)
                .param("query", query)
                .param("searchMode", searchMode)
                .param("status", "CREATED")
                .param("now", now)
                .update();
        return jdbcClient.sql("SELECT MAX(id) FROM research_task WHERE thread_id = :threadId")
                .param("threadId", threadId)
                .query(Long.class)
                .single();
    }

    public void markRunning(long taskId) {
        updateStatus(taskId, "RUNNING");
    }

    public void markCompleted(long taskId) {
        updateStatus(taskId, "COMPLETED");
    }

    public void markFailed(long taskId) {
        updateStatus(taskId, "FAILED");
    }

    private void updateStatus(long taskId, String status) {
        jdbcClient.sql("UPDATE research_task SET status = :status, updated_at = :now WHERE id = :id")
                .param("status", status)
                .param("now", LocalDateTime.now())
                .param("id", taskId)
                .update();
    }
}
