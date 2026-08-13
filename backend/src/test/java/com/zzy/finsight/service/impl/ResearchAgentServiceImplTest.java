package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.event.AgentEventStreamModule;
import com.zzy.finsight.agent.planning.ResearchIntentPolicy;
import com.zzy.finsight.agent.runtime.AgentTraceReader;
import com.zzy.finsight.agent.runtime.DurableAgentRunner;
import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.component.analysis.ComparisonTickerPolicy;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.dto.agent.ResearchIntent;
import com.zzy.finsight.dto.agent.ResearchRunCreatedResponse;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import com.zzy.finsight.infrastructure.serialization.ResearchRunRequestCodec;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.SseService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ResearchAgentServiceImplTest {
    private final DurableAgentRunner runner = mock(DurableAgentRunner.class);
    private final ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
    private final ResearchRunRequestCodec requestCodec = mock(ResearchRunRequestCodec.class);
    private final AgentTraceReader traceReader = mock(AgentTraceReader.class);
    private final SseService sseService = mock(SseService.class);
    private final AgentEventStreamModule eventStreamModule = mock(AgentEventStreamModule.class);
    private final ExecutorService executorService = mock(ExecutorService.class);
    private final StockCodeResolver resolver = mock(StockCodeResolver.class);
    private final ComparisonTickerPolicy comparisonTickerPolicy = mock(ComparisonTickerPolicy.class);
    private final ResearchIntentPolicy intentPolicy = mock(ResearchIntentPolicy.class);
    private final ResearchAgentServiceImpl service = new ResearchAgentServiceImpl(
            runner, taskMapper, requestCodec, traceReader, sseService, eventStreamModule,
            executorService, resolver, comparisonTickerPolicy, intentPolicy
    );

    @Test
    void normalizesTickerAndValidatesIntentBeforeSubmitting() {
        ResearchRunRequest request = request(ResearchIntent.FINANCIAL_QUALITY);
        when(resolver.resolve("600519")).thenReturn(subject());
        service.run(7L, request, new SseEmitter(0L));

        assertThat(request.getTicker()).isEqualTo("600519.SH");
        verify(intentPolicy).validate(ResearchIntent.FINANCIAL_QUALITY, StockAssetType.EQUITY);
        verify(executorService).submit(any(Callable.class));
    }

    @Test
    void rejectsIncompatibleIntentBeforeSubmittingTask() {
        ResearchRunRequest request = request(ResearchIntent.ETF_TRACKING);
        when(resolver.resolve("600519")).thenReturn(subject());
        doThrow(new IllegalArgumentException("研究意图不适用"))
                .when(intentPolicy).validate(ResearchIntent.ETF_TRACKING, StockAssetType.EQUITY);

        assertThatThrownBy(() -> service.run(7L, request, new SseEmitter(0L)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("不适用");

        verifyNoInteractions(executorService);
    }

    @Test
    void createsPersistentTaskBeforeSchedulingExecution() {
        ResearchRunRequest request = request(ResearchIntent.FINANCIAL_QUALITY);
        when(resolver.resolve("600519")).thenReturn(subject());
        when(taskMapper.findExecutionByClientRequestId(7L, "request-12345678"))
                .thenReturn(Optional.empty());
        when(runner.resolveThreadId(request)).thenReturn("thread-1");
        when(runner.createNewTask(7L, request, "request-12345678")).thenReturn(19L);

        ResearchRunCreatedResponse response = service.create(7L, request, "request-12345678");

        assertThat(response.taskId()).isEqualTo(19L);
        assertThat(response.threadId()).isEqualTo("thread-1");
        assertThat(response.reused()).isFalse();
        verify(executorService).submit(any(Runnable.class));
    }

    @Test
    void reusesTaskForSameClientRequestWithoutSchedulingAgain() {
        ResearchRunRequest request = request(ResearchIntent.FINANCIAL_QUALITY);
        when(resolver.resolve("600519")).thenReturn(subject());
        TaskExecutionRecord existing = new TaskExecutionRecord(
                19L, 7L, "thread-1", "RUNNING", "RESEARCH", 1,
                "payload-1", "", LocalDateTime.now(), "runner", LocalDateTime.now().plusMinutes(1),
                LocalDateTime.now()
        );
        when(taskMapper.findExecutionByClientRequestId(7L, "request-12345678"))
                .thenReturn(Optional.of(existing));
        when(requestCodec.toJson(any(ResearchRunRequest.class))).thenReturn("payload-1");

        ResearchRunCreatedResponse response = service.create(7L, request, "request-12345678");

        assertThat(response.taskId()).isEqualTo(19L);
        assertThat(response.reused()).isTrue();
        assertThat(request.getThreadId()).isEqualTo("thread-1");
        verifyNoInteractions(executorService);
        verify(runner, org.mockito.Mockito.never()).createNewTask(any(Long.class), any(), anyString());
    }

    @Test
    void reschedulesIdempotentTaskLeftInCreatedState() {
        ResearchRunRequest request = request(ResearchIntent.FINANCIAL_QUALITY);
        when(resolver.resolve("600519")).thenReturn(subject());
        TaskExecutionRecord existing = new TaskExecutionRecord(
                20L, 7L, "thread-2", "CREATED", "CREATED", 0,
                "payload-2", "", null, null, null, LocalDateTime.now()
        );
        when(taskMapper.findExecutionByClientRequestId(7L, "request-87654321"))
                .thenReturn(Optional.of(existing));
        when(requestCodec.toJson(any(ResearchRunRequest.class))).thenReturn("payload-2");

        ResearchRunCreatedResponse response = service.create(7L, request, "request-87654321");

        assertThat(response.taskId()).isEqualTo(20L);
        assertThat(response.reused()).isTrue();
        verify(executorService).submit(any(Runnable.class));
    }

    private ResearchRunRequest request(ResearchIntent intent) {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519");
        request.setResearchQuestion("分析盈利质量");
        request.setResearchIntent(intent);
        return request;
    }

    private StockSubject subject() {
        return new StockSubject(
                "600519", "SH", "600519.SH", "贵州茅台", "食品饮料", StockAssetType.EQUITY
        );
    }
}
