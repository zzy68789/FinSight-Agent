package com.zzy.drai.repository;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public class ReportRepository {
    private final JdbcClient jdbcClient;

    public ReportRepository(JdbcClient jdbcClient) {
        this.jdbcClient = jdbcClient;
    }

    public void save(long taskId, String threadId, String content, String reviewStatus, String critique) {
        Integer latestVersion = jdbcClient.sql("SELECT COALESCE(MAX(version), 0) FROM report WHERE thread_id = :threadId")
                .param("threadId", threadId)
                .query(Integer.class)
                .single();
        jdbcClient.sql("""
                        INSERT INTO report(task_id, thread_id, content, version, review_status, critique, created_at)
                        VALUES (:taskId, :threadId, :content, :version, :reviewStatus, :critique, :createdAt)
                        """)
                .param("taskId", taskId)
                .param("threadId", threadId)
                .param("content", content)
                .param("version", latestVersion + 1)
                .param("reviewStatus", reviewStatus)
                .param("critique", critique)
                .param("createdAt", LocalDateTime.now())
                .update();
    }

    public Optional<String> findLatestByThread(String threadId) {
        return jdbcClient.sql("""
                        SELECT content FROM report
                        WHERE thread_id = :threadId
                        ORDER BY version DESC, id DESC
                        LIMIT 1
                        """)
                .param("threadId", threadId)
                .query(String.class)
                .optional();
    }
}
