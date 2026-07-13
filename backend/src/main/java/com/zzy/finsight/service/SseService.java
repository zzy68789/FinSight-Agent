package com.zzy.finsight.service;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;

/**
 * 定义 SSE 事件发送和结束操作。
 */
public interface SseService {
    /** 发送一个工作流步骤事件。 */
    void send(SseEmitter emitter, String step, Object data) throws IOException;

    /** 发送结束事件并关闭连接。 */
    void done(SseEmitter emitter) throws IOException;

    /** 发送错误事件并关闭连接。 */
    void error(SseEmitter emitter, Throwable throwable);
}
