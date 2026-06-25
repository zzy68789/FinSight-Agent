package com.zzy.drai.service;

import com.zzy.drai.agent.graph.ResearchGraphFactory;
import com.zzy.drai.agent.state.ResearchState;
import com.zzy.drai.dto.ChatRequest;
import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.CheckpointRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import org.bsc.langgraph4j.NodeOutput;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class ResearchTaskService {
    private final ResearchGraphFactory graphFactory;
    private final SseService sseService;
    private final ResearchTaskRepository taskRepository;
    private final AgentStepLogRepository stepLogRepository;
    private final CheckpointRepository checkpointRepository;
    private final TaskRuntimeStateService runtimeStateService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public ResearchTaskService(
            ResearchGraphFactory graphFactory,
            SseService sseService,
            ResearchTaskRepository taskRepository,
            AgentStepLogRepository stepLogRepository,
            CheckpointRepository checkpointRepository,
            TaskRuntimeStateService runtimeStateService
    ) {
        this.graphFactory = graphFactory;
        this.sseService = sseService;
        this.taskRepository = taskRepository;
        this.stepLogRepository = stepLogRepository;
        this.checkpointRepository = checkpointRepository;
        this.runtimeStateService = runtimeStateService;
    }

    public void run(ChatRequest request, SseEmitter emitter) {
        executorService.submit(() -> {
            long taskId = taskRepository.create(request.getThreadId(), request.getQuery(), request.getSearchMode());
            runtimeStateService.taskCreated(taskId, request.getThreadId());
            try {
                taskRepository.markRunning(taskId);
                runtimeStateService.markStatus(taskId, "RUNNING");
                Map<String, Object> initialState = Map.of(
                        ResearchState.TASK_ID, taskId,
                        ResearchState.THREAD_ID, request.getThreadId(),
                        ResearchState.QUERY, request.getQuery(),
                        ResearchState.SEARCH_MODE, request.getSearchMode(),
                        ResearchState.REVISION_NUMBER, 0
                );
                graphFactory.create().stream(initialState).stream()
                        .filter(output -> !output.isSTART() && !output.isEND())
                        .forEach(output -> send(taskId, request.getThreadId(), emitter, output));
                taskRepository.markCompleted(taskId);
                runtimeStateService.markStatus(taskId, "COMPLETED");
                sseService.done(emitter);
            } catch (Exception e) {
                taskRepository.markFailed(taskId);
                runtimeStateService.markStatus(taskId, "FAILED");
                stepLogRepository.saveError(taskId, "workflow", e);
                sseService.error(emitter, e);
            }
        });
    }

    private void send(long taskId, String threadId, SseEmitter emitter, NodeOutput<ResearchState> output) {
        try {
            stepLogRepository.save(taskId, output.node(), output.state().data());
            checkpointRepository.save(threadId, taskId, output.state().data());
            runtimeStateService.recordStep(taskId, threadId, output.node(), output.state().data());
            sseService.send(emitter, output.node(), output.state().data());
        } catch (IOException e) {
            throw new IllegalStateException("发送 SSE 事件失败", e);
        }
    }
}
