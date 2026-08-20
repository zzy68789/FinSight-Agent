package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.ResearchTaskRecord;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.agent.runtime.LeaseToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 定义研究任务生命周期的 MyBatis 数据访问操作。
 */
@Mapper
public interface ResearchTaskMapper {
    /** 创建由 Research Agent Runtime 执行的研究任务。 */
    default long createAgent(
            long ownerId,
            String threadId,
            String query,
            String searchMode,
            String requestPayload,
            String plannerVersion,
            String toolsetVersion,
            String policyVersion
    ) {
        return createAgent(ownerId, threadId, query, searchMode, requestPayload,
                plannerVersion, toolsetVersion, policyVersion, null);
    }

    /** 使用客户端幂等键创建由 Research Agent Runtime 执行的研究任务。 */
    default long createAgent(
            long ownerId,
            String threadId,
            String query,
            String searchMode,
            String requestPayload,
            String plannerVersion,
            String toolsetVersion,
            String policyVersion,
            String clientRequestId
    ) {
        return createTask(ownerId, threadId, query, searchMode, requestPayload,
                "RESEARCH_AGENT", plannerVersion, toolsetVersion, policyVersion, clientRequestId);
    }

    private long createTask(
            long ownerId,
            String threadId,
            String query,
            String searchMode,
            String requestPayload,
            String runtimeType,
            String plannerVersion,
            String toolsetVersion,
            String policyVersion,
            String clientRequestId
    ) {
        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("ownerId", ownerId);
        command.put("clientRequestId", clientRequestId);
        command.put("threadId", threadId);
        command.put("query", query);
        command.put("searchMode", searchMode);
        command.put("requestPayload", requestPayload);
        command.put("runtimeType", runtimeType);
        command.put("plannerVersion", plannerVersion);
        command.put("toolsetVersion", toolsetVersion);
        command.put("policyVersion", policyVersion);
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

    default boolean startAttempt(long taskId, String leaseOwner, LocalDateTime leaseUntil) {
        return acquireAttempt(taskId, leaseOwner, leaseUntil, LocalDateTime.now()) == 1;
    }

    /** 获取新租约并返回用于后续事务 fencing 的单调令牌。 */
    default Optional<LeaseToken> startAttemptToken(long taskId, String leaseOwner, LocalDateTime leaseUntil) {
        if (acquireAttempt(taskId, leaseOwner, leaseUntil, LocalDateTime.now()) != 1) {
            return Optional.empty();
        }
        return findActiveLeaseEpoch(taskId, leaseOwner)
                .map(epoch -> new LeaseToken(taskId, leaseOwner, epoch));
    }

    int acquireAttempt(
            @Param("taskId") long taskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    /** 更新 Agent 当前阶段和已消耗预算。 */
    default boolean updateAgentProgress(
            long taskId,
            String stage,
            int turnCount,
            int toolCallCount,
            String leaseOwner,
            LocalDateTime leaseUntil
    ) {
        return updateRunningAgentProgress(
                taskId, stage, turnCount, toolCallCount, leaseOwner, leaseUntil, LocalDateTime.now()
        ) == 1;
    }

    int updateRunningAgentProgress(
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("turnCount") int turnCount,
            @Param("toolCallCount") int toolCallCount,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    /** 使用 owner + epoch 双重 fencing 更新 Agent 心跳和计数。 */
    default boolean updateAgentProgressFenced(
            LeaseToken lease,
            String stage,
            int turnCount,
            int toolCallCount,
            LocalDateTime leaseUntil
    ) {
        return updateRunningAgentProgressFenced(
                lease.taskId(), stage, turnCount, toolCallCount, lease.owner(), lease.epoch(),
                leaseUntil, LocalDateTime.now()
        ) == 1;
    }

    int updateRunningAgentProgressFenced(
            @Param("taskId") long taskId,
            @Param("stage") String stage,
            @Param("turnCount") int turnCount,
            @Param("toolCallCount") int toolCallCount,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseEpoch") long leaseEpoch,
            @Param("leaseUntil") LocalDateTime leaseUntil,
            @Param("now") LocalDateTime now
    );

    /** 以完成、证据不足或失败状态结束 Agent 任务。 */
    default void finishAgent(long taskId, String status, String stopReason, String error) {
        finishAgentTask(taskId, status, status, stopReason, error, LocalDateTime.now());
    }

    int finishAgentTask(
            @Param("taskId") long taskId,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("stopReason") String stopReason,
            @Param("error") String error,
            @Param("now") LocalDateTime now
    );

    /** 仅允许当前租约代次结束 Agent 任务。 */
    default boolean finishAgentFenced(LeaseToken lease, String status, String stopReason, String error) {
        return finishAgentTaskFenced(
                lease.taskId(), status, status, stopReason, error, lease.owner(), lease.epoch(), LocalDateTime.now()
        ) == 1;
    }

    int finishAgentTaskFenced(
            @Param("taskId") long taskId,
            @Param("status") String status,
            @Param("stage") String stage,
            @Param("stopReason") String stopReason,
            @Param("error") String error,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseEpoch") long leaseEpoch,
            @Param("now") LocalDateTime now
    );

    /** 原子取消用户拥有的非终态任务，并递增租约代次阻止旧执行者继续提交。 */
    int cancelAgentTask(
            @Param("ownerId") long ownerId,
            @Param("taskId") long taskId,
            @Param("now") LocalDateTime now
    );

    /** 查询用户任务已经分配的最新事件序号。 */
    Optional<Long> findEventSequence(
            @Param("ownerId") long ownerId,
            @Param("taskId") long taskId
    );

    Optional<Long> findActiveLeaseEpoch(
            @Param("taskId") long taskId,
            @Param("leaseOwner") String leaseOwner
    );

    /** 在当前事务中锁定仍归指定执行者持有的租约行。 */
    Optional<Long> lockActiveLeaseEpoch(
            @Param("taskId") long taskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseEpoch") long leaseEpoch
    );

    /** 仅由当前租约执行者递增任务内事件序号。 */
    default long nextEventSequence(LeaseToken lease) {
        if (incrementEventSequenceFenced(
                lease.taskId(), lease.owner(), lease.epoch(), LocalDateTime.now()
        ) != 1) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得写入 Agent 事件");
        }
        return findEventSequenceFenced(lease.taskId(), lease.owner(), lease.epoch())
                .orElseThrow(() -> new IllegalStateException("LEASE_FENCED：事件序号读取失败"));
    }

    int incrementEventSequenceFenced(
            @Param("taskId") long taskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseEpoch") long leaseEpoch,
            @Param("now") LocalDateTime now
    );

    Optional<Long> findEventSequenceFenced(
            @Param("taskId") long taskId,
            @Param("leaseOwner") String leaseOwner,
            @Param("leaseEpoch") long leaseEpoch
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

    Optional<TaskExecutionRecord> findExecution(
            @Param("ownerId") long ownerId,
            @Param("taskId") long taskId
    );

    /** 按用户与客户端幂等键查询已创建的权威任务。 */
    Optional<TaskExecutionRecord> findExecutionByClientRequestId(
            @Param("ownerId") long ownerId,
            @Param("clientRequestId") String clientRequestId
    );

    List<TaskExecutionRecord> findStaleAgentRunning(
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
