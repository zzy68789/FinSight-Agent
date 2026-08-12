package com.zzy.finsight.mapper;

import com.zzy.finsight.domain.AgentToolCallRecord;
import com.zzy.finsight.domain.AgentTurnRecord;
import com.zzy.finsight.agent.runtime.LeaseToken;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 持久化 Agent 决策轮次和白名单工具调用。
 */
@Mapper
public interface AgentRuntimeMapper {
    /** 保存一轮 Planner 动作并返回主键。 */
    default long saveTurn(
            long taskId,
            int turnNo,
            String phase,
            String actionType,
            Object action,
            String observationSummary,
            String status,
            int inputTokens,
            int outputTokens,
            long durationMs
    ) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("taskId", taskId);
        command.put("turnNo", turnNo);
        command.put("phase", phase);
        command.put("actionType", actionType);
        command.put("action", action);
        command.put("observationSummary", observationSummary);
        command.put("status", status);
        command.put("inputTokens", Math.max(0, inputTokens));
        command.put("outputTokens", Math.max(0, outputTokens));
        command.put("durationMs", Math.max(0L, durationMs));
        command.put("createdAt", LocalDateTime.now());
        insertTurn(command);
        return ((Number) command.get("id")).longValue();
    }

    int insertTurn(Map<String, Object> command);

    /** 在数据库写入语句中校验当前租约，避免查询校验后的竞态窗口。 */
    default long saveTurnFenced(
            LeaseToken lease,
            int turnNo,
            String phase,
            String actionType,
            Object action,
            int inputTokens,
            int outputTokens,
            long durationMs
    ) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("taskId", lease.taskId());
        command.put("leaseOwner", lease.owner());
        command.put("leaseEpoch", lease.epoch());
        command.put("turnNo", turnNo);
        command.put("phase", phase);
        command.put("actionType", actionType);
        command.put("action", action);
        command.put("inputTokens", Math.max(0, inputTokens));
        command.put("outputTokens", Math.max(0, outputTokens));
        command.put("durationMs", Math.max(0L, durationMs));
        command.put("createdAt", LocalDateTime.now());
        if (insertTurnFenced(command) != 1 || command.get("id") == null) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得打开 Agent turn");
        }
        return ((Number) command.get("id")).longValue();
    }

    int insertTurnFenced(Map<String, Object> command);

    int completeTurn(
            @Param("id") long id,
            @Param("observationSummary") String observationSummary,
            @Param("status") String status,
            @Param("durationMs") long durationMs
    );

    /** 创建运行中的工具调用记录并返回主键。 */
    default long startToolCall(
            long taskId,
            long turnId,
            String callId,
            String toolName,
            String argumentsHash,
            Object arguments,
            int attemptNo
    ) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("taskId", taskId);
        command.put("turnId", turnId);
        command.put("callId", callId);
        command.put("toolName", toolName);
        command.put("argumentsHash", argumentsHash);
        command.put("arguments", arguments);
        command.put("attemptNo", Math.max(1, attemptNo));
        command.put("startedAt", LocalDateTime.now());
        insertToolCall(command);
        return ((Number) command.get("id")).longValue();
    }

    int insertToolCall(Map<String, Object> command);

    /** 在数据库写入语句中校验当前租约后创建工具调用 journal。 */
    default long startToolCallFenced(
            LeaseToken lease,
            long turnId,
            String callId,
            String toolName,
            String argumentsHash,
            Object arguments,
            int attemptNo
    ) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("taskId", lease.taskId());
        command.put("leaseOwner", lease.owner());
        command.put("leaseEpoch", lease.epoch());
        command.put("turnId", turnId);
        command.put("callId", callId);
        command.put("toolName", toolName);
        command.put("argumentsHash", argumentsHash);
        command.put("arguments", arguments);
        command.put("attemptNo", Math.max(1, attemptNo));
        command.put("startedAt", LocalDateTime.now());
        if (insertToolCallFenced(command) != 1 || command.get("id") == null) {
            throw new IllegalStateException("LEASE_FENCED：过期执行者不得创建工具 journal");
        }
        return ((Number) command.get("id")).longValue();
    }

    int insertToolCallFenced(Map<String, Object> command);

    int completeToolCall(
            @Param("id") long id,
            @Param("result") Object result,
            @Param("status") String status,
            @Param("durationMs") long durationMs,
            @Param("errorCode") String errorCode,
            @Param("errorMessage") String errorMessage,
            @Param("completedAt") LocalDateTime completedAt
    );

    List<AgentTurnRecord> findTurns(@Param("taskId") long taskId);

    Optional<AgentTurnRecord> findTurn(
            @Param("taskId") long taskId,
            @Param("turnNo") int turnNo
    );

    List<AgentToolCallRecord> findToolCalls(@Param("taskId") long taskId);
}
