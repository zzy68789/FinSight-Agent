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

    List<AgentEvent> findUnpublished(@Param("limit") int limit);

    int markPublished(@Param("id") long id, @Param("publishedAt") LocalDateTime publishedAt);
}
