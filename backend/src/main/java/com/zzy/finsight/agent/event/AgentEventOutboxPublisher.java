package com.zzy.finsight.agent.event;

import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * 在数据库提交后把 outbox 事件投递到 Redis 运行态和当前 SSE Adapter。
 */
@Component
public class AgentEventOutboxPublisher {
    private final AgentEventOutboxMapper mapper;
    private final TaskRuntimeStateService runtimeStateService;

    public AgentEventOutboxPublisher(
            AgentEventOutboxMapper mapper,
            TaskRuntimeStateService runtimeStateService
    ) {
        this.mapper = mapper;
        this.runtimeStateService = runtimeStateService;
    }

    /** 提交后立即投递一条事件；SSE 断开不影响 outbox 已提交事实。 */
    public void publish(AgentEvent event, AgentEventListener listener) {
        if (event == null) {
            return;
        }
        try {
            runtimeStateService.recordStep(event.taskId(), event.threadId(), event.type(), event.ssePayload());
            (listener == null ? AgentEventListener.noop() : listener).onEvent(event);
            mapper.markPublished(event.id(), LocalDateTime.now());
        } catch (RuntimeException ignored) {
            // 投递失败保留未发布状态，由定时 outbox 扫描重试。
        }
    }

    /** 恢复提交后尚未投递到运行态的事件，历史 Trace 始终直接读取 outbox。 */
    @Scheduled(fixedDelayString = "${finsight.agent.event-outbox-publish-interval-ms:1000}")
    public void publishPending() {
        mapper.findUnpublished(100).forEach(event -> publish(event, AgentEventListener.noop()));
    }
}
