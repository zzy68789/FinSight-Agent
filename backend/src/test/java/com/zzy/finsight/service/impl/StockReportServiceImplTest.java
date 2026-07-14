package com.zzy.finsight.service.impl;

import com.zzy.finsight.component.workflow.FinancialReportFingerprinter;
import com.zzy.finsight.component.workflow.StockReportProgressListener;
import com.zzy.finsight.component.workflow.StockReportRunner;
import com.zzy.finsight.component.workflow.StockReportWorkflow;
import com.zzy.finsight.domain.stock.CitationReviewResult;
import com.zzy.finsight.domain.stock.FinancialComplianceReviewResult;
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
        when(workflow.snapshot(snapshot.subject(), request)).thenReturn(snapshot);
        when(snapshotMapper.saveSnapshot(eq(7L), eq(11L), eq("stock-thread"), eq(snapshot), eq("COLLECTED"))).thenReturn(21L);
        when(fingerprinter.dataSnapshotHash(snapshot)).thenReturn("data-hash");
        when(fingerprinter.generationContextHash("data-hash")).thenReturn("context-hash");
        when(snapshotMapper.saveSnapshot(
                eq(7L), eq(11L), eq("stock-thread"), eq(snapshot), eq("COLLECTED"), eq("data-hash")
        )).thenReturn(21L);
        when(reportService.findReusable(7L, "context-hash")).thenReturn(Optional.empty());
        when(reportService.findByTask(7L, 11L)).thenReturn(Optional.empty());
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
        doThrow(new IllegalStateException("数据源异常")).when(workflow).snapshot(snapshot.subject(), request);

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
        verify(reportService).saveLatest(
                7L, "stock-thread", 11L, "缓存报告", "PASS", "",
                21L, "data-hash", "context-hash", 99L
        );
        verify(taskMapper).markCompleted(11L);
    }
}
