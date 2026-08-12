package com.zzy.finsight.agent.event;

import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.service.SseService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 统一 Agent 事件的历史重放、实时订阅、序号去重与 SSE 连接清理。
 */
@Component
public class AgentEventStreamModule {
    private final AgentEventOutboxMapper mapper;
    private final SseService sseService;
    private final Map<Long, CopyOnWriteArrayList<Subscription>> subscriptions = new ConcurrentHashMap<>();

    public AgentEventStreamModule(AgentEventOutboxMapper mapper, SseService sseService) {
        this.mapper = mapper;
        this.sseService = sseService;
    }

    /**
     * 先注册实时订阅再重放缺失事件，通过内部缓冲消除查询历史期间的竞态窗口。
     */
    public SseEmitter subscribe(long taskId, long afterSequence, boolean terminal) {
        SseEmitter emitter = new SseEmitter(0L);
        Subscription subscription = new Subscription(taskId, Math.max(0L, afterSequence), emitter);
        subscriptions.computeIfAbsent(taskId, ignored -> new CopyOnWriteArrayList<>()).add(subscription);
        emitter.onCompletion(subscription::remove);
        emitter.onTimeout(subscription::remove);
        emitter.onError(ignored -> subscription.remove());
        try {
            subscription.replay(mapper.findAfterSequence(taskId, Math.max(0L, afterSequence)), terminal);
        } catch (RuntimeException exception) {
            subscription.fail(exception);
            throw exception;
        }
        return emitter;
    }

    /** 把事务提交后的事件广播给当前实例内的所有订阅者。 */
    public void publish(AgentEvent event) {
        if (event == null) {
            return;
        }
        boolean terminal = isTerminal(event);
        List<Subscription> current = subscriptions.getOrDefault(event.taskId(), new CopyOnWriteArrayList<>());
        current.forEach(subscription -> subscription.offer(event, terminal));
    }

    /**
     * 从共享数据库补取其他实例提交或投递的事件，避免 SSE 依赖粘性会话。
     */
    @Scheduled(fixedDelayString = "${finsight.agent.event-stream-poll-interval-ms:1000}")
    public void pollCommittedEvents() {
        subscriptions.forEach((taskId, current) -> {
            if (current.isEmpty()) {
                return;
            }
            long afterSequence = current.stream()
                    .mapToLong(Subscription::lastSequence)
                    .min()
                    .orElse(0L);
            mapper.findAfterSequence(taskId, afterSequence).forEach(this::publish);
        });
    }

    private boolean isTerminal(AgentEvent event) {
        return "run_completed".equals(event.type()) || "run_stopped".equals(event.type());
    }

    private final class Subscription {
        private final long taskId;
        private final SseEmitter emitter;
        private final TreeMap<Long, AgentEvent> buffered = new TreeMap<>();
        private long lastSequence;
        private boolean replaying = true;
        private boolean terminalPending;
        private boolean closed;

        private Subscription(long taskId, long afterSequence, SseEmitter emitter) {
            this.taskId = taskId;
            this.lastSequence = afterSequence;
            this.emitter = emitter;
        }

        private synchronized void replay(List<AgentEvent> history, boolean terminal) {
            List<AgentEvent> ordered = new ArrayList<>(history == null ? List.of() : history);
            ordered.sort(java.util.Comparator.comparingLong(AgentEvent::sequence));
            for (AgentEvent event : ordered) {
                deliver(event);
                terminalPending = terminalPending || isTerminal(event);
            }
            for (AgentEvent event : buffered.values()) {
                deliver(event);
                terminalPending = terminalPending || isTerminal(event);
            }
            buffered.clear();
            replaying = false;
            terminalPending = terminalPending || terminal;
            if (terminalPending) {
                finish();
            }
        }

        private synchronized void offer(AgentEvent event, boolean terminal) {
            if (closed || event.sequence() <= lastSequence) {
                return;
            }
            if (replaying) {
                buffered.putIfAbsent(event.sequence(), event);
                terminalPending = terminalPending || terminal;
                return;
            }
            deliver(event);
            if (terminal) {
                finish();
            }
        }

        private void deliver(AgentEvent event) {
            if (closed || event.sequence() <= lastSequence) {
                return;
            }
            try {
                sseService.sendAgentEvent(emitter, event);
                lastSequence = event.sequence();
            } catch (IOException | RuntimeException exception) {
                fail(exception);
            }
        }

        private void finish() {
            if (closed) {
                return;
            }
            closed = true;
            remove();
            try {
                sseService.done(emitter);
            } catch (IOException | RuntimeException exception) {
                emitter.completeWithError(exception);
            }
        }

        private void fail(Throwable throwable) {
            if (closed) {
                return;
            }
            closed = true;
            remove();
            emitter.completeWithError(throwable);
        }

        private void remove() {
            CopyOnWriteArrayList<Subscription> current = subscriptions.get(taskId);
            if (current == null) {
                return;
            }
            current.remove(this);
            if (current.isEmpty()) {
                subscriptions.remove(taskId, current);
            }
        }

        private synchronized long lastSequence() {
            return lastSequence;
        }
    }
}
