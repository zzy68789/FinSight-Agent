package com.zzy.finsight.component.workflow;

import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class WorkflowStagePersistenceTest {

    @Test
    void persistsStepCheckpointHeartbeatAndThenUpdatesRuntimeState() {
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
        LocalDateTime leaseUntil = LocalDateTime.of(2026, 7, 17, 18, 0);
        Map<String, Object> data = Map.of("finalReport", "报告");
        when(taskMapper.updateStage(11L, "WRITER", "runner-a", leaseUntil)).thenReturn(true);
        WorkflowStagePersistence persistence = new WorkflowStagePersistence(
                stepLogMapper, checkpointMapper, taskMapper, runtimeStateService
        );

        persistence.persist(new WorkflowStagePersistence.StageCommit(
                11L, "thread-a", "writer", "WRITER", data, 80L, 2,
                "SUCCESS", null, "context-hash", "runner-a", leaseUntil
        ));

        verify(stepLogMapper).save(11L, "writer", data, 2, 80L, "SUCCESS", null);
        verify(checkpointMapper).save("thread-a", 11L, "WRITER", 2, "context-hash", data);
        verify(taskMapper).updateStage(11L, "WRITER", "runner-a", leaseUntil);
        verify(runtimeStateService).recordStep(11L, "thread-a", "writer", data);
    }

    @Test
    void rejectsStageCommitAfterLeaseIsLost() {
        AgentStepLogMapper stepLogMapper = mock(AgentStepLogMapper.class);
        CheckpointMapper checkpointMapper = mock(CheckpointMapper.class);
        ResearchTaskMapper taskMapper = mock(ResearchTaskMapper.class);
        TaskRuntimeStateService runtimeStateService = mock(TaskRuntimeStateService.class);
        LocalDateTime leaseUntil = LocalDateTime.of(2026, 7, 17, 18, 0);
        WorkflowStagePersistence persistence = new WorkflowStagePersistence(
                stepLogMapper, checkpointMapper, taskMapper, runtimeStateService
        );

        assertThatThrownBy(() -> persistence.persist(new WorkflowStagePersistence.StageCommit(
                11L, "thread-a", "writer", "WRITER", Map.of(), 0L, 1,
                "SUCCESS", null, null, "expired-runner", leaseUntil
        ))).isInstanceOf(IllegalStateException.class).hasMessageContaining("租约已失效");

        verify(runtimeStateService, never()).recordStep(11L, "thread-a", "writer", Map.of());
    }
}
