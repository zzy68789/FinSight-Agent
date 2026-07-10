package com.zzy.drai.financial;

import com.zzy.drai.repository.AgentStepLogRepository;
import com.zzy.drai.repository.CheckpointRepository;
import com.zzy.drai.repository.ResearchTaskRepository;
import com.zzy.drai.service.ReportService;
import com.zzy.drai.service.TaskRuntimeStateService;
import com.zzy.drai.domain.ReusableReportRecord;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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

class StockReportServiceTest {
    private StockReportWorkflow workflow;
    private FinancialSnapshotRepository snapshotRepository;
    private ResearchTaskRepository taskRepository;
    private AgentStepLogRepository stepLogRepository;
    private CheckpointRepository checkpointRepository;
    private TaskRuntimeStateService runtimeStateService;
    private ReportService reportService;
    private FinancialReportFingerprintService fingerprintService;
    private StockReportRunner runner;
    private StockReportRequest request;
    private FinancialSnapshot snapshot;
    private List<FinancialMetricResult> metrics;

    @BeforeEach
    void setUp() {
        workflow = mock(StockReportWorkflow.class);
        snapshotRepository = mock(FinancialSnapshotRepository.class);
        taskRepository = mock(ResearchTaskRepository.class);
        stepLogRepository = mock(AgentStepLogRepository.class);
        checkpointRepository = mock(CheckpointRepository.class);
        runtimeStateService = mock(TaskRuntimeStateService.class);
        reportService = mock(ReportService.class);
        fingerprintService = mock(FinancialReportFingerprintService.class);
        runner = new StockReportRunner(
                workflow,
                snapshotRepository,
                taskRepository,
                stepLogRepository,
                checkpointRepository,
                runtimeStateService,
                reportService,
                fingerprintService,
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

        when(taskRepository.create(anyLong(), anyString(), anyString(), anyString(), anyString())).thenReturn(11L);
        when(taskRepository.startAttempt(eq(11L), anyString(), any(LocalDateTime.class))).thenReturn(true);
        when(workflow.resolve(request)).thenReturn(snapshot.subject());
        when(workflow.snapshot(snapshot.subject(), request)).thenReturn(snapshot);
        when(snapshotRepository.saveSnapshot(eq(7L), eq(11L), eq("stock-thread"), eq(snapshot), eq("COLLECTED"))).thenReturn(21L);
        when(fingerprintService.dataSnapshotHash(snapshot)).thenReturn("data-hash");
        when(fingerprintService.generationContextHash("data-hash")).thenReturn("context-hash");
        when(snapshotRepository.saveSnapshot(
                eq(7L), eq(11L), eq("stock-thread"), eq(snapshot), eq("COLLECTED"), eq("data-hash")
        )).thenReturn(21L);
        when(reportService.findReusable(7L, "context-hash")).thenReturn(Optional.empty());
        when(reportService.findByTask(7L, 11L)).thenReturn(Optional.empty());
        when(snapshotRepository.findSnapshot(7L, 11L)).thenReturn(Optional.empty());
        when(snapshotRepository.findMetrics(11L)).thenReturn(List.of());
        when(workflow.metrics(snapshot)).thenReturn(metrics);
        when(workflow.riskAssessment(metrics, snapshot)).thenReturn(new FinancialRiskAssessment(
                new BigDecimal("5.00"), "MEDIUM", List.of(), List.of()
        ));
        when(workflow.write(eq(snapshot), eq(metrics), any(FinancialRiskAssessment.class), any())).thenReturn("报告正文");
        when(workflow.compliance(anyString(), any(CitationReviewResult.class))).thenReturn(
                new FinancialComplianceReviewResult("PASS", new BigDecimal("100.00"), List.of())
        );
        when(workflow.evaluation(anyString(), eq(snapshot), eq(metrics))).thenReturn(Optional.empty());
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
        verify(taskRepository).markCompleted(11L);
        verify(taskRepository, never()).markFailed(11L);
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
        verify(taskRepository).markCompleted(11L);
    }

    @Test
    void marksTaskFailedWhenWorkflowComponentThrows() {
        doThrow(new IllegalStateException("数据源异常")).when(workflow).snapshot(snapshot.subject(), request);

        runner.runNew(7L, request, StockReportProgressListener.noop());

        verify(taskRepository).markFailed(11L, "数据源异常");
        verify(stepLogRepository).saveError(eq(11L), eq("stock_report_workflow"), any(IllegalStateException.class));
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

        verify(taskRepository).markCompleted(11L);
        verify(taskRepository, never()).markFailed(11L);
    }

    @Test
    void reusesPassingReportWhenGenerationContextHashMatches() {
        when(reportService.findReusable(7L, "context-hash"))
                .thenReturn(Optional.of(new ReusableReportRecord(99L, "缓存报告", "PASS")));

        runner.runNew(7L, request, StockReportProgressListener.noop());

        verify(workflow, never()).write(any(), any(), any(), any());
        verify(reportService).saveLatest(
                7L, "stock-thread", 11L, "缓存报告", "PASS", "",
                21L, "data-hash", "context-hash", 99L
        );
        verify(taskRepository).markCompleted(11L);
    }
}
