package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.CheckpointRecord;
import com.zzy.finsight.agent.memory.AgentCheckpointCodec;
import com.zzy.finsight.agent.memory.AgentCheckpointState;
import com.zzy.finsight.agent.memory.AgentState;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 定义工作流检查点的 MyBatis 数据访问操作。
 */
@Mapper
public interface CheckpointMapper {
    /** 保存 Research Agent 某一轮的可恢复状态。 */
    default void saveAgent(
            String threadId,
            long taskId,
            int turnNo,
            String contextHash,
            AgentState state
    ) {
        insert(threadId, taskId, "AGENT_STATE", Math.max(1, turnNo), contextHash,
                AgentCheckpointCodec.CURRENT_VERSION, Math.max(0, turnNo),
                AgentCheckpointState.from(state), LocalDateTime.now());
    }

    /** 兼容非 AgentState 的旧集成测试和迁移工具。 */
    default void saveAgent(
            String threadId,
            long taskId,
            int turnNo,
            String contextHash,
            Object legacyState
    ) {
        insert(threadId, taskId, "AGENT_STATE", Math.max(1, turnNo), contextHash,
                "agent-state-v1", Math.max(0, turnNo), legacyState, LocalDateTime.now());
    }

    int insert(
            @Param("threadId") String threadId,
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("attemptNo") int attemptNo,
            @Param("generationContextHash") String generationContextHash,
            @Param("stateVersion") String stateVersion,
            @Param("turnNo") int turnNo,
            @Param("state") Object state,
            @Param("createdAt") LocalDateTime createdAt
    );

    Optional<CheckpointRecord> findLatest(
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("generationContextHash") String generationContextHash
    );
}
