package com.zzy.drai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.drai.domain.AgentStepLogRecord;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AgentStepLogRepository {
    private static final RowMapper<AgentStepLogRecord> STEP_LOG_MAPPER = (rs, rowNum) -> new AgentStepLogRecord(
            rs.getLong("id"),
            rs.getLong("task_id"),
            rs.getString("step_name"),
            rs.getString("input_snapshot"),
            rs.getString("output_snapshot"),
            rs.getString("status"),
            rs.getString("error_message"),
            rs.getObject("created_at", LocalDateTime.class)
    );

    private final JdbcClient jdbcClient;
    private final JdbcTemplate jdbcTemplate;
    private final ObjectMapper objectMapper;

    public AgentStepLogRepository(JdbcClient jdbcClient, JdbcTemplate jdbcTemplate, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.jdbcTemplate = jdbcTemplate;
        this.objectMapper = objectMapper;
    }

    public void save(long taskId, String stepName, Object outputSnapshot) {
        jdbcClient.sql("""
                        INSERT INTO agent_step_log(task_id, step_name, input_snapshot, output_snapshot, status, error_message, created_at)
                        VALUES (:taskId, :stepName, NULL, :outputSnapshot, 'SUCCESS', NULL, :createdAt)
                        """)
                .param("taskId", taskId)
                .param("stepName", stepName)
                .param("outputSnapshot", toJson(outputSnapshot))
                .param("createdAt", LocalDateTime.now())
                .update();
    }

    public void saveError(long taskId, String stepName, Throwable throwable) {
        jdbcClient.sql("""
                        INSERT INTO agent_step_log(task_id, step_name, input_snapshot, output_snapshot, status, error_message, created_at)
                        VALUES (:taskId, :stepName, NULL, NULL, 'FAILED', :errorMessage, :createdAt)
                        """)
                .param("taskId", taskId)
                .param("stepName", stepName)
                .param("errorMessage", throwable.getMessage())
                .param("createdAt", LocalDateTime.now())
                .update();
    }

    public List<AgentStepLogRecord> findByTaskId(long taskId) {
        return jdbcTemplate.query("""
                        SELECT id, task_id, step_name, input_snapshot, output_snapshot, status, error_message, created_at
                        FROM agent_step_log
                        WHERE task_id = ?
                        ORDER BY id ASC
                        """,
                STEP_LOG_MAPPER,
                taskId
        );
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
