package com.zzy.finsight.infrastructure.serialization;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import org.springframework.stereotype.Component;

/**
 * 负责序列化和恢复 Research Agent 请求。
 */
@Component
public class ResearchRunRequestCodec {
    private final ObjectMapper objectMapper;

    public ResearchRunRequestCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 将运行请求编码为任务可恢复载荷。 */
    public String toJson(ResearchRunRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法序列化 Research Agent 请求", exception);
        }
    }

    /** 从任务载荷恢复运行请求。 */
    public ResearchRunRequest fromJson(String json) {
        try {
            return objectMapper.readValue(json, ResearchRunRequest.class);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("无法恢复 Research Agent 请求", exception);
        }
    }
}
