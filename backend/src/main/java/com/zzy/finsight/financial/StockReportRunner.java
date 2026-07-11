package com.zzy.finsight.financial;

import com.zzy.finsight.repository.AgentStepLogRepository;
import com.zzy.finsight.repository.CheckpointRepository;
import com.zzy.finsight.repository.ResearchTaskRepository;
import com.zzy.finsight.service.ReportService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import com.zzy.finsight.domain.ReusableReportRecord;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Component
public class StockReportRunner {
    private static final int MAX_WRITER_ATTEMPTS = 3;

    private final StockReportWorkflow workflow;
    private final FinancialSnapshotRepository snapshotRepository;
    private final ResearchTaskRepository taskRepository;
    private final AgentStepLogRepository stepLogRepository;
    private final CheckpointRepository checkpointRepository;
    private final TaskRuntimeStateService runtimeStateService;
    private final ReportService reportService;
    private final FinancialReportFingerprintService fingerprintService;
    private final StockReportRequestCodec requestCodec;
    private final MeterRegistry meterRegistry;
    private final String leaseOwner = "stock-runner-" + UUID.randomUUID();

    public StockReportRunner(
            StockReportWorkflow workflow,
            FinancialSnapshotRepository snapshotRepository,
            ResearchTaskRepository taskRepository,
            AgentStepLogRepository stepLogRepository,
            CheckpointRepository checkpointRepository,
            TaskRuntimeStateService runtimeStateService,
            ReportService reportService,
            FinancialReportFingerprintService fingerprintService,
            StockReportRequestCodec requestCodec,
            MeterRegistry meterRegistry
    ) {
        this.workflow = workflow;
        this.snapshotRepository = snapshotRepository;
        this.taskRepository = taskRepository;
        this.stepLogRepository = stepLogRepository;
        this.checkpointRepository = checkpointRepository;
        this.runtimeStateService = runtimeStateService;
        this.reportService = reportService;
        this.fingerprintService = fingerprintService;
        this.requestCodec = requestCodec;
        this.meterRegistry = meterRegistry;
    }

    public long runNew(long ownerId, StockReportRequest request, StockReportProgressListener listener) {
        String threadId = threadId(request);
        long taskId = taskRepository.create(
                ownerId,
                threadId,
                "A股股票投研报告：" + request.getTicker(),
                "stock-" + request.getSearchMode(),
                requestCodec.toJson(request)
        );
        run(ownerId, taskId, threadId, request, listener);
        return taskId;
    }

    public void runExisting(
            long ownerId,
            long taskId,
            String threadId,
            StockReportRequest request,
            StockReportProgressListener listener
    ) {
        run(ownerId, taskId, threadId, request, listener);
    }

    void run(
            long ownerId,
            long taskId,
            String threadId,
            StockReportRequest request,
            StockReportProgressListener listener
    ) {
        StockReportProgressListener progress = listener == null ? StockReportProgressListener.noop() : listener;
        if (!taskRepository.startAttempt(taskId, leaseOwner, leaseUntil())) {
            return;
        }
        runtimeStateService.taskCreated(taskId, threadId);
        try {
            runtimeStateService.markStatus(taskId, "RUNNING");

            long startedAt = System.nanoTime();
            StockSubject subject = workflow.resolve(request);
            publish(taskId, threadId, progress, "stock_resolve", mapOf(
                    "subject", subject,
                    "disclaimer", "仅作研究辅助，不构成投资建议"
            ), elapsedMs(startedAt), 1);

            startedAt = System.nanoTime();
            Optional<PersistedFinancialSnapshot> persistedSnapshot = snapshotRepository.findSnapshot(ownerId, taskId);
            FinancialSnapshot snapshot = persistedSnapshot
                    .map(PersistedFinancialSnapshot::snapshot)
                    .orElseGet(() -> workflow.snapshot(subject, request));
            String dataSnapshotHash = persistedSnapshot
                    .map(PersistedFinancialSnapshot::dataSnapshotHash)
                    .filter(value -> value != null && !value.isBlank())
                    .orElseGet(() -> fingerprintService.dataSnapshotHash(snapshot));
            String generationContextHash = fingerprintService.generationContextHash(dataSnapshotHash);
            long snapshotId = persistedSnapshot
                    .map(PersistedFinancialSnapshot::id)
                    .orElseGet(() -> snapshotRepository.saveSnapshot(
                            ownerId, taskId, threadId, snapshot, "COLLECTED", dataSnapshotHash
                    ));
            publish(taskId, threadId, progress, "data_snapshot", mapOf(
                    "snapshotId", snapshotId,
                    "subject", subject,
                    "evidenceCount", snapshot.evidenceItems().size(),
                    "missingCount", snapshot.evidenceItems().stream().filter(item -> !item.effective()).count(),
                    "dataSnapshotHash", dataSnapshotHash,
                    "generationContextHash", generationContextHash
            ), elapsedMs(startedAt), 1);

            startedAt = System.nanoTime();
            List<FinancialMetricResult> persistedMetrics = snapshotRepository.findMetrics(taskId);
            List<FinancialMetricResult> metrics = persistedMetrics.isEmpty() ? workflow.metrics(snapshot) : persistedMetrics;
            if (persistedMetrics.isEmpty()) {
                snapshotRepository.saveMetrics(snapshotId, taskId, metrics);
            }
            publish(taskId, threadId, progress, "metric_engine", mapOf("metrics", metrics), elapsedMs(startedAt), 1);

            startedAt = System.nanoTime();
            FinancialRiskAssessment riskAssessment = workflow.riskAssessment(metrics, snapshot);
            publish(taskId, threadId, progress, "risk_assessment", mapOf("riskAssessment", riskAssessment), elapsedMs(startedAt), 1);

            Optional<ReusableReportRecord> existingTaskReport = reportService.findByTask(ownerId, taskId);
            if (existingTaskReport.isPresent()) {
                ReusableReportRecord existing = existingTaskReport.orElseThrow();
                completeTask(taskId, threadId, progress, existing.reviewStatus(), 0, false);
                return;
            }

            Optional<ReusableReportRecord> reusableReport = reportService.findReusable(ownerId, generationContextHash);
            if (reusableReport.isPresent()) {
                ReusableReportRecord cached = reusableReport.orElseThrow();
                reportService.saveLatest(
                        ownerId, threadId, taskId, cached.content(), "PASS", "",
                        snapshotId, dataSnapshotHash, generationContextHash, cached.id()
                );
                publish(taskId, threadId, progress, "cache_hit", mapOf(
                        "cacheHit", true,
                        "reusedFromReportId", cached.id(),
                        "dataSnapshotHash", dataSnapshotHash,
                        "generationContextHash", generationContextHash
                ), 0L, 1);
                meterRegistry.counter("finsight.stock.report.cache", "result", "hit").increment();
                completeTask(taskId, threadId, progress, "PASS", 0, true);
                return;
            }
            meterRegistry.counter("finsight.stock.report.cache", "result", "miss").increment();

            publish(taskId, threadId, progress, "evidence_collect", mapOf(
                    "evidence", snapshot.evidenceItems(),
                    "effectiveCount", snapshot.evidenceItems().stream().filter(FinancialEvidenceItem::effective).count(),
                    "stageResults", snapshot.stageResults(),
                    "retrievalResults", snapshot.retrievalResults()
            ), 0L, 1);

            CitationReviewResult review = CitationReviewResult.fail("NOT_REVIEWED");
            FinancialComplianceReviewResult compliance = new FinancialComplianceReviewResult("FAIL", BigDecimal.ZERO, List.of());
            String report = "";
            int writerAttempts = 0;
            for (int attempt = 1; attempt <= MAX_WRITER_ATTEMPTS; attempt++) {
                writerAttempts = attempt;
                startedAt = System.nanoTime();
                report = workflow.write(snapshot, metrics, riskAssessment, attempt == 1 ? null : review);
                publish(taskId, threadId, progress, "writer", mapOf(
                        "attempt", attempt,
                        "finalReport", report
                ), elapsedMs(startedAt), attempt);

                startedAt = System.nanoTime();
                review = workflow.review(report, snapshot, metrics);
                compliance = workflow.compliance(report, review);
                publish(taskId, threadId, progress, "reviewer", mapOf(
                        "attempt", attempt,
                        "reviewStatus", review.status(),
                        "critique", review.reason(),
                        "compliance", compliance
                ), elapsedMs(startedAt), attempt);
                if (isPass(review.status()) && isPass(compliance.status())) {
                    break;
                }
            }

            startedAt = System.nanoTime();
            Optional<FinancialEvaluationResult> evaluation = workflow.evaluation(report, snapshot, metrics);
            if (evaluation.isPresent()) {
                publish(taskId, threadId, progress, "evaluation", mapOf(
                        "evaluation", evaluation.orElseThrow(),
                        "rewriteCount", Math.max(0, writerAttempts - 1)
                ), elapsedMs(startedAt), writerAttempts);
            }

            boolean reviewPassed = isPass(review.status());
            boolean compliancePassed = isPass(compliance.status());
            boolean evaluationPassed = evaluation.map(result -> isPass(result.status())).orElse(true);
            String finalStatus = reviewPassed && compliancePassed && evaluationPassed ? "PASS" : "FAIL";
            String critique = isPass(finalStatus)
                    ? ""
                    : (review.reason() + " " + compliance.issues() + " "
                    + evaluation.map(FinancialEvaluationResult::failedReasons).orElse(List.of())).trim();
            reportService.saveLatest(
                    ownerId, threadId, taskId, report, finalStatus, critique,
                    snapshotId, dataSnapshotHash, generationContextHash, null
            );
            completeTask(taskId, threadId, progress, finalStatus, Math.max(0, writerAttempts - 1), false);
        } catch (Exception e) {
            taskRepository.markFailed(taskId, e.getMessage());
            runtimeStateService.markStatus(taskId, "FAILED");
            stepLogRepository.saveError(taskId, "stock_report_workflow", e);
            meterRegistry.counter("finsight.stock.workflow.task", "result", "failed").increment();
            notifyError(progress, e);
        }
    }

    private void completeTask(
            long taskId,
            String threadId,
            StockReportProgressListener progress,
            String finalStatus,
            int rewriteCount,
            boolean cacheHit
    ) {
        taskRepository.markCompleted(taskId);
        runtimeStateService.markStatus(taskId, "COMPLETED");
        meterRegistry.counter("finsight.stock.workflow.task", "result", "completed").increment();
        publish(taskId, threadId, progress, "done", mapOf(
                "taskId", taskId,
                "threadId", threadId,
                "reviewStatus", finalStatus,
                "rewriteCount", rewriteCount,
                "cacheHit", cacheHit
        ), 0L, Math.max(1, rewriteCount + 1));
        notifyDone(progress);
    }

    private void publish(
            long taskId,
            String threadId,
            StockReportProgressListener listener,
            String step,
            Object data,
            long durationMs,
            int attemptNo
    ) {
        stepLogRepository.save(taskId, step, data, attemptNo, durationMs);
        Timer.builder("finsight.stock.workflow.stage.duration")
                .tag("stage", step)
                .register(meterRegistry)
                .record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
        checkpointRepository.save(threadId, taskId, data);
        runtimeStateService.recordStep(taskId, threadId, step, data);
        taskRepository.updateStage(taskId, step.toUpperCase(java.util.Locale.ROOT), leaseOwner, leaseUntil());
        try {
            listener.onStep(step, data);
        } catch (RuntimeException ignored) {
            // 客户端进度通道断开不影响后台任务。
        }
    }

    private void notifyDone(StockReportProgressListener listener) {
        try {
            listener.onDone();
        } catch (RuntimeException ignored) {
            // 客户端进度通道断开不影响后台任务。
        }
    }

    private void notifyError(StockReportProgressListener listener, Throwable throwable) {
        try {
            listener.onError(throwable);
        } catch (RuntimeException ignored) {
            // 错误通知失败时任务状态已经持久化。
        }
    }

    private boolean isPass(String status) {
        return "PASS".equals(status);
    }

    private long elapsedMs(long startedAt) {
        return Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
    }

    private LocalDateTime leaseUntil() {
        return LocalDateTime.now().plusMinutes(5);
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
