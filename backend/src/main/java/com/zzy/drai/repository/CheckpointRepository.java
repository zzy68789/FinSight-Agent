package com.zzy.drai.repository;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public class CheckpointRepository {
    private final JdbcClient jdbcClient;
    private final ObjectMapper objectMapper;

    public CheckpointRepository(JdbcClient jdbcClient, ObjectMapper objectMapper) {
        this.jdbcClient = jdbcClient;
        this.objectMapper = objectMapper;
    }

    public void save(String threadId, long taskId, Object state) {
        jdbcClient.sql("""
                        INSERT INTO checkpoint(thread_id, task_id, state_json, created_at)
                        VALUES (:threadId, :taskId, :stateJson, :createdAt)
                        """)
                .param("threadId", threadId)
                .param("taskId", taskId)
                .param("stateJson", toJson(state))
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
