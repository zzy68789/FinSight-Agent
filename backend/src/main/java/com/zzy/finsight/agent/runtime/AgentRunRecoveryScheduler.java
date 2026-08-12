package com.zzy.finsight.agent.runtime;

import com.zzy.finsight.agent.event.AgentEventListener;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 扫描心跳过期的 Research Agent 任务并从最近完整 turn 恢复。
 */
@Component
public class AgentRunRecoveryScheduler {
    private final ResearchTaskMapper taskMapper;
    private final DurableAgentRunner runner;
    private final ResearchRunRequestCodec requestCodec;
    private final ExecutorService executorService;
    private final Duration staleAfter;

    public AgentRunRecoveryScheduler(
            ResearchTaskMapper taskMapper,
            DurableAgentRunner runner,
            ResearchRunRequestCodec requestCodec,
            @Qualifier("agentExecutor") ExecutorService executorService,
            @Value("${finsight.agent.recovery-stale-after:PT5M}") Duration staleAfter
    ) {
        this.taskMapper = taskMapper;
        this.runner = runner;
        this.requestCodec = requestCodec;
        this.executorService = executorService;
        this.staleAfter = staleAfter == null || staleAfter.isZero() || staleAfter.isNegative()
                ? Duration.ofMinutes(5) : staleAfter;
    }

    /** 每分钟扫描一次停滞任务，最多领取十条并交给有界执行器恢复。 */
    @Scheduled(fixedDelayString = "${finsight.agent.recovery-interval-ms:60000}")
    public void recover() {
        LocalDateTime heartbeatBefore = LocalDateTime.now().minus(staleAfter);
        for (TaskExecutionRecord task : taskMapper.findStaleAgentRunning(heartbeatBefore, 10)) {
            if (!taskMapper.markStaleRetrying(task.id(), heartbeatBefore)) {
                continue;
            }
            try {
                executorService.submit(() -> runner.runExisting(
                        task.ownerId(),
                        task.id(),
                        task.threadId(),
                        requestCodec.fromJson(task.requestPayload()),
                        AgentEventListener.noop()
                ));
            } catch (RejectedExecutionException exception) {
                taskMapper.finishAgent(task.id(), "FAILED", "QUEUE_REJECTED", "Agent 恢复队列已满");
            }
        }
    }
}
