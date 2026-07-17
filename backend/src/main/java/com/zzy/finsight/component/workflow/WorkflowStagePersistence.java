package com.zzy.finsight.component.workflow;

import com.zzy.finsight.mapper.AgentStepLogMapper;
import com.zzy.finsight.mapper.CheckpointMapper;
import com.zzy.finsight.mapper.ResearchTaskMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;

/**
 * 以单个数据库事务提交工作流阶段日志、检查点和任务租约心跳。
 */
@Component
public class WorkflowStagePersistence {
    private final AgentStepLogMapper stepLogMapper;
    private final CheckpointMapper checkpointMapper;
    private final ResearchTaskMapper taskMapper;
    private final TaskRuntimeStateService runtimeStateService;

    public WorkflowStagePersistence(
            AgentStepLogMapper stepLogMapper,
            CheckpointMapper checkpointMapper,
            ResearchTaskMapper taskMapper,
            TaskRuntimeStateService runtimeStateService
    ) {
        this.stepLogMapper = stepLogMapper;
        this.checkpointMapper = checkpointMapper;
        this.taskMapper = taskMapper;
        this.runtimeStateService = runtimeStateService;
    }

    /** 原子提交可恢复阶段，并在数据库提交后刷新 Redis/内存运行态。 */
    @Transactional
    public void persist(StageCommit commit) {
        stepLogMapper.save(
                commit.taskId(),
                commit.step(),
                commit.data(),
                commit.attemptNo(),
                commit.durationMs(),
                commit.status(),
                commit.errorMessage()
        );
        checkpointMapper.save(
                commit.threadId(),
                commit.taskId(),
                commit.stage(),
                commit.attemptNo(),
                commit.generationContextHash(),
                commit.data()
        );
        boolean leaseUpdated = taskMapper.updateStage(
                commit.taskId(), commit.stage(), commit.leaseOwner(), commit.leaseUntil()
        );
        if (!leaseUpdated) {
            throw new IllegalStateException("任务租约已失效，拒绝提交阶段 " + commit.stage());
        }
        afterCommit(() -> runtimeStateService.recordStep(
                commit.taskId(), commit.threadId(), commit.step(), commit.data()
        ));
    }

    private void afterCommit(Runnable callback) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            callback.run();
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                callback.run();
            }
        });
    }

    /**
     * @param taskId 任务编号。
     * @param threadId 会话线程编号。
     * @param step SSE 使用的小写步骤名。
     * @param stage 数据库使用的大写阶段名。
     * @param data 阶段输出快照。
     * @param durationMs 阶段耗时毫秒数。
     * @param attemptNo 阶段尝试次数。
     * @param status 步骤执行状态。
     * @param errorMessage 降级或失败说明。
     * @param generationContextHash 报告生成上下文指纹。
     * @param leaseOwner 当前租约持有者。
     * @param leaseUntil 续租后的到期时间。
     */
    public record StageCommit(
            long taskId,
            String threadId,
            String step,
            String stage,
            Object data,
            long durationMs,
            int attemptNo,
            String status,
            String errorMessage,
            String generationContextHash,
            String leaseOwner,
            LocalDateTime leaseUntil
    ) {
    }
}
