package com.zzy.drai.service;

import com.zzy.drai.dto.SseEvent;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

@Service
public class SseService {
    public void send(SseEmitter emitter, String step, Object data) throws IOException {
        emitter.send(SseEmitter.event().data(new SseEvent(step, data)));
    }

    public void done(SseEmitter emitter) throws IOException {
        emitter.send(SseEmitter.event().data("[DONE]"));
        emitter.complete();
    }

    public void error(SseEmitter emitter, Throwable throwable) {
        emitter.completeWithError(throwable);
    }
}
