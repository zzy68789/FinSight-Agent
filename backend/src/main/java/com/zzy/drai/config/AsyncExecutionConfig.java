package com.zzy.drai.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

@Configuration
public class AsyncExecutionConfig {

    @Bean(name = "workflowExecutor", destroyMethod = "shutdown")
    public ExecutorService workflowExecutor(@Value("${drai.async.workflow-threads:8}") int threadCount) {
        return Executors.newFixedThreadPool(Math.max(1, threadCount), namedThreadFactory("drai-workflow-"));
    }

    @Bean(name = "financialProviderExecutor", destroyMethod = "shutdown")
    public ExecutorService financialProviderExecutor(@Value("${drai.async.financial-provider-threads:6}") int threadCount) {
        return Executors.newFixedThreadPool(Math.max(1, threadCount), namedThreadFactory("drai-financial-provider-"));
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
