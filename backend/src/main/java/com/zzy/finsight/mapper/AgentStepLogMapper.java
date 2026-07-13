package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.AgentStepLogRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface AgentStepLogMapper {
    default void save(long taskId, String stepName, Object outputSnapshot) {
        save(taskId, stepName, outputSnapshot, 1, 0L);
    }

    default void save(long taskId, String stepName, Object outputSnapshot, int attemptNo, long durationMs) {
        insertSuccess(taskId, stepName, outputSnapshot, Math.max(1, attemptNo), Math.max(0L, durationMs), LocalDateTime.now());
    }

    default void saveError(long taskId, String stepName, Throwable throwable) {
        insertError(taskId, stepName, throwable == null ? null : throwable.getMessage(), LocalDateTime.now());
    }

    int insertSuccess(
            @Param("taskId") long taskId,
            @Param("stepName") String stepName,
            @Param("outputSnapshot") Object outputSnapshot,
            @Param("attemptNo") int attemptNo,
            @Param("durationMs") long durationMs,
            @Param("createdAt") LocalDateTime createdAt
    );

    int insertError(
            @Param("taskId") long taskId,
            @Param("stepName") String stepName,
            @Param("errorMessage") String errorMessage,
            @Param("createdAt") LocalDateTime createdAt
    );

    List<AgentStepLogRecord> findByTaskId(@Param("taskId") long taskId);
}
