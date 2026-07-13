package com.zzy.finsight.service.impl;

import com.zzy.finsight.component.workflow.StockReportProgressListener;
import com.zzy.finsight.component.workflow.StockReportRunner;
import com.zzy.finsight.component.workflow.StockReportTraceReader;
import com.zzy.finsight.dto.stock.StockBadCaseFeedbackRequest;
import com.zzy.finsight.dto.stock.StockReportReplayResponse;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.dto.stock.StockReportTraceResponse;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;


import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.infrastructure.serialization.StockReportRequestCodec;
import com.zzy.finsight.service.SseService;
import com.zzy.finsight.service.StockReportService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 实现股票报告异步执行、重试、回放和反馈业务。
 */
@Service
public class StockReportServiceImpl implements StockReportService {
    private final StockReportRunner runner;
    private final FinancialSnapshotMapper snapshotMapper;
    private final SseService sseService;
    private final ExecutorService executorService;
    private final ResearchTaskMapper taskMapper;
    private final StockReportRequestCodec requestCodec;
    private final StockReportTraceReader traceReader;

    public StockReportServiceImpl(
            StockReportRunner runner,
            FinancialSnapshotMapper snapshotMapper,
            SseService sseService,
            ResearchTaskMapper taskMapper,
            StockReportRequestCodec requestCodec,
            StockReportTraceReader traceReader,
            @Qualifier("workflowExecutor") ExecutorService executorService
    ) {
        this.runner = runner;
        this.snapshotMapper = snapshotMapper;
        this.sseService = sseService;
        this.taskMapper = taskMapper;
        this.requestCodec = requestCodec;
        this.traceReader = traceReader;
        this.executorService = executorService;
    }

    public void run(long ownerId, StockReportRequest request, SseEmitter emitter) {
        executorService.submit(() -> runner.runNew(ownerId, request, progressListener(emitter)));
    }

    public void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request) {
        snapshotMapper.saveFeedback(ownerId, taskId, request);
    }

    public StockReportReplayResponse replay(long ownerId, long taskId) {
        return snapshotMapper.findReplay(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告回放快照"));
    }

    public void retry(long ownerId, long taskId) {
        WorkflowTaskExecutionRecord task = taskMapper.findExecution(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告任务"));
        if (!"FAILED".equals(task.status())) {
            throw new IllegalStateException("仅失败的股票报告任务允许重试");
        }
        if (task.attemptCount() >= 3) {
            throw new IllegalStateException("股票报告任务已达到最大重试次数");
        }
        StockReportRequest request = requestCodec.fromJson(task.requestPayload());
        if (!taskMapper.markRetrying(taskId, "FAILED")) {
            throw new IllegalStateException("股票报告任务状态已变化，请刷新后重试");
        }
        executorService.submit(() -> runner.runExisting(
                ownerId, taskId, task.threadId(), request, StockReportProgressListener.noop()
        ));
    }

    public StockReportTraceResponse trace(long ownerId, long taskId) {
        return traceReader.get(ownerId, taskId);
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
