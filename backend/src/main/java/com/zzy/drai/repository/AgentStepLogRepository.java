package com.zzy.drai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class AgentStepLogRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public AgentStepLogRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
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

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
