package com.zzy.finsight.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

public interface SseService {
    void send(SseEmitter emitter, String step, Object data) throws IOException;

    void done(SseEmitter emitter) throws IOException;

    void error(SseEmitter emitter, Throwable throwable);
}
