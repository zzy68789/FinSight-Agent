package com.zzy.drai.repository;

import com.zzy.drai.dto.AdminReportResponse;
import com.zzy.drai.dto.AdminTaskResponse;
import com.zzy.drai.dto.AdminUserResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Repository
public class AdminRepository {
    private static final RowMapper<AdminUserResponse> USER_MAPPER = (rs, rowNum) -> new AdminUserResponse(
            rs.getLong("id"),
            rs.getString("username"),
            rs.getString("email"),
            rs.getString("role"),
            rs.getString("status"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class),
            rs.getObject("last_login_at", LocalDateTime.class)
    );

    private static final RowMapper<AdminTaskResponse> TASK_MAPPER = (rs, rowNum) -> new AdminTaskResponse(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("owner_username"),
            rs.getString("thread_id"),
            rs.getString("query"),
            rs.getString("search_mode"),
            rs.getString("status"),
            rs.getInt("revision_number"),
            rs.getObject("created_at", LocalDateTime.class),
            rs.getObject("updated_at", LocalDateTime.class)
    );

    private static final RowMapper<AdminReportResponse> REPORT_MAPPER = (rs, rowNum) -> new AdminReportResponse(
            rs.getLong("id"),
            rs.getLong("owner_id"),
            rs.getString("owner_username"),
            rs.getObject("task_id", Long.class),
            rs.getString("thread_id"),
            rs.getString("content"),
            rs.getInt("version"),
            rs.getString("review_status"),
            rs.getBoolean("favorite"),
            rs.getObject("indexed_at", LocalDateTime.class),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;

    public AdminRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<AdminUserResponse> findUsers(String keyword) {
        String pattern = toLikePattern(keyword);
        return jdbcClient.sql("""
                        SELECT id, username, email, role, status, created_at, updated_at, last_login_at
                        FROM app_user
                        WHERE (:keyword IS NULL OR username LIKE :keyword OR email LIKE :keyword)
                        ORDER BY created_at DESC, id DESC
                        LIMIT 100
                        """)
                .param("keyword", pattern)
                .query(USER_MAPPER)
                .list();
    }

    public Optional<AdminUserResponse> updateUserRole(long userId, String role) {
        jdbcClient.sql("""
                        UPDATE app_user
                        SET role = :role, updated_at = :updatedAt
                        WHERE id = :userId
                        """)
                .param("role", role)
                .param("updatedAt", LocalDateTime.now())
                .param("userId", userId)
                .update();
        return findUser(userId);
    }

    public Optional<AdminUserResponse> updateUserStatus(long userId, String status) {
        jdbcClient.sql("""
                        UPDATE app_user
                        SET status = :status, updated_at = :updatedAt
                        WHERE id = :userId
                        """)
                .param("status", status)
                .param("updatedAt", LocalDateTime.now())
                .param("userId", userId)
                .update();
        return findUser(userId);
    }

    public Optional<AdminUserResponse> findUser(long userId) {
        return jdbcTemplate.query("""
                        SELECT id, username, email, role, status, created_at, updated_at, last_login_at
                        FROM app_user
                        WHERE id = ?
                        """,
                USER_MAPPER,
                userId
        ).stream().findFirst();
    }

    public List<AdminTaskResponse> findTasks(String status, Long ownerId, String keyword) {
        QueryParts queryParts = buildTaskFilter(status, ownerId, keyword);
        return jdbcTemplate.query("""
                        SELECT t.id, t.owner_id, COALESCE(u.username, 'unknown') AS owner_username,
                               t.thread_id, t.query, t.search_mode, t.status, t.revision_number,
                               t.created_at, t.updated_at
                        FROM research_task t
                        LEFT JOIN app_user u ON u.id = t.owner_id
                        %s
                        ORDER BY t.updated_at DESC, t.id DESC
                        LIMIT 100
                        """.formatted(queryParts.whereClause()),
                TASK_MAPPER,
                queryParts.params().toArray()
        );
    }

    public List<AdminReportResponse> findReports(Long ownerId, String keyword) {
        QueryParts queryParts = buildReportFilter(ownerId, keyword);
        return jdbcTemplate.query("""
                        SELECT r.id, r.owner_id, COALESCE(u.username, 'unknown') AS owner_username,
                               r.task_id, r.thread_id, r.content, r.version, r.review_status,
                               r.favorite, r.indexed_at, r.created_at
                        FROM report r
                        LEFT JOIN app_user u ON u.id = r.owner_id
                        %s
                        ORDER BY r.created_at DESC, r.id DESC
                        LIMIT 100
                        """.formatted(queryParts.whereClause()),
                REPORT_MAPPER,
                queryParts.params().toArray()
        );
    }

    public void softDeleteReport(long reportId) {
        jdbcClient.sql("""
                        UPDATE report
                        SET deleted_at = :deletedAt
                        WHERE id = :reportId AND deleted_at IS NULL
                        """)
                .param("deletedAt", LocalDateTime.now())
                .param("reportId", reportId)
                .update();
    }

    private QueryParts buildTaskFilter(String status, Long ownerId, String keyword) {
        List<String> conditions = new ArrayList<>();
        List<Object> params = new ArrayList<>();
        if (status != null && !status.isBlank()) {
            conditions.add("t.status = ?");
            params.add(status.trim());
        }
        if (ownerId != null) {
            conditions.add("t.owner_id = ?");
            params.add(ownerId);
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(t.query LIKE ? OR t.thread_id LIKE ? OR u.username LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        return toQueryParts(conditions, params);
    }

    private QueryParts buildReportFilter(Long ownerId, String keyword) {
        List<String> conditions = new ArrayList<>(List.of("r.deleted_at IS NULL"));
        List<Object> params = new ArrayList<>();
        if (ownerId != null) {
            conditions.add("r.owner_id = ?");
            params.add(ownerId);
        }
        if (keyword != null && !keyword.isBlank()) {
            conditions.add("(r.content LIKE ? OR r.thread_id LIKE ? OR u.username LIKE ?)");
            String pattern = "%" + keyword.trim() + "%";
            params.add(pattern);
            params.add(pattern);
            params.add(pattern);
        }
        return toQueryParts(conditions, params);
    }

    private QueryParts toQueryParts(List<String> conditions, List<Object> params) {
        String where = conditions.isEmpty() ? "" : "WHERE " + String.join(" AND ", conditions);
        return new QueryParts(where, params);
    }

    private String toLikePattern(String keyword) {
        return keyword == null || keyword.isBlank() ? null : "%" + keyword.trim() + "%";
    }

    private record QueryParts(String whereClause, List<Object> params) {
    }
}
