package com.zzy.finsight.component.workflow;

import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.infrastructure.serialization.StockReportRequestCodec;


import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;

/**
 * 扫描并恢复心跳超时的股票报告任务。
 */
@Component
public class StockReportRecoveryScheduler {
    private final ResearchTaskMapper taskMapper;
    private final StockReportRunner runner;
    private final StockReportRequestCodec requestCodec;
    private final ExecutorService executorService;
    private final Duration staleAfter;
    private final MeterRegistry meterRegistry;

    public StockReportRecoveryScheduler(
            ResearchTaskMapper taskMapper,
            StockReportRunner runner,
            StockReportRequestCodec requestCodec,
            @Qualifier("workflowExecutor") ExecutorService executorService,
            @Value("${finsight.workflow.recovery-stale-after:PT5M}") Duration staleAfter,
            MeterRegistry meterRegistry
    ) {
        this.taskMapper = taskMapper;
        this.runner = runner;
        this.requestCodec = requestCodec;
        this.executorService = executorService;
        this.staleAfter = staleAfter;
        this.meterRegistry = meterRegistry;
    }

    @Scheduled(fixedDelayString = "${finsight.workflow.recovery-interval-ms:60000}")
    /** 定期恢复长时间未更新的投研任务。 */
    public void recoverStaleTasks() {
        LocalDateTime cutoff = LocalDateTime.now().minus(staleAfter);
        for (WorkflowTaskExecutionRecord task : taskMapper.findStaleRunning(cutoff, 20)) {
            if (task.attemptCount() >= 3) {
                taskMapper.markFailed(task.id(), "工作流心跳超时且已达到最大尝试次数");
                meterRegistry.counter("finsight.stock.workflow.recovery", "result", "exhausted").increment();
                continue;
            }
            StockReportRequest request;
            try {
                request = requestCodec.fromJson(task.requestPayload());
            } catch (RuntimeException e) {
                taskMapper.markFailed(task.id(), e.getMessage());
                meterRegistry.counter("finsight.stock.workflow.recovery", "result", "invalid_request").increment();
                continue;
            }
            if (!taskMapper.markStaleRetrying(task.id(), cutoff)) {
                continue;
            }
            try {
                executorService.submit(() -> runner.runExisting(
                        task.ownerId(), task.id(), task.threadId(), request, StockReportProgressListener.noop()
                ));
                meterRegistry.counter("finsight.stock.workflow.recovery", "result", "resubmitted").increment();
            } catch (RejectedExecutionException e) {
                taskMapper.markFailed(task.id(), "恢复任务提交失败：工作流执行队列已满");
                meterRegistry.counter("finsight.stock.workflow.recovery", "result", "rejected").increment();
            }
        }
    }
}
