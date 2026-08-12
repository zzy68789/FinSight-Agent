package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.agent.memory.AgentCheckpointCodec;
import com.zzy.finsight.agent.memory.AgentState;
import com.zzy.finsight.agent.planning.ResearchPlanner;
import com.zzy.finsight.domain.CheckpointRecord;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

/**
 * 负责 Research Agent 任务创建、租约获取、检查点恢复和失败收口。
 */
@Component
public class DurableAgentRunner {
    private final ResearchAgentRuntime runtime;
    private final AgentBudgetGuard budgetGuard;
    private final ResearchTaskMapper taskMapper;
    private final CheckpointMapper checkpointMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final TaskRuntimeStateService runtimeStateService;
    private final ResearchRunRequestCodec requestCodec;
    private final AgentCheckpointCodec checkpointCodec;
    private final String leaseOwner = "research-agent-" + UUID.randomUUID();

    public DurableAgentRunner(
            ResearchAgentRuntime runtime,
            AgentBudgetGuard budgetGuard,
            ResearchTaskMapper taskMapper,
            CheckpointMapper checkpointMapper,
            AgentStepLogMapper stepLogMapper,
            TaskRuntimeStateService runtimeStateService,
            ResearchRunRequestCodec requestCodec,
            AgentCheckpointCodec checkpointCodec
    ) {
        this.runtime = runtime;
        this.budgetGuard = budgetGuard;
        this.taskMapper = taskMapper;
        this.checkpointMapper = checkpointMapper;
        this.stepLogMapper = stepLogMapper;
        this.runtimeStateService = runtimeStateService;
        this.requestCodec = requestCodec;
        this.checkpointCodec = checkpointCodec;
    }

    /** 创建并执行新的 Research Agent 任务。 */
    public long runNew(long ownerId, ResearchRunRequest request, AgentEventListener listener) {
        String threadId = threadId(request);
        request.setThreadId(threadId);
        long taskId = taskMapper.createAgent(
                ownerId,
                threadId,
                request.getResearchQuestion(),
                "agent-" + request.getSearchMode(),
                requestCodec.toJson(request),
                ResearchPlanner.PLANNER_VERSION,
                ResearchAgentRuntime.TOOLSET_VERSION,
                ResearchAgentRuntime.POLICY_VERSION
        );
        runExisting(ownerId, taskId, threadId, request, listener);
        return taskId;
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
        if (!taskMapper.startAttempt(taskId, leaseOwner, LocalDateTime.now().plusMinutes(5))) {
            return;
        }
        String contextHash = requestContextHash(request);
        AgentState state = restore(taskId, contextHash).orElseGet(AgentState::new);
        state.setTaskId(taskId);
        state.setThreadId(threadId);
        state.setRequest(request);
        state.setContextHash(contextHash);
        runtimeStateService.taskCreated(taskId, threadId);
        runtimeStateService.markStatus(taskId, "RUNNING");
        try {
            runtime.execute(ownerId, state, budgetGuard.resolve(request), leaseOwner, events);
        } catch (RuntimeException exception) {
            taskMapper.finishAgent(taskId, "FAILED", "RUNTIME_ERROR", exception.getMessage());
            runtimeStateService.markStatus(taskId, "FAILED");
            stepLogMapper.saveError(taskId, "research_agent_runtime", exception);
            try {
                events.onError(exception);
            } catch (RuntimeException ignored) {
                // SSE 连接断开不改变任务失败状态。
            }
        }
    }

    /** 返回请求对应的稳定检查点上下文摘要。 */
    public String requestContextHash(ResearchRunRequest request) {
        String canonical = String.join("|",
                safe(request.getTicker()).toUpperCase(java.util.Locale.ROOT),
                safe(request.getResearchQuestion()).replaceAll("\\s+", " ").trim(),
                request.getAsOfDate().toString(),
                request.getTimeHorizon(),
                request.getResearchDepth(),
                request.getSearchMode(),
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

    private Optional<AgentState> restore(long taskId, String contextHash) {
        Optional<CheckpointRecord> checkpoint = checkpointMapper.findLatest(
                taskId, "AGENT_STATE", contextHash
        );
        return checkpoint.flatMap(checkpointCodec::decode);
    }

    private String threadId(ResearchRunRequest request) {
        if (request.getThreadId() != null && !request.getThreadId().isBlank()) {
            return request.getThreadId().trim();
        }
        return "agent-" + request.getTicker().toUpperCase(java.util.Locale.ROOT) + "-" + UUID.randomUUID();
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
