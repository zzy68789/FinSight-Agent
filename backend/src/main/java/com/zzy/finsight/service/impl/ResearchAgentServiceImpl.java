package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.runtime.AgentTraceReader;
import com.zzy.finsight.agent.runtime.DurableAgentRunner;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchRunTraceResponse;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.ResearchAgentService;
import com.zzy.finsight.service.SseService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实现 Research Agent 异步运行、失败重试和动态轨迹查询。
 */
@Service
public class ResearchAgentServiceImpl implements ResearchAgentService {
    private static final Set<String> RETRYABLE_STATUSES = Set.of("FAILED", "INSUFFICIENT_EVIDENCE");
    private final DurableAgentRunner runner;
    private final ResearchTaskMapper taskMapper;
    private final ResearchRunRequestCodec requestCodec;
    private final AgentTraceReader traceReader;
    private final SseService sseService;
    private final ExecutorService executorService;

    public ResearchAgentServiceImpl(
            DurableAgentRunner runner,
            ResearchTaskMapper taskMapper,
            ResearchRunRequestCodec requestCodec,
            AgentTraceReader traceReader,
            SseService sseService,
            @Qualifier("agentExecutor") ExecutorService executorService
    ) {
        this.runner = runner;
        this.taskMapper = taskMapper;
        this.requestCodec = requestCodec;
        this.traceReader = traceReader;
        this.sseService = sseService;
        this.executorService = executorService;
    }

    @Override
    public void run(long ownerId, ResearchRunRequest request, SseEmitter emitter) {
        AgentEventListener listener = eventListener(emitter);
        try {
            executorService.submit(() -> runner.runNew(ownerId, request, listener));
        } catch (RejectedExecutionException exception) {
            listener.onError(new IllegalStateException("当前 Agent 任务较多，请稍后重试", exception));
        }
    }

    @Override
    public void retry(long ownerId, long taskId) {
        TaskExecutionRecord task = taskMapper.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Research Agent 任务"));
        if (!RETRYABLE_STATUSES.contains(task.status())) {
            throw new IllegalStateException("仅失败或证据不足的 Agent 任务允许重试");
        }
        if (task.attemptCount() >= 3) {
            throw new IllegalStateException("Research Agent 任务已达到最大重试次数");
        }
        ResearchRunRequest request = requestCodec.fromJson(task.requestPayload());
        if (!taskMapper.markRetrying(taskId, task.status())) {
            throw new IllegalStateException("Research Agent 任务状态已变化，请刷新后重试");
        }
        try {
            executorService.submit(() -> runner.runExisting(
                    ownerId, taskId, task.threadId(), request, AgentEventListener.noop()
            ));
        } catch (RejectedExecutionException exception) {
            taskMapper.finishAgent(taskId, "FAILED", "QUEUE_REJECTED", "Agent 执行队列已满");
            throw new IllegalStateException("当前 Agent 任务较多，请稍后重试", exception);
        }
    }

    @Override
    public ResearchRunTraceResponse trace(long ownerId, long taskId) {
        return traceReader.get(ownerId, taskId);
    }

    private AgentEventListener eventListener(SseEmitter emitter) {
        AtomicBoolean connected = new AtomicBoolean(true);
        return new AgentEventListener() {
            @Override
            public void onEvent(AgentEvent event) {
                if (!connected.get()) {
                    return;
                }
                try {
                    sseService.send(emitter, event.type(), event.ssePayload());
                } catch (Exception exception) {
                    connected.set(false);
                }
            }

            @Override
            public void onDone() {
                if (!connected.get()) {
                    return;
                }
                try {
                    sseService.done(emitter);
                } catch (Exception exception) {
                    connected.set(false);
                }
            }

            @Override
            public void onError(Throwable throwable) {
                if (connected.get()) {
                    sseService.error(emitter, throwable);
                }
            }
        };
    }
}
