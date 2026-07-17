package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.ReusableReportRecord;
import com.zzy.finsight.service.ReportService;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ReportReuseCoordinatorTest {

    @Test
    void returnsHistoricalReportOnlyAfterCurrentValidationPasses() {
        ReportService reportService = mock(ReportService.class);
        ReusableReportRecord historical = new ReusableReportRecord(9L, "历史报告", "PASS");
        when(reportService.findReusable(7L, "context-hash")).thenReturn(Optional.of(historical));
        ReportReuseCoordinator coordinator = coordinator(reportService, Duration.ofSeconds(1));
        AtomicInteger generations = new AtomicInteger();

        ReportReuseCoordinator.ReuseOutcome outcome = coordinator.coordinate(
                7L,
                "context-hash",
                (candidate, origin) -> true,
                () -> {
                    generations.incrementAndGet();
                    return generated(10L);
                },
                () -> {
                }
        );

        assertThat(outcome.origin()).isEqualTo(ReportReuseCoordinator.ReuseOrigin.HISTORICAL);
        assertThat(outcome.report()).isEqualTo(historical);
        assertThat(generations).hasValue(0);
    }

    @Test
    void coalescesConcurrentGenerationBehindOneLeader() throws Exception {
        ReportService reportService = mock(ReportService.class);
        when(reportService.findReusable(7L, "context-hash")).thenReturn(Optional.empty());
        ReportReuseCoordinator coordinator = coordinator(reportService, Duration.ofSeconds(2));
        AtomicInteger generations = new AtomicInteger();
        int requestCount = 6;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ReportReuseCoordinator.ReuseOutcome>> futures = new ArrayList<>();
        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await(1, TimeUnit.SECONDS);
                    return coordinator.coordinate(
                            7L,
                            "context-hash",
                            (candidate, origin) -> true,
                            () -> {
                                generations.incrementAndGet();
                                try {
                                    Thread.sleep(100);
                                } catch (InterruptedException e) {
                                    Thread.currentThread().interrupt();
                                }
                                return generated(99L);
                            },
                            () -> {
                            }
                    );
                }));
            }
            start.countDown();
            List<ReportReuseCoordinator.ReuseOutcome> outcomes = futures.stream()
                    .map(this::get)
                    .toList();

            assertThat(generations).hasValue(1);
            assertThat(outcomes).filteredOn(outcome -> !outcome.reused()).hasSize(1);
            assertThat(outcomes).filteredOn(ReportReuseCoordinator.ReuseOutcome::reused).hasSize(5);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void timesOutFollowerInsteadOfWaitingForever() throws Exception {
        ReportService reportService = mock(ReportService.class);
        when(reportService.findReusable(7L, "context-hash")).thenReturn(Optional.empty());
        ReportReuseCoordinator coordinator = coordinator(reportService, Duration.ofMillis(50));
        ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch leaderStarted = new CountDownLatch(1);
        CountDownLatch releaseLeader = new CountDownLatch(1);
        try {
            Future<ReportReuseCoordinator.ReuseOutcome> leader = executor.submit(() -> coordinator.coordinate(
                    7L,
                    "context-hash",
                    (candidate, origin) -> true,
                    () -> {
                        leaderStarted.countDown();
                        try {
                            releaseLeader.await(2, TimeUnit.SECONDS);
                        } catch (InterruptedException e) {
                            Thread.currentThread().interrupt();
                        }
                        return generated(99L);
                    },
                    () -> {
                    }
            ));
            assertThat(leaderStarted.await(1, TimeUnit.SECONDS)).isTrue();

            assertThatThrownBy(() -> coordinator.coordinate(
                    7L,
                    "context-hash",
                    (candidate, origin) -> true,
                    () -> generated(100L),
                    () -> {
                    }
            )).isInstanceOf(IllegalStateException.class).hasMessageContaining("等待相同报告生成超时");

            releaseLeader.countDown();
            assertThat(leader.get(1, TimeUnit.SECONDS).origin())
                    .isEqualTo(ReportReuseCoordinator.ReuseOrigin.GENERATED);
        } finally {
            releaseLeader.countDown();
            executor.shutdownNow();
        }
    }

    private ReportReuseCoordinator coordinator(ReportService reportService, Duration timeout) {
        return new ReportReuseCoordinator(
                reportService,
                new ReportGenerationSingleFlight(),
                new SimpleMeterRegistry(),
                timeout
        );
    }

    private ReportReuseCoordinator.GeneratedReport generated(long id) {
        return new ReportReuseCoordinator.GeneratedReport(
                new ReusableReportRecord(id, "报告正文", "PASS"), 0
        );
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get(3, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
