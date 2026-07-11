package com.zzy.finsight.financial;

import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import com.zzy.finsight.repository.ResearchTaskRepository;
import com.zzy.finsight.service.SseService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

@Service
public class StockReportService {
    private final StockReportRunner runner;
    private final FinancialSnapshotRepository snapshotRepository;
    private final SseService sseService;
    private final ExecutorService executorService;
    private final ResearchTaskRepository taskRepository;
    private final StockReportRequestCodec requestCodec;
    private final StockReportTraceService traceService;

    public StockReportService(
            StockReportRunner runner,
            FinancialSnapshotRepository snapshotRepository,
            SseService sseService,
            ResearchTaskRepository taskRepository,
            StockReportRequestCodec requestCodec,
            StockReportTraceService traceService,
            @Qualifier("workflowExecutor") ExecutorService executorService
    ) {
        this.runner = runner;
        this.snapshotRepository = snapshotRepository;
        this.sseService = sseService;
        this.taskRepository = taskRepository;
        this.requestCodec = requestCodec;
        this.traceService = traceService;
        this.executorService = executorService;
    }

    public void run(long ownerId, StockReportRequest request, SseEmitter emitter) {
        executorService.submit(() -> runner.runNew(ownerId, request, progressListener(emitter)));
    }

    public void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request) {
        snapshotRepository.saveFeedback(ownerId, taskId, request);
    }

    public StockReportReplayResponse replay(long ownerId, long taskId) {
        return snapshotRepository.findReplay(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告回放快照"));
    }

    public void retry(long ownerId, long taskId) {
        WorkflowTaskExecutionRecord task = taskRepository.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告任务"));
        if (!"FAILED".equals(task.status())) {
            throw new IllegalStateException("仅失败的股票报告任务允许重试");
        }
        if (task.attemptCount() >= 3) {
            throw new IllegalStateException("股票报告任务已达到最大重试次数");
        }
        StockReportRequest request = requestCodec.fromJson(task.requestPayload());
        if (!taskRepository.markRetrying(taskId, "FAILED")) {
            throw new IllegalStateException("股票报告任务状态已变化，请刷新后重试");
        }
        executorService.submit(() -> runner.runExisting(
                ownerId, taskId, task.threadId(), request, StockReportProgressListener.noop()
        ));
    }

    public StockReportTraceResponse trace(long ownerId, long taskId) {
        return traceService.get(ownerId, taskId);
    }

    private StockReportProgressListener progressListener(SseEmitter emitter) {
        AtomicBoolean connected = new AtomicBoolean(true);
        return new StockReportProgressListener() {
            @Override
            public void onStep(String step, Object data) {
                if (!connected.get()) {
                    return;
                }
                try {
                    sseService.send(emitter, step, data);
                } catch (Exception e) {
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
                } catch (Exception e) {
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
