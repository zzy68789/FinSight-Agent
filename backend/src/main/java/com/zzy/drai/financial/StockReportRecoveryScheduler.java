package com.zzy.drai.financial;

import com.zzy.drai.domain.WorkflowTaskExecutionRecord;
import com.zzy.drai.repository.ResearchTaskRepository;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;

@Component
public class StockReportRecoveryScheduler {
    private final ResearchTaskRepository taskRepository;
    private final StockReportRunner runner;
    private final StockReportRequestCodec requestCodec;
    private final ExecutorService executorService;
    private final Duration staleAfter;
    private final MeterRegistry meterRegistry;

    public StockReportRecoveryScheduler(
            ResearchTaskRepository taskRepository,
            StockReportRunner runner,
            StockReportRequestCodec requestCodec,
            @Qualifier("workflowExecutor") ExecutorService executorService,
            @Value("${drai.workflow.recovery-stale-after:PT5M}") Duration staleAfter,
            MeterRegistry meterRegistry
    ) {
        this.taskRepository = taskRepository;
        this.runner = runner;
        this.requestCodec = requestCodec;
        this.executorService = executorService;
        this.staleAfter = staleAfter;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${drai.workflow.recovery-interval-ms:60000}")
    public void recoverStaleTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minus(staleAfter);
        for (WorkflowTaskExecutionRecord task : taskRepository.findStaleRunning(cutoff, 20)) {
            if (task.attemptCount() >= 3) {
                taskRepository.markFailed(task.id(), "工作流心跳超时且已达到最大尝试次数");
                meterRegistry.counter("drai.stock.workflow.recovery", "result", "exhausted").increment();
                continue;
            }
            StockReportRequest request;
            try {
                request = requestCodec.fromJson(task.requestPayload());
            } catch (RuntimeException e) {
                taskRepository.markFailed(task.id(), e.getMessage());
                meterRegistry.counter("drai.stock.workflow.recovery", "result", "invalid_request").increment();
                continue;
            }
            if (!taskRepository.markStaleRetrying(task.id(), cutoff)) {
                continue;
            }
            executorService.submit(() -> runner.runExisting(
                    task.ownerId(), task.id(), task.threadId(), request, StockReportProgressListener.noop()
            ));
            meterRegistry.counter("drai.stock.workflow.recovery", "result", "resubmitted").increment();
        }
    }
}
