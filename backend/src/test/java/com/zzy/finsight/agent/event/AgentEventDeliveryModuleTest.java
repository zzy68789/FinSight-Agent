package com.zzy.finsight.agent.event;

import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.service.SseService;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AgentEventDeliveryModuleTest {

    @Test
    void buffersLiveEventUntilHistoricalReplayCompletes() throws Exception {
        AgentEventOutboxMapper mapper = mock(AgentEventOutboxMapper.class);
        SseService sseService = mock(SseService.class);
        AgentEventStreamModule module = new AgentEventStreamModule(mapper, sseService);
        CountDownLatch queryStarted = new CountDownLatch(1);
        CountDownLatch releaseQuery = new CountDownLatch(1);
        List<Long> delivered = new CopyOnWriteArrayList<>();
        when(mapper.findAfterSequence(11L, 0L)).thenAnswer(ignored -> {
            queryStarted.countDown();
            assertThat(releaseQuery.await(2, TimeUnit.SECONDS)).isTrue();
            return List.of(event(1L, "run_created"));
        });
        doAnswer(invocation -> {
            AgentEvent event = invocation.getArgument(1);
            delivered.add(event.sequence());
            return null;
        }).when(sseService).sendAgentEvent(any(SseEmitter.class), any(AgentEvent.class));

        CompletableFuture<SseEmitter> subscription = CompletableFuture.supplyAsync(
                () -> module.subscribe(11L, 0L, false)
        );
        assertThat(queryStarted.await(2, TimeUnit.SECONDS)).isTrue();
        module.publish(event(2L, "tool_completed"));
        releaseQuery.countDown();
        subscription.get(2, TimeUnit.SECONDS);

        assertThat(delivered).containsExactly(1L, 2L);
        module.publish(event(3L, "run_completed"));
        verify(sseService).done(any(SseEmitter.class));
    }

    @Test
    void claimsPendingEventsBeforePublishingAcrossInstances() {
        AgentEventOutboxMapper mapper = mock(AgentEventOutboxMapper.class);
        TaskRuntimeStateService runtimeState = mock(TaskRuntimeStateService.class);
        AgentEventStreamModule streamModule = mock(AgentEventStreamModule.class);
        AgentEvent event = event(4L, "tool_completed");
        when(mapper.findClaimed(anyString(), eq(100))).thenReturn(List.of(event));
        AgentEventOutboxPublisher publisher = new AgentEventOutboxPublisher(
                mapper, runtimeState, streamModule, Duration.ofSeconds(30), Duration.ofSeconds(5), 8
        );

        publisher.publishPending();

        verify(mapper).claimBatch(anyString(), any(), any(), any(), eq(100));
        verify(runtimeState).recordStep(11L, "thread-1", "tool_completed", event.ssePayload());
        verify(streamModule).publish(event);
        verify(mapper).markClaimPublished(eq(4L), anyString(), any());
    }

    @Test
    void pollsSharedOutboxForEventsPublishedByAnotherInstance() throws Exception {
        AgentEventOutboxMapper mapper = mock(AgentEventOutboxMapper.class);
        SseService sseService = mock(SseService.class);
        AgentEventStreamModule module = new AgentEventStreamModule(mapper, sseService);
        when(mapper.findAfterSequence(11L, 6L)).thenReturn(List.of());
        module.subscribe(11L, 6L, false);
        AgentEvent remote = event(7L, "tool_completed");
        when(mapper.findAfterSequence(11L, 6L)).thenReturn(List.of(remote));

        module.pollCommittedEvents();

        verify(sseService).sendAgentEvent(any(SseEmitter.class), eq(remote));
    }

    @Test
    void deadLettersPoisonEventAfterConfiguredAttempts() {
        AgentEventOutboxMapper mapper = mock(AgentEventOutboxMapper.class);
        TaskRuntimeStateService runtimeState = mock(TaskRuntimeStateService.class);
        AgentEventStreamModule streamModule = mock(AgentEventStreamModule.class);
        AgentEvent event = event(5L, "tool_completed");
        when(mapper.findClaimed(anyString(), anyInt())).thenReturn(List.of(event));
        when(mapper.findPublishAttempts(5L)).thenReturn(3);
        doThrow(new IllegalStateException("Redis unavailable"))
                .when(runtimeState).recordStep(anyLong(), anyString(), anyString(), any());
        AgentEventOutboxPublisher publisher = new AgentEventOutboxPublisher(
                mapper, runtimeState, streamModule, Duration.ofSeconds(30), Duration.ofSeconds(5), 3
        );

        publisher.publishPending();

        verify(mapper).markDeadLetter(eq(5L), anyString(), any(LocalDateTime.class), eq("Redis unavailable"));
    }

    private AgentEvent event(long sequence, String type) {
        return new AgentEvent(
                sequence,
                AgentEvent.CURRENT_SCHEMA_VERSION,
                11L,
                "thread-1",
                sequence,
                "event-" + sequence,
                1,
                type,
                "SUCCESS",
                Map.of("sequence", sequence),
                "",
                0L,
                null,
                LocalDateTime.of(2026, 8, 12, 12, 0).plusSeconds(sequence)
        );
    }
}
