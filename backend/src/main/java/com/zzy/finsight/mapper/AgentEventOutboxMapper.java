package com.zzy.finsight.mapper;

import com.zzy.finsight.agent.event.AgentEvent;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 持久化版本化 Agent 事件 outbox，并提供 Trace 与待投递查询。
 */
@Mapper
public interface AgentEventOutboxMapper {
    /** 插入事件并返回数据库主键。 */
    default long insert(AgentEvent event) {
        Map<String, Object> command = new LinkedHashMap<>();
        command.put("schemaVersion", event.schemaVersion());
        command.put("taskId", event.taskId());
        command.put("threadId", event.threadId());
        command.put("sequence", event.sequence());
        command.put("eventId", event.eventId());
        command.put("turnNo", event.turnNo());
        command.put("eventType", event.type());
        command.put("status", event.status());
        command.put("payload", event.payload());
        command.put("errorMessage", event.errorMessage());
        command.put("durationMs", event.durationMs());
        command.put("createdAt", event.createdAt());
        insertEvent(command);
        Number id = (Number) command.get("id");
        if (id == null) {
            throw new IllegalStateException("写入 Agent 事件 outbox 后未返回主键");
        }
        return id.longValue();
    }

    int insertEvent(Map<String, Object> command);

    List<AgentEvent> findByTaskId(@Param("taskId") long taskId);

    List<AgentEvent> findAfterSequence(
            @Param("taskId") long taskId,
            @Param("afterSequence") long afterSequence
    );

    /** 原子领取一批达到重试时间且未被其他实例占用的事件。 */
    int claimBatch(
            @Param("claimOwner") String claimOwner,
            @Param("claimBefore") LocalDateTime claimBefore,
            @Param("now") LocalDateTime now,
            @Param("claimedUntil") LocalDateTime claimedUntil,
            @Param("limit") int limit
    );

    List<AgentEvent> findClaimed(@Param("claimOwner") String claimOwner, @Param("limit") int limit);

    int markPublishedDirect(@Param("id") long id, @Param("publishedAt") LocalDateTime publishedAt);

    int markClaimPublished(
            @Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("publishedAt") LocalDateTime publishedAt
    );

    int releaseClaim(
            @Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastError") String lastError
    );

    int markDeadLetter(
            @Param("id") long id,
            @Param("claimOwner") String claimOwner,
            @Param("deadLetteredAt") LocalDateTime deadLetteredAt,
            @Param("lastError") String lastError
    );

    int findPublishAttempts(@Param("id") long id);
}
