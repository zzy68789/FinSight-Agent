package com.zzy.finsight.config;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 配置股票工作流和金融数据采集的有界执行器。
 */
@Configuration
public class AsyncExecutionConfig {

    @Bean(name = "workflowExecutor", destroyMethod = "shutdown")
    public ExecutorService workflowExecutor(
            @Value("${finsight.async.workflow-threads:8}") int threadCount,
            @Value("${finsight.async.workflow-queue-capacity:32}") int queueCapacity,
            MeterRegistry meterRegistry
    ) {
        return boundedExecutor(
                "workflow", threadCount, queueCapacity, "finsight-workflow-", meterRegistry
        );
    }

    @Bean(name = "financialProviderExecutor", destroyMethod = "shutdown")
    public ExecutorService financialProviderExecutor(
            @Value("${finsight.async.financial-provider-threads:6}") int threadCount,
            @Value("${finsight.async.financial-provider-queue-capacity:24}") int queueCapacity,
            MeterRegistry meterRegistry
    ) {
        return boundedExecutor(
                "financial_provider", threadCount, queueCapacity, "finsight-financial-provider-", meterRegistry
        );
    }

    /** 创建固定工作线程数和固定等待队列容量的执行器。 */
    ExecutorService boundedExecutor(
            String executorName,
            int threadCount,
            int queueCapacity,
            String threadPrefix,
            MeterRegistry meterRegistry
    ) {
        int normalizedThreads = Math.max(1, threadCount);
        int normalizedCapacity = Math.max(1, queueCapacity);
        ThreadPoolExecutor executor = new ThreadPoolExecutor(
                normalizedThreads,
                normalizedThreads,
                0L,
                TimeUnit.MILLISECONDS,
                new ArrayBlockingQueue<>(normalizedCapacity),
                namedThreadFactory(threadPrefix),
                (task, pool) -> {
                    meterRegistry.counter(
                            "finsight.async.rejected", "executor", executorName
                    ).increment();
                    throw new RejectedExecutionException("执行器队列已满：" + executorName);
                }
        );
        Gauge.builder("finsight.async.active", executor, ThreadPoolExecutor::getActiveCount)
                .tag("executor", executorName)
                .register(meterRegistry);
        Gauge.builder("finsight.async.queued", executor, pool -> pool.getQueue().size())
                .tag("executor", executorName)
                .register(meterRegistry);
        return executor;
    }

    private ThreadFactory namedThreadFactory(String prefix) {
        AtomicInteger index = new AtomicInteger(1);
        return runnable -> {
            Thread thread = new Thread(runnable, prefix + index.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        };
    }
}
