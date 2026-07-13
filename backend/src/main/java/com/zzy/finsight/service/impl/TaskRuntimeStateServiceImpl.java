package com.zzy.finsight.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.service.TaskRuntimeStateService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Service
public class TaskRuntimeStateServiceImpl implements TaskRuntimeStateService {
    private static final String TASK_STATUS_KEY = "finsight:task:%d:status";
    private static final String TASK_PROGRESS_KEY = "finsight:task:%d:progress";
    private static final String THREAD_LATEST_TASK_KEY = "finsight:thread:%s:latestTask";
    private static final String SSE_LAST_EVENT_KEY = "finsight:sse:%d:last-event";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration runtimeTtl;
    private final ConcurrentMap<Long, String> localStatus = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> localProgress = new ConcurrentHashMap<>();
    private final ConcurrentMap<Long, String> localLastEvent = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> localLatestTaskByThread = new ConcurrentHashMap<>();

    public TaskRuntimeStateServiceImpl(
            StringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            @Value("${finsight.redis.runtime-ttl:PT24H}") Duration runtimeTtl
    ) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.runtimeTtl = runtimeTtl;
    }

    public void taskCreated(long taskId, String threadId) {
        markStatus(taskId, "CREATED");
        bindLatestTask(threadId, taskId);
    }

    public void markStatus(long taskId, String status) {
        localStatus.put(taskId, status);
        write(TASK_STATUS_KEY.formatted(taskId), status);
    }

    public void bindLatestTask(String threadId, long taskId) {
        if (threadId == null || threadId.isBlank()) {
            return;
        }
        localLatestTaskByThread.put(threadId, taskId);
        write(THREAD_LATEST_TASK_KEY.formatted(threadId), String.valueOf(taskId));
    }

    public void recordStep(long taskId, String threadId, String step, Object data) {
        String event = toJson(Map.of(
                "taskId", taskId,
                "threadId", threadId,
                "step", step,
                "data", data,
                "createdAt", Instant.now().toString()
        ));
        localProgress.put(taskId, event);
        localLastEvent.put(taskId, event);
        write(TASK_PROGRESS_KEY.formatted(taskId), event);
        write(SSE_LAST_EVENT_KEY.formatted(taskId), event);
    }

    public Optional<String> getStatus(long taskId) {
        return read(TASK_STATUS_KEY.formatted(taskId))
                .or(() -> Optional.ofNullable(localStatus.get(taskId)));
    }

    public Optional<Long> getLatestTaskId(String threadId) {
        return read(THREAD_LATEST_TASK_KEY.formatted(threadId))
                .map(Long::parseLong)
                .or(() -> Optional.ofNullable(localLatestTaskByThread.get(threadId)));
    }

    public Optional<String> getProgress(long taskId) {
        return read(TASK_PROGRESS_KEY.formatted(taskId))
                .or(() -> Optional.ofNullable(localProgress.get(taskId)));
    }

    public Optional<String> getLastEvent(long taskId) {
        return read(SSE_LAST_EVENT_KEY.formatted(taskId))
                .or(() -> Optional.ofNullable(localLastEvent.get(taskId)));
    }

    private void write(String key, String value) {
        try {
            redisTemplate.opsForValue().set(key, value, runtimeTtl);
        } catch (RuntimeException ignored) {
            // Keep the workflow usable when Redis is unavailable in local development.
        }
    }

    private Optional<String> read(String key) {
        try {
            return Optional.ofNullable(redisTemplate.opsForValue().get(key))
                    .filter(value -> !value.isBlank());
        } catch (RuntimeException ignored) {
            return Optional.empty();
        }
    }

    private String toJson(Map<String, Object> event) {
        try {
            return objectMapper.writeValueAsString(new LinkedHashMap<>(event));
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }
}
