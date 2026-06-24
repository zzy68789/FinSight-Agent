package com.zzy.drai.repository;

import com.zzy.drai.domain.ReportRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ReportRepository {
    private static final RowMapper<ReportRecord> REPORT_MAPPER = (rs, rowNum) -> new ReportRecord(
            rs.getLong("id"),
            rs.getObject("task_id", Long.class),
            rs.getString("thread_id"),
            rs.getString("content"),
            rs.getInt("version"),
            rs.getString("review_status"),
            rs.getString("critique"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public ReportRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
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

    public List<ReportRecord> findReportsByThread(String threadId) {
        return jdbcTemplate.query("""
                        SELECT id, task_id, thread_id, content, version, review_status, critique, created_at
                        FROM report
                        WHERE thread_id = ?
                        ORDER BY version DESC, id DESC
                        """,
                REPORT_MAPPER,
                threadId
        );
    }

    public Optional<ReportRecord> findReportById(long reportId) {
        return jdbcTemplate.query("""
                        SELECT id, task_id, thread_id, content, version, review_status, critique, created_at
                        FROM report
                        WHERE id = ?
                        """,
                REPORT_MAPPER,
                reportId
        ).stream().findFirst();
    }
}
