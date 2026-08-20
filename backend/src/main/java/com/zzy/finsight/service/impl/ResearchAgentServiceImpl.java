package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.event.AgentEventStreamModule;
import com.zzy.finsight.agent.runtime.AgentTraceReader;
import com.zzy.finsight.agent.runtime.DurableAgentRunner;
import com.zzy.finsight.agent.planning.ResearchIntentPolicy;
import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.component.analysis.ComparisonTickerPolicy;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.dto.agent.ResearchRunCreatedResponse;
import com.zzy.finsight.dto.agent.ResearchRunTraceResponse;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.ResearchAgentService;
import com.zzy.finsight.service.SseService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
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
    private static final java.util.regex.Pattern CLIENT_REQUEST_ID =
            java.util.regex.Pattern.compile("[A-Za-z0-9._:-]{8,64}");
    private final DurableAgentRunner runner;
    private final ResearchTaskMapper taskMapper;
    private final ResearchRunRequestCodec requestCodec;
    private final AgentTraceReader traceReader;
    private final SseService sseService;
    private final AgentEventStreamModule eventStreamModule;
    private final ExecutorService executorService;
    private final StockCodeResolver stockCodeResolver;
    private final ComparisonTickerPolicy comparisonTickerPolicy;
    private final ResearchIntentPolicy researchIntentPolicy;

    public ResearchAgentServiceImpl(
            DurableAgentRunner runner,
            ResearchTaskMapper taskMapper,
            ResearchRunRequestCodec requestCodec,
            AgentTraceReader traceReader,
            SseService sseService,
            AgentEventStreamModule eventStreamModule,
            @Qualifier("agentExecutor") ExecutorService executorService,
            StockCodeResolver stockCodeResolver,
            ComparisonTickerPolicy comparisonTickerPolicy,
            ResearchIntentPolicy researchIntentPolicy
    ) {
        this.runner = runner;
        this.taskMapper = taskMapper;
        this.requestCodec = requestCodec;
        this.traceReader = traceReader;
        this.sseService = sseService;
        this.eventStreamModule = eventStreamModule;
        this.executorService = executorService;
        this.stockCodeResolver = stockCodeResolver;
        this.comparisonTickerPolicy = comparisonTickerPolicy;
        this.researchIntentPolicy = researchIntentPolicy;
    }

    @Override
    public ResearchRunCreatedResponse create(
            long ownerId,
            ResearchRunRequest request,
            String clientRequestId
    ) {
        String normalizedClientRequestId = normalizeClientRequestId(clientRequestId);
        normalizeAndValidate(request);
        java.util.Optional<TaskExecutionRecord> existing = taskMapper.findExecutionByClientRequestId(
                ownerId, normalizedClientRequestId
        );
        if (existing.isPresent()) {
            return resumeExisting(ownerId, existing.orElseThrow(), request);
        }

        request.setThreadId(runner.resolveThreadId(request));
        long taskId;
        try {
            taskId = runner.createNewTask(ownerId, request, normalizedClientRequestId);
        } catch (DuplicateKeyException exception) {
            TaskExecutionRecord raced = taskMapper.findExecutionByClientRequestId(ownerId, normalizedClientRequestId)
                    .orElseThrow(() -> exception);
            return resumeExisting(ownerId, raced, request);
        }

        String status = schedule(ownerId, taskId, request.getThreadId(), request) ? "CREATED" : "FAILED";
        return new ResearchRunCreatedResponse(taskId, request.getThreadId(), status, false);
    }

    @Override
    public void run(long ownerId, ResearchRunRequest request, SseEmitter emitter) {
        normalizeAndValidate(request);
        AgentEventListener listener = eventListener(emitter);
        try {
            executorService.submit(() -> runner.runNew(ownerId, request, listener));
        } catch (RejectedExecutionException exception) {
            listener.onError(new IllegalStateException("当前 Agent 任务较多，请稍后重试", exception));
        }
    }

    private void normalizeAndValidate(ResearchRunRequest request) {
        StockSubject subject = stockCodeResolver.resolve(request.getTicker());
        researchIntentPolicy.validate(request.getResearchIntent(), subject.assetType());
        request.setTicker(subject.fullCode());
        request.setComparisonTickers(comparisonTickerPolicy.normalize(
                subject, request.getComparisonTickers(), request.getResearchDepth()
        ));
    }

    /** 把已持久化任务提交到有界执行器，队列拒绝时同步落失败状态。 */
    private boolean schedule(long ownerId, long taskId, String threadId, ResearchRunRequest request) {
        try {
            executorService.submit(() -> runner.runExisting(
                    ownerId, taskId, threadId, request, AgentEventListener.noop()
            ));
            return true;
        } catch (RejectedExecutionException exception) {
            taskMapper.finishAgent(taskId, "FAILED", "QUEUE_REJECTED", "Agent 执行队列已满");
            return false;
        }
    }

    /** 校验同一幂等键未被不同研究请求复用。 */
    private ResearchRunCreatedResponse existingReceipt(
            TaskExecutionRecord existing,
            ResearchRunRequest request
    ) {
        if (request.getThreadId() == null || request.getThreadId().isBlank()) {
            request.setThreadId(existing.threadId());
        }
        String payload = requestCodec.toJson(request);
        if (!java.util.Objects.equals(payload, existing.requestPayload())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "幂等键已关联不同研究请求");
        }
        return new ResearchRunCreatedResponse(
                existing.id(), existing.threadId(), existing.status(), true
        );
    }

    /** 复用权威任务回执，并补调度宕机窗口中遗留的 CREATED 任务。 */
    private ResearchRunCreatedResponse resumeExisting(
            long ownerId,
            TaskExecutionRecord existing,
            ResearchRunRequest request
    ) {
        ResearchRunCreatedResponse receipt = existingReceipt(existing, request);
        if (!"CREATED".equals(existing.status())) {
            return receipt;
        }
        boolean scheduled = schedule(ownerId, existing.id(), existing.threadId(), request);
        return new ResearchRunCreatedResponse(
                existing.id(), existing.threadId(), scheduled ? existing.status() : "FAILED", true
        );
    }

    /** 规范化并限制外部客户端幂等键，避免无界索引输入。 */
    private String normalizeClientRequestId(String value) {
        String normalized = value == null ? "" : value.trim();
        if (!CLIENT_REQUEST_ID.matcher(normalized).matches()) {
            throw new IllegalArgumentException("Idempotency-Key 必须为 8 到 64 位字母、数字或 ._:-");
        }
        return normalized;
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
    public SseEmitter subscribe(long ownerId, long taskId, long afterSequence) {
        TaskExecutionRecord task = taskMapper.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到 Research Agent 任务"));
        boolean terminal = Set.of("COMPLETED", "FAILED", "INSUFFICIENT_EVIDENCE", "CANCELLED")
                .contains(task.status());
        return eventStreamModule.subscribe(taskId, afterSequence, terminal);
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
                    sseService.sendAgentEvent(emitter, event);
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
