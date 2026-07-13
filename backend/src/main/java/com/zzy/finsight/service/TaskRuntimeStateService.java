package com.zzy.finsight.service;

import java.util.Optional;

public interface TaskRuntimeStateService {
    void taskCreated(long taskId, String threadId);

    void markStatus(long taskId, String status);

    void bindLatestTask(String threadId, long taskId);

    void recordStep(long taskId, String threadId, String step, Object data);

    Optional<String> getStatus(long taskId);

    Optional<Long> getLatestTaskId(String threadId);

    Optional<String> getProgress(long taskId);

    Optional<String> getLastEvent(long taskId);
}
