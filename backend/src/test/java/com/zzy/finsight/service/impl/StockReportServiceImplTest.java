package com.zzy.finsight.service.impl;

import com.zzy.finsight.component.workflow.FinancialReportFingerprinter;
import com.zzy.finsight.component.workflow.ReportGenerationSingleFlight;
import com.zzy.finsight.component.workflow.StockReportProgressListener;
import com.zzy.finsight.component.workflow.StockReportRunner;
import com.zzy.finsight.component.workflow.StockReportWorkflow;
import com.zzy.finsight.component.workflow.WorkflowCheckpointCodec;
import com.zzy.finsight.domain.WorkflowCheckpointRecord;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
import com.zzy.finsight.domain.stock.FinancialEvaluationResult;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.infrastructure.serialization.StockReportRequestCodec;
import com.zzy.finsight.mapper.FinancialSnapshotMapper;


import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.ReportService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import com.zzy.finsight.domain.ReusableReportRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockReportServiceImplTest {
    private StockReportWorkflow workflow;
    private FinancialSnapshotMapper snapshotMapper;
    private ResearchTaskMapper taskMapper;
    private AgentStepLogMapper stepLogMapper;
    private CheckpointMapper checkpointMapper;
    private TaskRuntimeStateService runtimeStateService;
    private ReportService reportService;
    private FinancialReportFingerprinter fingerprinter;
    private StockReportRunner runner;
    private StockReportRequest request;
    private FinancialSnapshot snapshot;
    private List<FinancialMetricResult> metrics;

    @BeforeEach
    void setUp() {
        workflow = mock(StockReportWorkflow.class);
        snapshotMapper = mock(FinancialSnapshotMapper.class);
        taskMapper = mock(ResearchTaskMapper.class);
        stepLogMapper = mock(AgentStepLogMapper.class);
        checkpointMapper = mock(CheckpointMapper.class);
        runtimeStateService = mock(TaskRuntimeStateService.class);
        reportService = mock(ReportService.class);
        fingerprinter = mock(FinancialReportFingerprinter.class);
        runner = new StockReportRunner(
                workflow,
                snapshotMapper,
                taskMapper,
                stepLogMapper,
                checkpointMapper,
                runtimeStateService,
                reportService,
                fingerprinter,
                new ReportGenerationSingleFlight(),
                new WorkflowCheckpointCodec(new ObjectMapper()),
                new StockReportRequestCodec(new ObjectMapper()),
                new SimpleMeterRegistry()
        );

        request = new StockReportRequest();
        request.setTicker("600519");
        request.setThreadId("stock-thread");
        snapshot = new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "latest",
                "hybrid",
                List.of(),
                LocalDateTime.of(2026, 7, 10, 10, 0)
        );
        metrics = List.of(new FinancialMetricResult(
                "ROE", new BigDecimal("30.00"), "30.00%", "净利润 / 平均净资产", "OK", "", List.of("ROE")
        ));

        when(taskMapper.create(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(11L);
        when(taskMapper.startAttempt(eq(11L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(workflow.resolve(request)).thenReturn(snapshot.subject());
        when(workflow.snapshot(7L, snapshot.subject(), request)).thenReturn(snapshot);
        when(snapshotMapper.saveSnapshot(eq(7L), eq(11L), eq("stock-thread"), eq(snapshot), eq("COLLECTED"))).thenReturn(21L);
        when(fingerprinter.dataSnapshotHash(snapshot)).thenReturn("data-hash");
        when(fingerprinter.generationContextHash("data-hash")).thenReturn("context-hash");
        when(snapshotMapper.saveSnapshot(
                eq(7L), eq(11L), eq("stock-thread"), eq(snapshot), eq("COLLECTED"), eq("data-hash")
        )).thenReturn(21L);
        when(reportService.findReusable(7L, "context-hash")).thenReturn(Optional.empty());
        when(reportService.findByTask(7L, 11L)).thenReturn(Optional.empty());
        when(checkpointMapper.findLatest(anyLong(), anyString(), anyString())).thenReturn(Optional.empty());
        when(snapshotMapper.findSnapshot(7L, 11L)).thenReturn(Optional.empty());
        when(snapshotMapper.findMetrics(11L)).thenReturn(List.of());
        when(workflow.metrics(snapshot)).thenReturn(metrics);
        when(workflow.riskAssessment(metrics, snapshot)).thenReturn(new FinancialRiskAssessment(
                new BigDecimal("5.00"), "MEDIUM", List.of(), List.of()
        ));
        when(workflow.write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any())).thenReturn("报告正文");
        when(workflow.compliance(anyString(), any(CitationReviewResult.class))).thenReturn(
                new FinancialComplianceReviewResult("PASS", new BigDecimal("100.00"), List.of())
        );
        when(workflow.evaluation(anyString(), eq(snapshot), eq(metrics))).thenReturn(
                new FinancialEvaluationResult("600519", "贵州茅台", BigDecimal.ONE, "PASS", List.of(), List.of())
        );
    }

    @Test
    void rewritesAfterCitationFailureAndCompletesWhenSecondAttemptPasses() {
        when(workflow.review(anyString(), eq(snapshot), eq(metrics)))
                .thenReturn(CitationReviewResult.fail("缺少引用"))
                .thenReturn(CitationReviewResult.pass());

        runner.runNew(7L, request, StockReportProgressListener.noop());

        verify(workflow, times(2)).write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any());
        verify(reportService).saveLatest(
                7L, "stock-thread", 11L, "报告正文", "PASS", "",
                21L, "data-hash", "context-hash", null
        );
        verify(taskMapper).markCompleted(11L);
        verify(taskMapper, never()).markFailed(11L);
    }

    @Test
    void persistsFailedReviewAfterThreeWriterAttemptsWithoutFailingTaskExecution() {
        when(workflow.review(anyString(), eq(snapshot), eq(metrics)))
                .thenReturn(CitationReviewResult.fail("证据不足"));

        runner.runNew(7L, request, StockReportProgressListener.noop());

        verify(workflow, times(3)).write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any());
        verify(reportService).saveLatest(
                eq(7L), eq("stock-thread"), eq(11L), eq("报告正文"), eq("FAIL"), anyString(),
                eq(21L), eq("data-hash"), eq("context-hash"), eq(null)
        );
        verify(taskMapper).markCompleted(11L);
    }

    @Test
    void marksTaskFailedWhenWorkflowComponentThrows() {
        doThrow(new IllegalStateException("数据源异常")).when(workflow).snapshot(7L, snapshot.subject(), request);

        runner.runNew(7L, request, StockReportProgressListener.noop());

        verify(taskMapper).markFailed(11L, "数据源异常");
        verify(stepLogMapper).saveError(eq(11L), eq("stock_report_workflow"), any(IllegalStateException.class));
        verify(reportService, never()).saveLatest(
                anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), any(), any(), any()
        );
    }

    @Test
    void listenerDisconnectDoesNotFailBackgroundWorkflow() {
        when(workflow.review(anyString(), eq(snapshot), eq(metrics))).thenReturn(CitationReviewResult.pass());
        StockReportProgressListener disconnected = new StockReportProgressListener() {
            @Override
            public void onStep(String step, Object data) {
                throw new IllegalStateException("client disconnected");
            }

            @Override
            public void onDone() {
                throw new IllegalStateException("client disconnected");
            }

            @Override
            public void onError(Throwable throwable) {
                throw new IllegalStateException("client disconnected");
            }
        };

        runner.runNew(7L, request, disconnected);

        verify(taskMapper).markCompleted(11L);
        verify(taskMapper, never()).markFailed(11L);
    }

    @Test
    void persistsWriterFallbackAsDegradedStep() {
        when(workflow.write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any())).thenReturn("""
                <!-- FinSight generation-mode: template-fallback -->
                <!-- FinSight fallback-reason: LLM_TIMEOUT -->
                报告正文
                """);
        when(workflow.review(anyString(), eq(snapshot), eq(metrics))).thenReturn(CitationReviewResult.pass());
        AtomicReference<Object> writerEvent = new AtomicReference<>();
        StockReportProgressListener listener = new StockReportProgressListener() {
            @Override
            public void onStep(String step, Object data) {
                if ("writer".equals(step)) {
                    writerEvent.set(data);
                }
            }

            @Override
            public void onDone() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };

        runner.runNew(7L, request, listener);

        verify(stepLogMapper).save(
                eq(11L), eq("writer"), any(), eq(1), anyLong(), eq("DEGRADED"), eq("LLM_TIMEOUT")
        );
        assertThat(writerEvent.get()).isInstanceOf(Map.class);
        Map<?, ?> writerPayload = (Map<?, ?>) writerEvent.get();
        assertThat(writerPayload.get("generationMode")).isEqualTo("template-fallback");
        assertThat(writerPayload.get("fallbackReason")).isEqualTo("LLM_TIMEOUT");
    }

    @Test
    void reusesPassingReportWhenGenerationContextHashMatches() {
        when(reportService.findReusable(7L, "context-hash"))
                .thenReturn(Optional.of(new ReusableReportRecord(99L, "缓存报告", "PASS")));

        runner.runNew(7L, request, StockReportProgressListener.noop());

        verify(workflow, never()).write(any(), any(), any(), any());
        verify(workflow).evaluation("缓存报告", snapshot, metrics);
        verify(reportService).saveLatest(
                7L, "stock-thread", 11L, "缓存报告", "PASS", "",
                21L, "data-hash", "context-hash", 99L
        );
        verify(taskMapper).markCompleted(11L);
    }

    @Test
    void coalescesConcurrentGenerationForSameOwnerAndContext() throws Exception {
        when(taskMapper.create(anyLong(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(11L, 12L);
        when(taskMapper.startAttempt(eq(12L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(snapshotMapper.saveSnapshot(
                eq(7L), anyLong(), eq("stock-thread"), eq(snapshot), eq("COLLECTED"), eq("data-hash")
        )).thenReturn(21L, 22L);
        when(snapshotMapper.findSnapshot(7L, 12L)).thenReturn(Optional.empty());
        when(snapshotMapper.findMetrics(12L)).thenReturn(List.of());
        when(reportService.findByTask(7L, 12L)).thenReturn(Optional.empty());
        when(workflow.review(anyString(), eq(snapshot), eq(metrics))).thenReturn(CitationReviewResult.pass());
        when(reportService.saveLatest(
                anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString(), any()
        )).thenAnswer(invocation -> invocation.<Long>getArgument(9) == null ? 99L : 100L);

        CountDownLatch followerWaiting = new CountDownLatch(1);
        when(workflow.write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any()))
                .thenAnswer(invocation -> {
                    if (!followerWaiting.await(5, TimeUnit.SECONDS)) {
                        throw new IllegalStateException("并发请求未进入等待状态");
                    }
                    return "报告正文";
                });
        StockReportProgressListener listener = new StockReportProgressListener() {
            @Override
            public void onStep(String step, Object data) {
                if ("cache_wait".equals(step)) {
                    followerWaiting.countDown();
                }
            }

            @Override
            public void onDone() {
            }

            @Override
            public void onError(Throwable throwable) {
            }
        };
        ExecutorService executor = Executors.newFixedThreadPool(2);

        try {
            Future<Long> first = executor.submit(() -> runner.runNew(7L, request, listener));
            Future<Long> second = executor.submit(() -> runner.runNew(7L, request, listener));

            assertThat(first.get(10, TimeUnit.SECONDS)).isIn(11L, 12L);
            assertThat(second.get(10, TimeUnit.SECONDS)).isIn(11L, 12L);
        } finally {
            executor.shutdownNow();
        }

        verify(workflow, times(1)).write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any());
        verify(reportService, times(2)).saveLatest(
                anyLong(), anyString(), anyLong(), anyString(), anyString(), anyString(),
                any(), anyString(), anyString(), any()
        );
        verify(reportService).saveLatest(
                eq(7L), eq("stock-thread"), anyLong(), eq("报告正文"), eq("PASS"), eq(""),
                any(), eq("data-hash"), eq("context-hash"), eq(99L)
        );
        verify(taskMapper).markCompleted(11L);
        verify(taskMapper).markCompleted(12L);
    }

    @Test
    void resumesWriterCheckpointWithoutCallingLlmAgain() {
        when(checkpointMapper.findLatest(11L, "WRITER", "context-hash")).thenReturn(Optional.of(
                checkpoint(31L, "WRITER", 1, """
                        {"attempt":1,"finalReport":"检查点报告"}
                        """)
        ));
        when(workflow.review("检查点报告", snapshot, metrics)).thenReturn(CitationReviewResult.pass());

        runner.runExisting(7L, 11L, "stock-thread", request, StockReportProgressListener.noop());

        verify(workflow, never()).write(any(), any(), any(), any());
        verify(workflow).review("检查点报告", snapshot, metrics);
        verify(workflow).evaluation("检查点报告", snapshot, metrics);
        verify(reportService).saveLatest(
                7L, "stock-thread", 11L, "检查点报告", "PASS", "",
                21L, "data-hash", "context-hash", null
        );
        verify(taskMapper).markCompleted(11L);
    }

    @Test
    void resumesPassingReviewerCheckpointAtEvaluation() {
        when(checkpointMapper.findLatest(11L, "WRITER", "context-hash")).thenReturn(Optional.of(
                checkpoint(31L, "WRITER", 1, """
                        {"attempt":1,"finalReport":"检查点报告"}
                        """)
        ));
        when(checkpointMapper.findLatest(11L, "REVIEWER", "context-hash")).thenReturn(Optional.of(
                checkpoint(32L, "REVIEWER", 1, """
                        {
                          "attempt":1,
                          "reviewStatus":"PASS",
                          "critique":"",
                          "compliance":{"status":"PASS","score":100.00,"issues":[]}
                        }
                        """)
        ));

        runner.runExisting(7L, 11L, "stock-thread", request, StockReportProgressListener.noop());

        verify(workflow, never()).write(any(), any(), any(), any());
        verify(workflow, never()).review(anyString(), any(), any());
        verify(workflow, never()).compliance(anyString(), any());
        verify(workflow).evaluation("检查点报告", snapshot, metrics);
        verify(taskMapper).markCompleted(11L);
    }

    @Test
    void regeneratesWhenWriterCheckpointIsCorrupted() {
        when(checkpointMapper.findLatest(11L, "WRITER", "context-hash")).thenReturn(Optional.of(
                checkpoint(31L, "WRITER", 1, "{invalid-json")
        ));
        when(workflow.review(anyString(), eq(snapshot), eq(metrics))).thenReturn(CitationReviewResult.pass());

        runner.runExisting(7L, 11L, "stock-thread", request, StockReportProgressListener.noop());

        verify(workflow).write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any());
        verify(taskMapper).markCompleted(11L);
    }

    private WorkflowCheckpointRecord checkpoint(long id, String stage, int attemptNo, String stateJson) {
        return new WorkflowCheckpointRecord(
                id,
                "stock-thread",
                11L,
                stage,
                attemptNo,
                "context-hash",
                stateJson,
                LocalDateTime.of(2026, 7, 17, 10, 0)
        );
    }
}
