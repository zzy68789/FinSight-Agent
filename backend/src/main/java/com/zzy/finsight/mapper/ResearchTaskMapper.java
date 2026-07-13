package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.ResearchTaskRecord;
import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Mapper
public interface ResearchTaskMapper {
    default long create(long ownerId, String threadId, String query, String searchMode) {
        return create(ownerId, threadId, query, searchMode, null);
    }

    default long create(long ownerId, String threadId, String query, String searchMode, String requestPayload) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("ownerId", ownerId);
        command.put("threadId", threadId);
        command.put("query", query);
        command.put("searchMode", searchMode);
        command.put("requestPayload", requestPayload);
        command.put("createdAt", now);
        command.put("updatedAt", now);
        insertTask(command);
        Number id = (Number) command.get("id");
        if (id == null) {
            throw new IllegalStateException("创建调研任务后未返回生成主键");
        }
        return id.longValue();
    }

    int insertTask(Map<String, Object> command);

    default void markRunning(long taskId) {
        updateStatus(taskId, "RUNNING", LocalDateTime.now());
    }

    int updateStatus(@Param("taskId") long taskId, @Param("status") String status, @Param("now") LocalDateTime now);

    default boolean startAttempt(long taskId, String leaseOwner, LocalDateTime leaseUntil) {
        return acquireAttempt(taskId, leaseOwner, leaseUntil, LocalDateTime.now()) == 1;
    }

    int acquireAttempt(
            @Param("taskId") long taskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    default void updateStage(long taskId, String stage, String leaseOwner, LocalDateTime leaseUntil) {
        updateRunningStage(taskId, stage, leaseOwner, leaseUntil, LocalDateTime.now());
    }

    int updateRunningStage(
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    default void markCompleted(long taskId) {
        finish(taskId, "COMPLETED", "COMPLETED", null, LocalDateTime.now());
    }

    default void markFailed(long taskId) {
        markFailed(taskId, null);
    }

    default void markFailed(long taskId, String error) {
        finish(taskId, "FAILED", "FAILED", error, LocalDateTime.now());
    }

    int finish(
            @Param("taskId") long taskId,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("error") String error,
            @Param("now") LocalDateTime now
    );

    default boolean markRetrying(long taskId, String expectedStatus) {
        return updateRetrying(taskId, expectedStatus, LocalDateTime.now()) == 1;
    }

    int updateRetrying(
            @Param("taskId") long taskId,
            @Param("expectedStatus") String expectedStatus,
            @Param("now") LocalDateTime now
    );

    default boolean markStaleRetrying(long taskId, LocalDateTime heartbeatBefore) {
        return updateStaleRetrying(taskId, heartbeatBefore, LocalDateTime.now()) == 1;
    }

    int updateStaleRetrying(
            @Param("taskId") long taskId,
            @Param("heartbeatBefore") LocalDateTime heartbeatBefore,
            @Param("now") LocalDateTime now
    );

    Optional<WorkflowTaskExecutionRecord> findExecution(
            @Param("ownerId") long ownerId,
            @Param("taskId") long taskId
    );

    List<WorkflowTaskExecutionRecord> findStaleRunning(
            @Param("heartbeatBefore") LocalDateTime heartbeatBefore,
            @Param("limit") int limit
    );

    default List<ResearchTaskRecord> findPage(long ownerId, int page, int size, String status, String keyword) {
        int normalizedSize = Math.max(1, size);
        int offset = Math.max(0, (page - 1) * normalizedSize);
        return selectPage(ownerId, offset, normalizedSize, status, keyword);
    }

    List<ResearchTaskRecord> selectPage(
            @Param("ownerId") long ownerId,
            @Param("offset") int offset,
            @Param("size") int size,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    long count(
            @Param("ownerId") long ownerId,
            @Param("status") String status,
            @Param("keyword") String keyword
    );

    Optional<ResearchTaskRecord> findById(@Param("ownerId") long ownerId, @Param("taskId") long taskId);
}
