package com.zzy.finsight.infrastructure.serialization;

import com.zzy.finsight.dto.stock.StockReportRequest;


import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

/**
 * 序列化和反序列化股票报告请求。
 */
@Component
public class StockReportRequestCodec {
    private final ObjectMapper objectMapper;

    public StockReportRequestCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String toJson(StockReportRequest request) {
        try {
            return objectMapper.writeValueAsString(request);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("序列化股票报告请求失败", e);
        }
    }

    public StockReportRequest fromJson(String json) {
        if (json == null || json.isBlank()) {
            throw new IllegalArgumentException("任务缺少可恢复的股票报告请求");
        }
        try {
            return objectMapper.readValue(json, StockReportRequest.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("任务中的股票报告请求无法解析", e);
        }
    }
}
