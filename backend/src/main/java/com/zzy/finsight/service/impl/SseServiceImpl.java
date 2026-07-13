package com.zzy.finsight.service.impl;

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
