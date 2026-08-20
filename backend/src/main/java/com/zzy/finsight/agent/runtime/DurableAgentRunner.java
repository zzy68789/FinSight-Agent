package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.memory.AgentStateStore;
import com.zzy.finsight.agent.planning.ResearchPlanner;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 负责 Research Agent 任务创建、租约获取、检查点恢复和失败收口。
 */
@Component
public class DurableAgentRunner {
    private final ResearchAgentRuntime runtime;
    private final AgentBudgetGuard budgetGuard;
    private final ResearchTaskMapper taskMapper;
    private final AgentStateStore stateStore;
    private final DurableTurnCommitModule turnCommitModule;
    private final AgentStepLogMapper stepLogMapper;
    private final TaskRuntimeStateService runtimeStateService;
    private final ResearchRunRequestCodec requestCodec;
    private final String leaseOwner = "research-agent-" + UUID.randomUUID();

    public DurableAgentRunner(
            ResearchAgentRuntime runtime,
            AgentBudgetGuard budgetGuard,
            ResearchTaskMapper taskMapper,
            AgentStateStore stateStore,
            DurableTurnCommitModule turnCommitModule,
            AgentStepLogMapper stepLogMapper,
            TaskRuntimeStateService runtimeStateService,
            ResearchRunRequestCodec requestCodec
    ) {
        this.runtime = runtime;
        this.budgetGuard = budgetGuard;
        this.taskMapper = taskMapper;
        this.stateStore = stateStore;
        this.turnCommitModule = turnCommitModule;
        this.stepLogMapper = stepLogMapper;
        this.runtimeStateService = runtimeStateService;
        this.requestCodec = requestCodec;
    }

    /** 创建并执行新的 Research Agent 任务。 */
    public long runNew(long ownerId, ResearchRunRequest request, AgentEventListener listener) {
        long taskId = createNewTask(ownerId, request, null);
        runExisting(ownerId, taskId, request.getThreadId(), request, listener);
        return taskId;
    }

    /** 只创建持久化任务，事件订阅与异步执行可在任务回执返回后独立进行。 */
    public long createNewTask(long ownerId, ResearchRunRequest request, String clientRequestId) {
        String threadId = resolveThreadId(request);
        request.setThreadId(threadId);
        long taskId = taskMapper.createAgent(
                ownerId,
                threadId,
                request.getResearchQuestion(),
                "agent-" + request.getSearchMode(),
                requestCodec.toJson(request),
                ResearchPlanner.PLANNER_VERSION,
                ResearchAgentRuntime.TOOLSET_VERSION,
                ResearchAgentRuntime.POLICY_VERSION,
                clientRequestId
        );
        return taskId;
    }

    /** 解析或生成稳定研究线程标识并写回请求。 */
    public String resolveThreadId(ResearchRunRequest request) {
        if (request.getThreadId() != null && !request.getThreadId().isBlank()) {
            return request.getThreadId().trim();
        }
        return "agent-" + request.getTicker().toUpperCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID();
    }

    /** 从最近完整 Agent turn 检查点恢复已有任务。 */
    public void runExisting(
            long ownerId,
            long taskId,
            String threadId,
            ResearchRunRequest request,
            AgentEventListener listener
    ) {
        AgentEventListener events = listener == null ? AgentEventListener.noop() : listener;
        java.util.Optional<LeaseToken> lease = turnCommitModule.claimLease(
                taskId, leaseOwner, LocalDateTime.now().plusMinutes(5)
        );
        if (lease.isEmpty()) {
            return;
        }
        String contextHash = requestContextHash(request);
        AgentState state;
        try {
            state = stateStore.load(ownerId, taskId, contextHash).orElseGet(AgentState::new);
        } catch (RuntimeException exception) {
            turnCommitModule.failTask(
                    lease.orElseThrow(), "CHECKPOINT_RECOVERY_FAILED", exception.getMessage()
            );
            runtimeStateService.markStatus(taskId, "FAILED");
            stepLogMapper.saveError(taskId, "agent_state_recovery", exception);
            try {
                events.onError(exception);
            } catch (RuntimeException ignored) {
                // SSE 连接断开不改变 fail-closed 的恢复失败状态。
            }
            return;
        }
        state.setTaskId(taskId);
        state.setThreadId(threadId);
        state.setRequest(request);
        state.setContextHash(contextHash);
        runtimeStateService.taskCreated(taskId, threadId);
        runtimeStateService.markStatus(taskId, "RUNNING");
        try {
            runtime.execute(ownerId, state, budgetGuard.resolve(request), lease.orElseThrow(), events);
        } catch (RuntimeException exception) {
            if (isCancelled(ownerId, taskId)) {
                runtimeStateService.markStatus(taskId, "CANCELLED");
                safeDone(events);
                return;
            }
            try {
                turnCommitModule.finishTask(
                        state, lease.orElseThrow(), "FAILED", "RUNTIME_ERROR", exception.getMessage()
                );
            } catch (IllegalStateException fenced) {
                if (isCancelled(ownerId, taskId)) {
                    runtimeStateService.markStatus(taskId, "CANCELLED");
                    safeDone(events);
                    return;
                }
                throw fenced;
            }
            runtimeStateService.markStatus(taskId, "FAILED");
            stepLogMapper.saveError(taskId, "research_agent_runtime", exception);
            try {
                events.onError(exception);
            } catch (RuntimeException ignored) {
                // SSE 连接断开不改变任务失败状态。
            }
        }
    }

    private boolean isCancelled(long ownerId, long taskId) {
        return taskMapper.findExecution(ownerId, taskId)
                .map(task -> "CANCELLED".equals(task.status()))
                .orElse(false);
    }

    private void safeDone(AgentEventListener events) {
        try {
            events.onDone();
        } catch (RuntimeException ignored) {
            // 客户端断开不改变已经持久化的取消状态。
        }
    }

    /** 返回请求对应的稳定检查点上下文摘要。 */
    public String requestContextHash(ResearchRunRequest request) {
        String canonical = String.join("|",
                safe(request.getTicker()).toUpperCase(java.util.Locale.ROOT),
                safe(request.getResearchQuestion()).replaceAll("\\s+", " ").trim(),
                request.getResearchIntent().name(),
                request.getAsOfDate().toString(),
                request.getTimeHorizon(),
                request.getResearchDepth(),
                request.getSearchMode(),
                String.join(",", request.getComparisonTickers()),
                ResearchPlanner.PLANNER_VERSION,
                ResearchAgentRuntime.TOOLSET_VERSION,
                ResearchAgentRuntime.POLICY_VERSION
        );
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
