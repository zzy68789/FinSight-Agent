package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.WorkflowCheckpointRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 定义工作流检查点的 MyBatis 数据访问操作。
 */
@Mapper
public interface CheckpointMapper {
    default void save(String threadId, long taskId, Object state) {
        save(threadId, taskId, "UNKNOWN", 1, null, state);
    }

    /** 保存带阶段和生成上下文的可恢复检查点。 */
    default void save(
            String threadId,
            long taskId,
            String stage,
            int attemptNo,
            String generationContextHash,
            Object state
    ) {
        insert(threadId, taskId, stage, attemptNo, generationContextHash, state, LocalDateTime.now());
    }

    int insert(
            @Param("threadId") String threadId,
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("attemptNo") int attemptNo,
            @Param("generationContextHash") String generationContextHash,
            @Param("state") Object state,
            @Param("createdAt") LocalDateTime createdAt
    );

    Optional<WorkflowCheckpointRecord> findLatest(
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("generationContextHash") String generationContextHash
    );
}
