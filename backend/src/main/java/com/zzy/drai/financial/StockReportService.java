package com.zzy.drai.financial;

import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.CheckpointRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import com.zzy.drai.service.ReportService;
import com.zzy.drai.service.SseService;
import com.zzy.drai.service.TaskRuntimeStateService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Service
public class StockReportService {
    private final StockReportWorkflow workflow;
    private final FinancialSnapshotRepository snapshotRepository;
    private final ResearchTaskRepository taskRepository;
    private final AgentStepLogRepository stepLogRepository;
    private final CheckpointRepository checkpointRepository;
    private final TaskRuntimeStateService runtimeStateService;
    private final ReportService reportService;
    private final SseService sseService;
    private final ExecutorService executorService = Executors.newCachedThreadPool();

    public StockReportService(
            StockReportWorkflow workflow,
            FinancialSnapshotRepository snapshotRepository,
            ResearchTaskRepository taskRepository,
            AgentStepLogRepository stepLogRepository,
            CheckpointRepository checkpointRepository,
            TaskRuntimeStateService runtimeStateService,
            ReportService reportService,
            SseService sseService
    ) {
        this.workflow = workflow;
        this.snapshotRepository = snapshotRepository;
        this.taskRepository = taskRepository;
        this.stepLogRepository = stepLogRepository;
        this.checkpointRepository = checkpointRepository;
        this.runtimeStateService = runtimeStateService;
        this.reportService = reportService;
        this.sseService = sseService;
    }

    public void run(long ownerId, StockReportRequest request, SseEmitter emitter) {
        executorService.submit(() -> runWorkflow(ownerId, request, emitter));
    }

    public void saveFeedback(long ownerId, long taskId, StockBadCaseFeedbackRequest request) {
        snapshotRepository.saveFeedback(ownerId, taskId, request);
    }

    public StockReportReplayResponse replay(long ownerId, long taskId) {
        return snapshotRepository.findReplay(ownerId, taskId)
                .orElseThrow(() -> new IllegalArgumentException("未找到股票报告回放快照"));
    }

    private void runWorkflow(long ownerId, StockReportRequest request, SseEmitter emitter) {
        String threadId = threadId(request);
        long taskId = taskRepository.create(ownerId, threadId, "A股股票投研报告：" + request.getTicker(), "stock-" + request.getSearchMode());
        runtimeStateService.taskCreated(taskId, threadId);
        try {
            taskRepository.markRunning(taskId);
            runtimeStateService.markStatus(taskId, "RUNNING");

            StockSubject subject = workflow.resolve(request);
            send(taskId, threadId, emitter, "stock_resolve", mapOf(
                    "subject", subject,
                    "disclaimer", "仅作研究辅助，不构成投资建议"
            ));

            FinancialSnapshot snapshot = workflow.snapshot(subject, request);
            long snapshotId = snapshotRepository.saveSnapshot(ownerId, taskId, threadId, snapshot, "COLLECTED");
            send(taskId, threadId, emitter, "data_snapshot", mapOf(
                    "snapshotId", snapshotId,
                    "subject", subject,
                    "evidenceCount", snapshot.evidenceItems().size(),
                    "missingCount", snapshot.evidenceItems().stream().filter(item -> !item.effective()).count()
            ));

            List<FinancialMetricResult> metrics = workflow.metrics(snapshot);
            snapshotRepository.saveMetrics(snapshotId, taskId, metrics);
            send(taskId, threadId, emitter, "metric_engine", mapOf("metrics", metrics));

            send(taskId, threadId, emitter, "evidence_collect", mapOf(
                    "evidence", snapshot.evidenceItems(),
                    "effectiveCount", snapshot.evidenceItems().stream().filter(FinancialEvidenceItem::effective).count()
            ));

            CitationReviewResult review = CitationReviewResult.fail("NOT_REVIEWED");
            String report = "";
            for (int attempt = 0; attempt < 3; attempt++) {
                report = workflow.write(snapshot, metrics, attempt == 0 ? null : review);
                send(taskId, threadId, emitter, "writer", mapOf(
                        "attempt", attempt + 1,
                        "finalReport", report
                ));
                review = workflow.review(report, snapshot, metrics);
                send(taskId, threadId, emitter, "reviewer", mapOf(
                        "attempt", attempt + 1,
                        "reviewStatus", review.status(),
                        "critique", review.reason()
                ));
                if ("PASS".equals(review.status())) {
                    break;
                }
            }

            reportService.saveLatest(ownerId, threadId, taskId, report, review.status(), review.reason());
            taskRepository.markCompleted(taskId);
            runtimeStateService.markStatus(taskId, "COMPLETED");
            send(taskId, threadId, emitter, "done", mapOf(
                    "taskId", taskId,
                    "threadId", threadId,
                    "reviewStatus", review.status()
            ));
            sseService.done(emitter);
        } catch (Exception e) {
            taskRepository.markFailed(taskId);
            runtimeStateService.markStatus(taskId, "FAILED");
            stepLogRepository.saveError(taskId, "stock_report_workflow", e);
            sseService.error(emitter, e);
        }
    }

    private void send(long taskId, String threadId, SseEmitter emitter, String step, Object data) {
        try {
            stepLogRepository.save(taskId, step, data);
            checkpointRepository.save(threadId, taskId, data);
            runtimeStateService.recordStep(taskId, threadId, step, data);
            sseService.send(emitter, step, data);
        } catch (IOException e) {
            throw new IllegalStateException("发送股票报告 SSE 事件失败", e);
        }
    }

    private String threadId(StockReportRequest request) {
        if (request.getThreadId() != null && !request.getThreadId().isBlank()) {
            return request.getThreadId();
        }
        return "stock-" + request.getTicker() + "-" + UUID.randomUUID();
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i].toString(), values[i + 1]);
        }
        return map;
    }
}
