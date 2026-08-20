package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.agent.event.AgentEventOutboxPublisher;
import com.zzy.finsight.domain.TaskExecutionRecord;
import com.zzy.finsight.mapper.AgentEventOutboxMapper;
import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ResearchRunCancellationServiceImplTest {
    private final ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
    private final AgentEventOutboxMapper eventMapper = mock(AgentEventOutboxMapper.class);
    private final AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
    private final TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
    private final AgentEventOutboxPublisher eventPublisher = mock(AgentEventOutboxPublisher.class);
    private final ResearchRunCancellationServiceImpl service = new ResearchRunCancellationServiceImpl(
            taskMapper, eventMapper, stepLogMapper, runtimeStateService, eventPublisher
    );

    @Test
    void cancelsRunningTaskAndPublishesTerminalEvent() {
        when(taskMapper.findExecution(7L, 19L)).thenReturn(Optional.of(task("RUNNING")));
        when(taskMapper.cancelAgentTask(anyLong(), anyLong(), any(LocalDateTime.class))).thenReturn(1);
        when(taskMapper.findEventSequence(7L, 19L)).thenReturn(Optional.of(8L));
        when(eventMapper.insert(any(AgentEvent.class))).thenReturn(99L);

        service.cancel(7L, 19L);

        ArgumentCaptor<AgentEvent> event = ArgumentCaptor.forClass(AgentEvent.class);
        verify(eventMapper).insert(event.capture());
        assertThat(event.getValue().type()).isEqualTo("run_stopped");
        assertThat(event.getValue().status()).isEqualTo("CANCELLED");
        assertThat(event.getValue().sequence()).isEqualTo(8L);
        verify(runtimeStateService).markStatus(19L, "CANCELLED");
        verify(eventPublisher).publish(any(AgentEvent.class), any());
    }

    @Test
    void repeatedCancellationIsIdempotent() {
        when(taskMapper.findExecution(7L, 19L)).thenReturn(Optional.of(task("CANCELLED")));

        service.cancel(7L, 19L);

        verify(taskMapper, never()).cancelAgentTask(anyLong(), anyLong(), any(LocalDateTime.class));
        verify(eventMapper, never()).insert(any());
    }

    private TaskExecutionRecord task(String status) {
        return new TaskExecutionRecord(
                19L, 7L, "thread-1", status, status, 1, "{}", "",
                LocalDateTime.now(), "runner", LocalDateTime.now().plusMinutes(1), LocalDateTime.now()
        );
    }
}
