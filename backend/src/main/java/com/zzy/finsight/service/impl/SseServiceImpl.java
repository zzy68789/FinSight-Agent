package com.zzy.finsight.service.impl;

import com.zzy.finsight.agent.event.AgentEvent;
import com.zzy.finsight.dto.SseEvent;
import com.zzy.finsight.service.SseService;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;

/**
 * 实现股票工作流的 SSE 事件推送。
 */
@Service
public class SseServiceImpl implements SseService {
    public void send(SseEmitter emitter, String step, Object data) throws IOException {
        emitter.send(SseEmitter.event().data(new SseEvent(step, data)));
    }

    @Override
    public void sendAgentEvent(SseEmitter emitter, AgentEvent event) throws IOException {
        emitter.send(SseEmitter.event()
                .id(Long.toString(event.sequence()))
                .name(event.type())
                .data(new SseEvent(event.type(), event.ssePayload())));
    }

    public void done(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }

    public void error(SseEmitter emitter, Throwable throwable) {
        try {
            emitter.send(SseEmitter.event().data(new SseEvent("error", Map.of("message", errorMessage(throwable)))));
            emitter.complete();
        } catch (IOException e) {
            emitter.completeWithError(e);
        }
    }

    private String errorMessage(Throwable throwable) {
        String message = throwable == null ? null : throwable.getMessage();
        if (message == null || message.isBlank()) {
            return "任务执行失败，请稍后重试";
        }
        return message;
    }
}
