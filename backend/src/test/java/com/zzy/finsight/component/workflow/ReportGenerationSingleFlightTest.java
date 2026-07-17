package com.zzy.finsight.component.workflow;

import com.zzy.finsight.domain.ReusableReportRecord;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

class ReportGenerationSingleFlightTest {

    @Test
    void coalescesConcurrentRequestsForSameOwnerAndContext() throws Exception {
        ReportGenerationSingleFlight singleFlight = new ReportGenerationSingleFlight();
        int requestCount = 8;
        ExecutorService executor = Executors.newFixedThreadPool(requestCount);
        CountDownLatch start = new CountDownLatch(1);
        CountDownLatch acquired = new CountDownLatch(requestCount);
        List<Future<ReportGenerationSingleFlight.Flight>> futures = new ArrayList<>();

        try {
            for (int i = 0; i < requestCount; i++) {
                futures.add(executor.submit(() -> {
                    start.await(5, TimeUnit.SECONDS);
                    ReportGenerationSingleFlight.Flight flight = singleFlight.acquire(7L, "context-hash");
                    acquired.countDown();
                    return flight;
                }));
            }
            start.countDown();
            assertThat(acquired.await(5, TimeUnit.SECONDS)).isTrue();

            List<ReportGenerationSingleFlight.Flight> flights = futures.stream()
                    .map(this::get)
                    .toList();
            ReportGenerationSingleFlight.Flight leader = flights.stream()
                    .filter(ReportGenerationSingleFlight.Flight::leader)
                    .findFirst()
                    .orElseThrow();
            ReusableReportRecord report = new ReusableReportRecord(99L, "报告正文", "PASS");
            singleFlight.complete(leader, report);

            assertThat(flights).filteredOn(ReportGenerationSingleFlight.Flight::leader).hasSize(1);
            assertThat(flights.stream()
                    .map(singleFlight::await)
                    .toList()).containsOnly(Optional.of(report));
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void keepsDifferentOwnersIsolated() {
        ReportGenerationSingleFlight singleFlight = new ReportGenerationSingleFlight();

        ReportGenerationSingleFlight.Flight firstOwner = singleFlight.acquire(7L, "context-hash");
        ReportGenerationSingleFlight.Flight secondOwner = singleFlight.acquire(8L, "context-hash");

        assertThat(firstOwner.leader()).isTrue();
        assertThat(secondOwner.leader()).isTrue();
    }

    @Test
    void releasesKeyWhenLeaderProducesNoReusableReport() {
        ReportGenerationSingleFlight singleFlight = new ReportGenerationSingleFlight();
        ReportGenerationSingleFlight.Flight leader = singleFlight.acquire(7L, "context-hash");
        ReportGenerationSingleFlight.Flight follower = singleFlight.acquire(7L, "context-hash");

        singleFlight.completeWithoutReusable(leader);

        assertThat(singleFlight.await(follower)).isEmpty();
        assertThat(singleFlight.acquire(7L, "context-hash").leader()).isTrue();
    }

    private <T> T get(Future<T> future) {
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
