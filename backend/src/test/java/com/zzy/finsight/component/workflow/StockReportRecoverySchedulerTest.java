package com.zzy.finsight.component.workflow;

import com.zzy.finsight.dto.stock.StockReportRequest;
import com.zzy.finsight.infrastructure.serialization.StockReportRequestCodec;


import com.zzy.finsight.domain.WorkflowTaskExecutionRecord;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ExecutorService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StockReportRecoverySchedulerTest {

    @Test
    void resubmitsStaleTaskWhenAttemptsRemain() {
        ResearchTaskMapper mapper = mock(ResearchTaskMapper.class);
        StockReportRunner runner = mock(StockReportRunner.class);
        StockReportRequestCodec codec = mock(StockReportRequestCodec.class);
        ExecutorService executor = mock(ExecutorService.class);
        WorkflowTaskExecutionRecord task = task(1);
        StockReportRequest request = new StockReportRequest();
        request.setTicker("600519");
        when(mapper.findStaleRunning(any(LocalDateTime.class), eq(20))).thenReturn(List.of(task));
        when(mapper.markStaleRetrying(eq(11L), any(LocalDateTime.class))).thenReturn(true);
        when(codec.fromJson("{}")) .thenReturn(request);

        StockReportRecoveryScheduler scheduler = new StockReportRecoveryScheduler(
                mapper, runner, codec, executor, Duration.ofMinutes(5), new SimpleMeterRegistry()
        );
        scheduler.recoverStaleTasks();

        verify(executor).submit(any(Runnable.class));
        verify(mapper, never()).markFailed(eq(11L), any());
    }

    @Test
    void stopsRecoveryAfterThreeAttempts() {
        ResearchTaskMapper mapper = mock(ResearchTaskMapper.class);
        StockReportRunner runner = mock(StockReportRunner.class);
        StockReportRequestCodec codec = mock(StockReportRequestCodec.class);
        ExecutorService executor = mock(ExecutorService.class);
        when(mapper.findStaleRunning(any(LocalDateTime.class), eq(20))).thenReturn(List.of(task(3)));

        StockReportRecoveryScheduler scheduler = new StockReportRecoveryScheduler(
                mapper, runner, codec, executor, Duration.ofMinutes(5), new SimpleMeterRegistry()
        );
        scheduler.recoverStaleTasks();

        verify(mapper).markFailed(11L, "工作流心跳超时且已达到最大尝试次数");
        verify(executor, never()).submit(any(Runnable.class));
    }

    private WorkflowTaskExecutionRecord task(int attempts) {
        LocalDateTime now = LocalDateTime.now();
        return new WorkflowTaskExecutionRecord(
                11L, 7L, "stock-thread", "RUNNING", "WRITER", attempts, "{}", null,
                now.minusMinutes(10), "old-runner", now.minusMinutes(5), now.minusMinutes(10)
        );
    }
}
