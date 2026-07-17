package com.zzy.finsight.config;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AsyncExecutionConfigTest {

    @Test
    void rejectsSubmissionWhenWorkerAndBoundedQueueAreFull() throws InterruptedException {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        ExecutorService executor = new AsyncExecutionConfig().boundedExecutor(
                "test", 1, 1, "test-executor-", registry
        );
        CountDownLatch workerStarted = new CountDownLatch(1);
        CountDownLatch releaseWorker = new CountDownLatch(1);
        try {
            executor.submit(() -> {
                workerStarted.countDown();
                try {
                    releaseWorker.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
            });
            assertThat(workerStarted.await(1, TimeUnit.SECONDS)).isTrue();
            executor.submit(() -> {
            });

            assertThatThrownBy(() -> executor.submit(() -> {
            })).isInstanceOf(RejectedExecutionException.class)
                    .hasMessageContaining("队列已满");
            assertThat(registry.counter("finsight.async.rejected", "executor", "test").count())
                    .isEqualTo(1.0);
        } finally {
            releaseWorker.countDown();
            executor.shutdownNow();
        }
    }
}
