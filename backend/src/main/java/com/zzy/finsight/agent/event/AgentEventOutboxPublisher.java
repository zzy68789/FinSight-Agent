package com.zzy.finsight.agent.event;

import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 在数据库提交后把 outbox 事件投递到 Redis 运行态和当前 SSE Adapter。
 */
@Component
public class AgentEventOutboxPublisher {
    private final AgentEventOutboxMapper mapper;
    private final TaskRuntimeStateService runtimeStateService;
    private final AgentEventStreamModule streamModule;
    private final String claimOwner = UUID.randomUUID().toString();
    private final Duration claimDuration;
    private final Duration immediateGrace;
    private final int maxAttempts;

    public AgentEventOutboxPublisher(
            AgentEventOutboxMapper mapper,
            TaskRuntimeStateService runtimeStateService,
            AgentEventStreamModule streamModule,
            @Value("${finsight.agent.event-outbox-claim-duration:PT30S}") Duration claimDuration,
            @Value("${finsight.agent.event-outbox-immediate-grace:PT5S}") Duration immediateGrace,
            @Value("${finsight.agent.event-outbox-max-attempts:8}") int maxAttempts
    ) {
        this.mapper = mapper;
        this.runtimeStateService = runtimeStateService;
        this.streamModule = streamModule;
        this.claimDuration = positive(claimDuration, Duration.ofSeconds(30));
        this.immediateGrace = positive(immediateGrace, Duration.ofSeconds(5));
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    /** 提交后立即投递一条事件；SSE 断开不影响 outbox 已提交事实。 */
    public void publish(AgentEvent event, AgentEventListener listener) {
        if (event == null) {
            return;
        }
        try {
            deliver(event, listener);
            mapper.markPublishedDirect(event.id(), LocalDateTime.now());
        } catch (RuntimeException ignored) {
            // 投递失败保留未发布状态，由定时 outbox 扫描重试。
        }
    }

    /** 原子领取并恢复尚未投递的事件，失败时按尝试次数退避或进入死信状态。 */
    @Scheduled(fixedDelayString = "${finsight.agent.event-outbox-publish-interval-ms:1000}")
    public void publishPending() {
        LocalDateTime now = LocalDateTime.now();
        mapper.claimBatch(
                claimOwner,
                now.minus(immediateGrace),
                now,
                now.plus(claimDuration),
                100
        );
        mapper.findClaimed(claimOwner, 100).forEach(this::publishClaimed);
    }

    private void publishClaimed(AgentEvent event) {
        try {
            deliver(event, AgentEventListener.noop());
            mapper.markClaimPublished(event.id(), claimOwner, LocalDateTime.now());
        } catch (RuntimeException exception) {
            int attempts = Math.max(1, mapper.findPublishAttempts(event.id()));
            String error = safeError(exception);
            if (attempts >= maxAttempts) {
                mapper.markDeadLetter(event.id(), claimOwner, LocalDateTime.now(), error);
                return;
            }
            long delaySeconds = Math.min(300L, 1L << Math.min(8, attempts - 1));
            mapper.releaseClaim(
                    event.id(), claimOwner, LocalDateTime.now().plusSeconds(delaySeconds), error
            );
        }
    }

    private void deliver(AgentEvent event, AgentEventListener listener) {
        runtimeStateService.recordStep(event.taskId(), event.threadId(), event.type(), event.ssePayload());
        streamModule.publish(event);
        (listener == null ? AgentEventListener.noop() : listener).onEvent(event);
    }

    private Duration positive(Duration value, Duration fallback) {
        return value == null || value.isZero() || value.isNegative() ? fallback : value;
    }

    private String safeError(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
                ? exception.getClass().getSimpleName()
                : message.substring(0, Math.min(2000, message.length()));
    }
}
