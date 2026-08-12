package com.zzy.finsight.mapper;

import com.zzy.finsight.agent.planning.PlannerOutput;
import com.zzy.finsight.domain.AgentPlannerCallRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 持久化 Planner 模型路由、结构合法性、Token 和延迟元数据。
 */
@Mapper
public interface AgentPlannerCallMapper {
    /** 保存一次 Planner 调用。 */
    default void save(long taskId, PlannerOutput<?> output, boolean routeCorrect) {
        insert(
                taskId,
                null,
                output.decisionType(),
                output.requestedModel(),
                output.actualModel(),
                output.inputTokens(),
                output.outputTokens(),
                output.durationMs(),
                output.structureAttempts(),
                output.structuredValid(),
                routeCorrect,
                output.degraded(),
                output.degradedReason(),
                LocalDateTime.now()
        );
    }

    /** 以 turn 唯一键幂等保存动作决策，供事务内与轮次记录一起提交。 */
    default void saveForTurn(long taskId, long turnId, PlannerOutput<?> output, boolean routeCorrect) {
        upsertTurnDecision(
                taskId,
                turnId,
                output.decisionType(),
                output.requestedModel(),
                output.actualModel(),
                output.inputTokens(),
                output.outputTokens(),
                output.durationMs(),
                output.structureAttempts(),
                output.structuredValid(),
                routeCorrect,
                output.degraded(),
                output.degradedReason(),
                LocalDateTime.now()
        );
    }

    int insert(
            @Param("taskId") long taskId,
            @Param("turnId") Long turnId,
            @Param("decisionType") String decisionType,
            @Param("requestedModel") String requestedModel,
            @Param("actualModel") String actualModel,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("durationMs") long durationMs,
            @Param("structureAttempts") int structureAttempts,
            @Param("structuredValid") boolean structuredValid,
            @Param("routeCorrect") boolean routeCorrect,
            @Param("degraded") boolean degraded,
            @Param("degradedReason") String degradedReason,
            @Param("createdAt") LocalDateTime createdAt
    );

    int upsertTurnDecision(
            @Param("taskId") long taskId,
            @Param("turnId") long turnId,
            @Param("decisionType") String decisionType,
            @Param("requestedModel") String requestedModel,
            @Param("actualModel") String actualModel,
            @Param("inputTokens") int inputTokens,
            @Param("outputTokens") int outputTokens,
            @Param("durationMs") long durationMs,
            @Param("structureAttempts") int structureAttempts,
            @Param("structuredValid") boolean structuredValid,
            @Param("routeCorrect") boolean routeCorrect,
            @Param("degraded") boolean degraded,
            @Param("degradedReason") String degradedReason,
            @Param("createdAt") LocalDateTime createdAt
    );

    List<AgentPlannerCallRecord> findByTaskId(@Param("taskId") long taskId);

    Optional<AgentPlannerCallRecord> findByTurnId(@Param("turnId") long turnId);
}
