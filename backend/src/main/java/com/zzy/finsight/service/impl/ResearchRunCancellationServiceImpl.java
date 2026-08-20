package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.event.AgentEventOutboxPublisher;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.ResearchRunCancellationService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 通过任务状态和租约代次 fencing 原子取消 Research Agent 任务。
 */
@Service
public class ResearchRunCancellationServiceImpl implements ResearchRunCancellationService {
    private static final Set<String> TERMINAL_STATUSES =
            Set.of("COMPLETED", "FAILED", "INSUFFICIENT_EVIDENCE", "CANCELLED");
    private final ResearchTaskMapper taskMapper;
    private final AgentEventOutboxMapper eventOutboxMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final TaskRuntimeStateService runtimeStateService;
    private final AgentEventOutboxPublisher eventPublisher;

    public ResearchRunCancellationServiceImpl(
            ResearchTaskMapper taskMapper,
            AgentEventOutboxMapper eventOutboxMapper,
            AgentStepLogMapper stepLogMapper,
            TaskRuntimeStateService runtimeStateService,
            AgentEventOutboxPublisher eventPublisher
    ) {
        this.taskMapper = taskMapper;
        this.eventOutboxMapper = eventOutboxMapper;
        this.stepLogMapper = stepLogMapper;
        this.runtimeStateService = runtimeStateService;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 先终止任务并使旧租约失效，再在同一事务写入终止事件，保证刷新和 SSE 结果一致。
     */
    @Override
    @Transactional
    public void cancel(long ownerId, long taskId) {
        TaskExecutionRecord task = findOwnedTask(ownerId, taskId);
        if ("CANCELLED".equals(task.status())) {
            return;
        }
        if (TERMINAL_STATUSES.contains(task.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "已经结束的任务不能取消");
        }

        LocalDateTime now = LocalDateTime.now();
        if (taskMapper.cancelAgentTask(ownerId, taskId, now) != 1) {
            TaskExecutionRecord current = findOwnedTask(ownerId, taskId);
            if ("CANCELLED".equals(current.status())) {
                return;
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT, "任务状态已经变化，请刷新后重试");
        }

        long sequence = taskMapper.findEventSequence(ownerId, taskId)
                .orElseThrow(() -> new IllegalStateException("取消任务后未找到事件序号"));
        AgentEvent pending = new AgentEvent(
                0L,
                AgentEvent.CURRENT_SCHEMA_VERSION,
                taskId,
                task.threadId(),
                sequence,
                UUID.randomUUID().toString(),
                Math.max(0, task.attemptCount()),
                "run_stopped",
                "CANCELLED",
                Map.of(
                        "taskId", taskId,
                        "status", "CANCELLED",
                        "reason", "USER_CANCELLED"
                ),
                "用户取消任务",
                0L,
                null,
                now
        );
        long eventId = eventOutboxMapper.insert(pending);
        AgentEvent persisted = withId(pending, eventId);
        stepLogMapper.save(
                taskId, "run_stopped", persisted.ssePayload(), Math.max(1, task.attemptCount()),
                0L, "CANCELLED", "用户取消任务"
        );
        publishAfterCommit(persisted);
    }

    private TaskExecutionRecord findOwnedTask(long ownerId, long taskId) {
        return taskMapper.findExecution(ownerId, taskId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "未找到 Research Agent 任务"));
    }

    private AgentEvent withId(AgentEvent event, long id) {
        return new AgentEvent(
                id, event.schemaVersion(), event.taskId(), event.threadId(), event.sequence(), event.eventId(),
                event.turnNo(), event.type(), event.status(), event.payload(), event.errorMessage(),
                event.durationMs(), event.publishedAt(), event.createdAt()
        );
    }

    private void publishAfterCommit(AgentEvent event) {
        Runnable publish = () -> {
            runtimeStateService.markStatus(event.taskId(), "CANCELLED");
            eventPublisher.publish(event, AgentEventListener.noop());
        };
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            publish.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                publish.run();
            }
        });
    }
}
