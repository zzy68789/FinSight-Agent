package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.AgentStepLogRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 定义 Agent 步骤日志的 MyBatis 数据访问操作。
 */
@Mapper
public interface AgentStepLogMapper {
    default void save(long taskId, String stepName, Object outputSnapshot) {
        save(taskId, stepName, outputSnapshot, 1, 0L);
    }

    default void save(long taskId, String stepName, Object outputSnapshot, int attemptNo, long durationMs) {
        save(taskId, stepName, outputSnapshot, attemptNo, durationMs, "SUCCESS", null);
    }

    /** 保存成功或降级完成的步骤，并保留稳定错误分类。 */
    default void save(
            long taskId,
            String stepName,
            Object outputSnapshot,
            int attemptNo,
            long durationMs,
            String status,
            String errorMessage
    ) {
        String normalizedStatus = "DEGRADED".equals(status) ? "DEGRADED" : "SUCCESS";
        insertStep(
                taskId,
                stepName,
                outputSnapshot,
                normalizedStatus,
                errorMessage == null || errorMessage.isBlank() ? null : errorMessage,
                Math.max(1, attemptNo),
                Math.max(0L, durationMs),
                LocalDateTime.now()
        );
    }

    default void saveError(long taskId, String stepName, Throwable throwable) {
        insertError(taskId, stepName, throwable == null ? null : throwable.getMessage(), LocalDateTime.now());
    }

    int insertStep(
            @Param("taskId") long taskId,
            @Param("stepName") String stepName,
            @Param("outputSnapshot") Object outputSnapshot,
            @Param("status") String status,
            @Param("errorMessage") String errorMessage,
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
