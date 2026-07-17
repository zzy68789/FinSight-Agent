package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.PersistedFinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.WorkflowCheckpointRecord;
import com.zzy.finsight.component.review.InvestmentReportWriter;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.infrastructure.serialization.StockReportRequestCodec;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;


import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.ReportService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import com.zzy.finsight.domain.ReusableReportRecord;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * 执行股票报告任务并持久化阶段、报告和状态。
 */
@Component
public class StockReportRunner {
    private static final int MAX_WRITER_ATTEMPTS = 3;

    private final StockReportWorkflow workflow;
    private final FinancialSnapshotMapper snapshotMapper;
    private final ResearchTaskMapper taskMapper;
    private final AgentStepLogMapper stepLogMapper;
    private final CheckpointMapper checkpointMapper;
    private final TaskRuntimeStateService runtimeStateService;
    private final ReportService reportService;
    private final FinancialReportFingerprinter fingerprinter;
    private final ReportReuseCoordinator reuseCoordinator;
    private final WorkflowCheckpointCodec checkpointCodec;
    private final StockReportRequestCodec requestCodec;
    private final MeterRegistry meterRegistry;
    private final WorkflowStagePersistence stagePersistence;
    private final String leaseOwner = "stock-runner-" + UUID.randomUUID();

    public StockReportRunner(
            StockReportWorkflow workflow,
            FinancialSnapshotMapper snapshotMapper,
            ResearchTaskMapper taskMapper,
            AgentStepLogMapper stepLogMapper,
            CheckpointMapper checkpointMapper,
            TaskRuntimeStateService runtimeStateService,
            ReportService reportService,
            FinancialReportFingerprinter fingerprinter,
            ReportGenerationSingleFlight singleFlight,
            WorkflowCheckpointCodec checkpointCodec,
            StockReportRequestCodec requestCodec,
            MeterRegistry meterRegistry
    ) {
        this(
                workflow, snapshotMapper, taskMapper, stepLogMapper, checkpointMapper, runtimeStateService,
                reportService, fingerprinter,
                new ReportReuseCoordinator(reportService, singleFlight, meterRegistry, Duration.ofMinutes(7)),
                checkpointCodec, requestCodec, meterRegistry,
                new WorkflowStagePersistence(stepLogMapper, checkpointMapper, taskMapper, runtimeStateService)
        );
    }

    @Autowired
    public StockReportRunner(
            StockReportWorkflow workflow,
            FinancialSnapshotMapper snapshotMapper,
            ResearchTaskMapper taskMapper,
            AgentStepLogMapper stepLogMapper,
            CheckpointMapper checkpointMapper,
            TaskRuntimeStateService runtimeStateService,
            ReportService reportService,
            FinancialReportFingerprinter fingerprinter,
            ReportReuseCoordinator reuseCoordinator,
            WorkflowCheckpointCodec checkpointCodec,
            StockReportRequestCodec requestCodec,
            MeterRegistry meterRegistry,
            WorkflowStagePersistence stagePersistence
    ) {
        this.workflow = workflow;
        this.snapshotMapper = snapshotMapper;
        this.taskMapper = taskMapper;
        this.stepLogMapper = stepLogMapper;
        this.checkpointMapper = checkpointMapper;
        this.runtimeStateService = runtimeStateService;
        this.reportService = reportService;
        this.fingerprinter = fingerprinter;
        this.reuseCoordinator = reuseCoordinator;
        this.checkpointCodec = checkpointCodec;
        this.requestCodec = requestCodec;
        this.meterRegistry = meterRegistry;
        this.stagePersistence = stagePersistence;
    }

    /** 创建并同步执行新的投研任务。 */
    public long runNew(long ownerId, StockReportRequest request, StockReportProgressListener listener) {
        String threadId = threadId(request);
        long taskId = taskMapper.create(
                ownerId,
                threadId,
                "A股股票投研报告：" + request.getTicker(),
                "stock-" + request.getSearchMode(),
                requestCodec.toJson(request)
        );
        run(ownerId, taskId, threadId, request, listener);
        return taskId;
    }

    /** 从已持久化状态继续执行投研任务。 */
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
        if (!taskMapper.startAttempt(taskId, leaseOwner, leaseUntil())) {
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
            Optional<PersistedFinancialSnapshot> persistedSnapshot = snapshotMapper.findSnapshot(ownerId, taskId);
            FinancialSnapshot snapshot = persistedSnapshot
                    .map(PersistedFinancialSnapshot::snapshot)
                    .orElseGet(() -> workflow.snapshot(ownerId, subject, request));
            String dataSnapshotHash = persistedSnapshot
                    .map(PersistedFinancialSnapshot::dataSnapshotHash)
                    .filter(value -> value != null && !value.isBlank())
                    .orElseGet(() -> fingerprinter.dataSnapshotHash(snapshot));
            String generationContextHash = fingerprinter.generationContextHash(dataSnapshotHash);
            long snapshotId = persistedSnapshot
                    .map(PersistedFinancialSnapshot::id)
                    .orElseGet(() -> snapshotMapper.saveSnapshot(
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
            List<FinancialMetricResult> persistedMetrics = snapshotMapper.findMetrics(taskId);
            List<FinancialMetricResult> metrics = persistedMetrics.isEmpty() ? workflow.metrics(snapshot) : persistedMetrics;
            if (persistedMetrics.isEmpty()) {
                snapshotMapper.saveMetrics(snapshotId, taskId, metrics);
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

            ReportReuseCoordinator.ReuseOutcome reuseOutcome = reuseCoordinator.coordinate(
                    ownerId,
                    generationContextHash,
                    (candidate, origin) -> validateReusableCandidate(
                            candidate, origin, taskId, threadId, progress, snapshot, metrics, generationContextHash
                    ),
                    () -> {
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
            int nextWriterAttempt = 1;

            Optional<WorkflowCheckpointRecord> writerRecord = checkpointMapper.findLatest(
                    taskId, "WRITER", generationContextHash
            );
            Optional<WorkflowCheckpointRecord> reviewerRecord = checkpointMapper.findLatest(
                    taskId, "REVIEWER", generationContextHash
            );
            Optional<WriterCheckpointState> restoredWriter = writerRecord.flatMap(checkpointCodec::writer);
            Optional<ReviewerCheckpointState> restoredReviewer = reviewerRecord.flatMap(checkpointCodec::reviewer);
            if (writerRecord.isPresent() && restoredWriter.isEmpty()) {
                meterRegistry.counter("finsight.stock.workflow.checkpoint", "result", "invalid_writer").increment();
            }
            if (reviewerRecord.isPresent() && restoredReviewer.isEmpty()) {
                meterRegistry.counter("finsight.stock.workflow.checkpoint", "result", "invalid_reviewer").increment();
            }

            if (restoredWriter.isPresent()) {
                WriterCheckpointState writerState = restoredWriter.orElseThrow();
                report = writerState.report();
                writerAttempts = writerState.attempt();
                nextWriterAttempt = writerAttempts + 1;
                String generationMode = InvestmentReportWriter.generationMode(report);
                String fallbackReason = InvestmentReportWriter.fallbackReason(report);
                publish(taskId, threadId, progress, "writer", mapOf(
                        "attempt", writerAttempts,
                        "finalReport", report,
                        "generationMode", generationMode,
                        "fallbackReason", fallbackReason,
                        "resumed", true,
                        "generationContextHash", generationContextHash
                ), 0L, writerAttempts,
                        "template-fallback".equals(generationMode) ? "DEGRADED" : "SUCCESS",
                        fallbackReason);
                meterRegistry.counter("finsight.stock.workflow.checkpoint", "result", "writer_restored").increment();

                if (restoredReviewer.isPresent()
                        && restoredReviewer.orElseThrow().attempt() == writerAttempts) {
                    ReviewerCheckpointState reviewerState = restoredReviewer.orElseThrow();
                    review = reviewerState.review();
                    compliance = reviewerState.compliance();
                    publish(taskId, threadId, progress, "reviewer", mapOf(
                            "attempt", writerAttempts,
                            "reviewStatus", review.status(),
                            "critique", review.reason(),
                            "compliance", compliance,
                            "resumed", true,
                            "generationContextHash", generationContextHash
                    ), 0L, writerAttempts);
                    meterRegistry.counter("finsight.stock.workflow.checkpoint", "result", "reviewer_restored").increment();
                } else {
                    long reviewerStartedAt = System.nanoTime();
                    review = workflow.review(report, snapshot, metrics);
                    compliance = workflow.compliance(report, review);
                    publish(taskId, threadId, progress, "reviewer", mapOf(
                            "attempt", writerAttempts,
                            "reviewStatus", review.status(),
                            "critique", review.reason(),
                            "compliance", compliance,
                            "generationContextHash", generationContextHash
                    ), elapsedMs(reviewerStartedAt), writerAttempts);
                }
            }

            if (!isPass(review.status()) || !isPass(compliance.status())) {
                for (int attempt = nextWriterAttempt; attempt <= MAX_WRITER_ATTEMPTS; attempt++) {
                    writerAttempts = attempt;
                    long writerStartedAt = System.nanoTime();
                    report = workflow.write(snapshot, metrics, riskAssessment, attempt == 1 ? null : review);
                    String generationMode = InvestmentReportWriter.generationMode(report);
                    String fallbackReason = InvestmentReportWriter.fallbackReason(report);
                    publish(taskId, threadId, progress, "writer", mapOf(
                            "attempt", attempt,
                            "finalReport", report,
                            "generationMode", generationMode,
                            "fallbackReason", fallbackReason,
                            "generationContextHash", generationContextHash
                    ), elapsedMs(writerStartedAt), attempt,
                            "template-fallback".equals(generationMode) ? "DEGRADED" : "SUCCESS",
                            fallbackReason);

                    long reviewerStartedAt = System.nanoTime();
                    review = workflow.review(report, snapshot, metrics);
                    compliance = workflow.compliance(report, review);
                    publish(taskId, threadId, progress, "reviewer", mapOf(
                            "attempt", attempt,
                            "reviewStatus", review.status(),
                            "critique", review.reason(),
                            "compliance", compliance,
                            "generationContextHash", generationContextHash
                    ), elapsedMs(reviewerStartedAt), attempt);
                    if (isPass(review.status()) && isPass(compliance.status())) {
                        break;
                    }
                }
            }

            long evaluationStartedAt = System.nanoTime();
            FinancialEvaluationResult evaluation = workflow.evaluation(report, snapshot, metrics);
            publish(taskId, threadId, progress, "evaluation", mapOf(
                    "evaluation", evaluation,
                    "rewriteCount", Math.max(0, writerAttempts - 1),
                    "generationContextHash", generationContextHash
            ), elapsedMs(evaluationStartedAt), writerAttempts);

            boolean reviewPassed = isPass(review.status());
            boolean compliancePassed = isPass(compliance.status());
            boolean evaluationPassed = isPass(evaluation.status());
            String finalStatus = reviewPassed && compliancePassed && evaluationPassed ? "PASS" : "FAIL";
            String critique = isPass(finalStatus)
                    ? ""
                    : (review.reason() + " " + compliance.issues() + " "
                    + evaluation.failedReasons()).trim();
            long savedReportId = reportService.saveLatest(
                    ownerId, threadId, taskId, report, finalStatus, critique,
                    snapshotId, dataSnapshotHash, generationContextHash, null
            );
            return new ReportReuseCoordinator.GeneratedReport(
                    new ReusableReportRecord(savedReportId, report, finalStatus),
                    Math.max(0, writerAttempts - 1)
            );
                    },
                    () -> publish(taskId, threadId, progress, "cache_wait", mapOf(
                            "generationContextHash", generationContextHash,
                            "reason", "IDENTICAL_GENERATION_IN_PROGRESS"
                    ), 0L, 1)
            );

            if (reuseOutcome.reused()) {
                ReusableReportRecord reused = reuseOutcome.report();
                boolean coalesced = reuseOutcome.origin() == ReportReuseCoordinator.ReuseOrigin.COALESCED;
                reportService.saveLatest(
                        ownerId, threadId, taskId, reused.content(), "PASS", "",
                        snapshotId, dataSnapshotHash, generationContextHash, reused.id()
                );
                publish(taskId, threadId, progress, "cache_hit", mapOf(
                        "cacheHit", true,
                        "coalesced", coalesced,
                        "reusedFromReportId", reused.id(),
                        "dataSnapshotHash", dataSnapshotHash,
                        "generationContextHash", generationContextHash
                ), 0L, 1);
            }
            completeTask(
                    taskId,
                    threadId,
                    progress,
                    reuseOutcome.report().reviewStatus(),
                    reuseOutcome.rewriteCount(),
                    reuseOutcome.reused()
            );
        } catch (Exception e) {
            taskMapper.markFailed(taskId, e.getMessage());
            runtimeStateService.markStatus(taskId, "FAILED");
            stepLogMapper.saveError(taskId, "stock_report_workflow", e);
            meterRegistry.counter("finsight.stock.workflow.task", "result", "failed").increment();
            notifyError(progress, e);
        }
    }

    private boolean validateReusableCandidate(
            ReusableReportRecord candidate,
            ReportReuseCoordinator.ReuseOrigin origin,
            long taskId,
            String threadId,
            StockReportProgressListener progress,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            String generationContextHash
    ) {
        FinancialEvaluationResult evaluation = workflow.evaluation(candidate.content(), snapshot, metrics);
        publish(taskId, threadId, progress, "evaluation", mapOf(
                "evaluation", evaluation,
                "rewriteCount", 0,
                "cacheCandidate", true,
                "coalesced", origin == ReportReuseCoordinator.ReuseOrigin.COALESCED,
                "generationContextHash", generationContextHash
        ), 0L, 1);
        return isPass(evaluation.status());
    }

    private void completeTask(
            long taskId,
            String threadId,
            StockReportProgressListener progress,
            String finalStatus,
            int rewriteCount,
            boolean cacheHit
    ) {
        publish(taskId, threadId, progress, "done", mapOf(
                "taskId", taskId,
                "threadId", threadId,
                "reviewStatus", finalStatus,
                "rewriteCount", rewriteCount,
                "cacheHit", cacheHit
        ), 0L, Math.max(1, rewriteCount + 1));
        taskMapper.markCompleted(taskId);
        runtimeStateService.markStatus(taskId, "COMPLETED");
        meterRegistry.counter("finsight.stock.workflow.task", "result", "completed").increment();
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
        publish(taskId, threadId, listener, step, data, durationMs, attemptNo, "SUCCESS", null);
    }

    /** 持久化步骤状态后推送进度，降级完成不会被误记为普通成功。 */
    private void publish(
            long taskId,
            String threadId,
            StockReportProgressListener listener,
            String step,
            Object data,
            long durationMs,
            int attemptNo,
            String status,
            String errorMessage
    ) {
        Timer.builder("finsight.stock.workflow.stage.duration")
                .tag("stage", step)
                .register(meterRegistry)
                .record(Math.max(0L, durationMs), TimeUnit.MILLISECONDS);
        String stage = step.toUpperCase(java.util.Locale.ROOT);
        stagePersistence.persist(new WorkflowStagePersistence.StageCommit(
                taskId,
                threadId,
                step,
                stage,
                data,
                durationMs,
                attemptNo,
                status,
                errorMessage,
                generationContextHash(data),
                leaseOwner,
                leaseUntil()
        ));
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

    private String generationContextHash(Object data) {
        if (!(data instanceof Map<?, ?> values)) {
            return null;
        }
        Object value = values.get("generationContextHash");
        return value instanceof String hash && !hash.isBlank() ? hash : null;
    }

    private Map<String, Object> mapOf(Object... values) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2) {
            map.put(values[i].toString(), values[i + 1]);
        }
        return map;
    }
}
