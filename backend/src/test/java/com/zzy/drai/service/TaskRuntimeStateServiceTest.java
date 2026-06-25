package com.zzy.drai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TaskRuntimeStateServiceTest {
    private static final Duration TTL = Duration.ofHours(6);

    @Mock
    StringRedisTemplate redisTemplate;

    @Mock
    ValueOperations<String, String> valueOperations;

    @Test
    void taskCreatedStoresStatusAndLatestThreadTaskInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TaskRuntimeStateService service = new TaskRuntimeStateService(redisTemplate, new ObjectMapper(), TTL);

        service.taskCreated(42L, "thread-1");

        verify(valueOperations).set("drai:task:42:status", "CREATED", TTL);
        verify(valueOperations).set("drai:thread:thread-1:latestTask", "42", TTL);
    }

    @Test
    void recordStepStoresProgressAndLastSseEventInRedis() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        TaskRuntimeStateService service = new TaskRuntimeStateService(redisTemplate, new ObjectMapper(), TTL);

        service.recordStep(42L, "thread-1", "planner", Map.of("plan", "draft"));

        verify(valueOperations).set(eq("drai:task:42:progress"), contains("\"step\":\"planner\""), eq(TTL));
        verify(valueOperations).set(eq("drai:sse:42:last-event"), contains("\"step\":\"planner\""), eq(TTL));
    }

    @Test
    void fallsBackToLocalStateWhenRedisIsUnavailable() {
        when(redisTemplate.opsForValue()).thenThrow(new RedisConnectionFailureException("redis down"));
        TaskRuntimeStateService service = new TaskRuntimeStateService(redisTemplate, new ObjectMapper(), TTL);

        service.taskCreated(42L, "thread-1");
        service.markStatus(42L, "RUNNING");

        assertThat(service.getStatus(42L)).contains("RUNNING");
        assertThat(service.getLatestTaskId("thread-1")).contains(42L);
    }
}
