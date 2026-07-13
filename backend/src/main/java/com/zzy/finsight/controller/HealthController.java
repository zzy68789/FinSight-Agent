package com.zzy.finsight.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 提供后端基础健康检查接口。
 */
@RestController
public class HealthController {
    /** 返回后端运行状态和当前工作流标识。 */
    @GetMapping("/")
    public Map<String, String> health() {
        return Map.of(
                "status", "running",
                "backend", "java",
                "workflow", "stock-report-pipeline"
        );
    }
}
