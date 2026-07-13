package com.zzy.finsight.service;

import java.util.Optional;

/**
 * 定义任务运行状态的缓存操作。
 */
public interface TaskRuntimeStateService {
    /** 初始化任务运行状态并绑定线程。 */
    void taskCreated(long taskId, String threadId);

    /** 更新任务运行状态。 */
    void markStatus(long taskId, String status);

    /** 绑定线程最近一次任务。 */
    void bindLatestTask(String threadId, long taskId);

    /** 记录任务最近的步骤事件。 */
    void recordStep(long taskId, String threadId, String step, Object data);

    /** 查询任务状态。 */
    Optional<String> getStatus(long taskId);

    /** 查询线程最近一次任务编号。 */
    Optional<Long> getLatestTaskId(String threadId);

    /** 查询任务最近进度。 */
    Optional<String> getProgress(long taskId);

    /** 查询任务最后一次 SSE 事件。 */
    Optional<String> getLastEvent(long taskId);
}
