package com.zzy.finsight.repository;

import com.zzy.finsight.domain.ResearchTaskRecord;
import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class ResearchTaskRepository {
    private static final RowMapper<ResearchTaskRecord> TASK_MAPPER = (rs, rowNum) -> new ResearchTaskRecord(
            rs.getLong("id"),
            rs.getString("thread_id"),
            rs.getString("query"),
            rs.getString("search_mode"),
            rs.getString("status"),
            rs.getInt("revision_number"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );
    private static final RowMapper<WorkflowTaskExecutionRecord> EXECUTION_MAPPER = (rs, rowNum) -> new WorkflowTaskExecutionRecord(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("thread_id"),
            rs.getString("status"),
            rs.getString("stage"),
            rs.getInt("attempt_count"),
            rs.getString("request_payload"),
            rs.getString("last_error"),
            rs.getObject("heartbeat_at", LocalDateTime.class),
            rs.getString("lease_owner"),
            rs.getObject("lease_until", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public ResearchTaskRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public long create(long ownerId, String threadId, String query, String searchMode) {
        return create(ownerId, threadId, query, searchMode, null);
    }

    public long create(long ownerId, String threadId, String query, String searchMode, String requestPayload) {
        LocalDateTime now = LocalDateTime.now();
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO research_task(
                      owner_id, thread_id, query, search_mode, status, revision_number,
                      stage, attempt_count, request_payload, created_at, updated_at
                    )
                    VALUES (?, ?, ?, ?, ?, 0, 'CREATED', 0, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            statement.setLong(1, ownerId);
            statement.setString(2, threadId);
            statement.setString(3, query);
            statement.setString(4, searchMode);
            statement.setString(5, "CREATED");
            statement.setString(6, requestPayload);
            statement.setObject(7, now);
            statement.setObject(8, now);
            return statement;
        }, keyHolder);
        Number key = keyHolder.getKey();
        if (key == null) {
            throw new IllegalStateException("创建调研任务后未返回生成主键");
        }
        return key.longValue();
    }

    public void markRunning(long taskId) {
        updateStatus(taskId, "RUNNING");
    }

    public boolean startAttempt(long taskId, String leaseOwner, LocalDateTime leaseUntil) {
        LocalDateTime now = LocalDateTime.now();
        return jdbcClient.sql("""
                        UPDATE research_task
                        SET status = 'RUNNING',
                            attempt_count = attempt_count + 1,
                            heartbeat_at = :now,
                            lease_owner = :leaseOwner,
                            lease_until = :leaseUntil,
                            last_error = NULL,
                            updated_at = :now
                        WHERE id = :id
                          AND attempt_count < 3
                          AND status IN ('CREATED', 'RETRYING')
                          AND (lease_until IS NULL OR lease_until < :now OR lease_owner = :leaseOwner)
                        """)
                .param("now", now)
                .param("leaseOwner", leaseOwner)
                .param("leaseUntil", leaseUntil)
                .param("id", taskId)
                .update() == 1;
    }

    public void updateStage(long taskId, String stage, String leaseOwner, LocalDateTime leaseUntil) {
        LocalDateTime now = LocalDateTime.now();
        jdbcClient.sql("""
                        UPDATE research_task
                        SET stage = :stage, heartbeat_at = :now, lease_until = :leaseUntil, updated_at = :now
                        WHERE id = :id AND status = 'RUNNING' AND lease_owner = :leaseOwner
                        """)
                .param("stage", stage)
                .param("now", now)
                .param("leaseUntil", leaseUntil)
                .param("id", taskId)
                .param("leaseOwner", leaseOwner)
                .update();
    }

    public void markCompleted(long taskId) {
        finish(taskId, "COMPLETED", "COMPLETED", null);
    }

    public void markFailed(long taskId) {
        markFailed(taskId, null);
    }

    public void markFailed(long taskId, String error) {
        finish(taskId, "FAILED", "FAILED", error);
    }

    public boolean markRetrying(long taskId, String expectedStatus) {
        return jdbcClient.sql("""
                        UPDATE research_task
                        SET status = 'RETRYING', stage = 'RETRYING', lease_owner = NULL, lease_until = NULL, updated_at = :now
                        WHERE id = :id AND status = :expectedStatus AND attempt_count < 3
                        """)
                .param("now", LocalDateTime.now())
                .param("id", taskId)
                .param("expectedStatus", expectedStatus)
                .update() == 1;
    }

    public boolean markStaleRetrying(long taskId, LocalDateTime heartbeatBefore) {
        return jdbcClient.sql("""
                        UPDATE research_task
                        SET status = 'RETRYING', stage = 'RETRYING', lease_owner = NULL, lease_until = NULL, updated_at = :now
                        WHERE id = :id
                          AND status = 'RUNNING'
                          AND attempt_count < 3
                          AND COALESCE(heartbeat_at, updated_at) < :heartbeatBefore
                        """)
                .param("now", LocalDateTime.now())
                .param("id", taskId)
                .param("heartbeatBefore", heartbeatBefore)
                .update() == 1;
    }

    public Optional<WorkflowTaskExecutionRecord> findExecution(long ownerId, long taskId) {
        return jdbcTemplate.query("""
                        SELECT id, owner_id, thread_id, status, stage, attempt_count, request_payload,
                               last_error, heartbeat_at, lease_owner, lease_until, updated_at
                        FROM research_task
                        WHERE owner_id = ? AND id = ?
                        """,
                EXECUTION_MAPPER,
                ownerId,
                taskId
        ).stream().findFirst();
    }

    public List<WorkflowTaskExecutionRecord> findStaleRunning(LocalDateTime heartbeatBefore, int limit) {
        return jdbcTemplate.query("""
                        SELECT id, owner_id, thread_id, status, stage, attempt_count, request_payload,
                               last_error, heartbeat_at, lease_owner, lease_until, updated_at
                        FROM research_task
                        WHERE status = 'RUNNING'
                          AND search_mode LIKE 'stock-%'
                          AND COALESCE(heartbeat_at, updated_at) < ?
                        ORDER BY updated_at ASC
                        LIMIT ?
                        """,
                EXECUTION_MAPPER,
                heartbeatBefore,
                Math.max(1, limit)
        );
    }

    private void finish(long taskId, String status, String stage, String error) {
        LocalDateTime now = LocalDateTime.now();
        jdbcClient.sql("""
                        UPDATE research_task
                        SET status = :status, stage = :stage, last_error = :error,
                            heartbeat_at = :now, lease_owner = NULL, lease_until = NULL, updated_at = :now
                        WHERE id = :id
                        """)
                .param("status", status)
                .param("stage", stage)
                .param("error", error)
                .param("now", now)
                .param("id", taskId)
                .update();
    }

    private void updateStatus(long taskId, String status) {
        jdbcClient.sql("UPDATE research_task SET status = :status, updated_at = :now WHERE id = :id")
                .param("status", status)
                .param("now", LocalDateTime.now())
                .param("id", taskId)
                .update();
    }

    public List<ResearchTaskRecord> findPage(long ownerId, int page, int size, String status, String keyword) {
        QueryParts queryParts = buildFilter(ownerId, status, keyword);
        List<Object> params = new ArrayList<>(queryParts.params());
        params.add(size);
        params.add((page - 1) * size);
        return jdbcTemplate.query("""
                        SELECT id, thread_id, query, search_mode, status, revision_number, created_at, updated_at
                        FROM research_task
                        %s
                        ORDER BY updated_at DESC, id DESC
                        LIMIT ? OFFSET ?
                        """.formatted(queryParts.whereClause()),
                TASK_MAPPER,
                params.toArray()
        );
    }

    public long count(long ownerId, String status, String keyword) {
        QueryParts queryParts = buildFilter(ownerId, status, keyword);
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM research_task " + queryParts.whereClause(),
                Long.class,
                queryParts.params().toArray()
        );
        return count == null ? 0L : count;
    }

    public Optional<ResearchTaskRecord> findById(long ownerId, long taskId) {
        return jdbcTemplate.query("""
                        SELECT id, thread_id, query, search_mode, status, revision_number, created_at, updated_at
                        FROM research_task
                        WHERE owner_id = ? AND id = ?
                        """,
                TASK_MAPPER,
                ownerId,
                taskId
        ).stream().findFirst();
    }

    private QueryParts buildFilter(long ownerId, String status, String keyword) {
        List<String> conditions = new ArrayList<>(List.of("owner_id = ?"));
        List<Object> params = new ArrayList<>(List.of(ownerId));
        if (status != null && !status.isBlank()) {
            conditions.add("status = ?");
            params.add(status);
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(query LIKE ? OR thread_id LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
        }
        String whereClause = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new QueryParts(whereClause, params);
    }

    private record QueryParts(String whereClause, List<Object> params) {
    }
}
