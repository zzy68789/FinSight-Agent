package com.zzy.drai.llm;

final class LlmFallbacks {
    private LlmFallbacks() {
    }

    static String fallback(String prompt, LlmClient.ModelType modelType, Exception cause) {
        if (modelType == LlmClient.ModelType.SMART) {
            return "{\"status\":\"FAIL\",\"feedback\":\"LLM 调用失败，请稍后重试或检查模型配置。\"}";
        }
        if (prompt != null && prompt.contains("JSON array")) {
            return "[\"梳理问题背景\", \"检索关键证据\", \"分析技术路线\", \"总结风险与趋势\"]";
        }
        String firstLine = prompt == null ? "DRAI" : prompt.lines().findFirst().orElse("DRAI");
        return "## DRAI 调研报告\n\n"
                + "当前未配置可用的大模型 API，系统使用本地降级内容完成流程验证。\n\n"
                + "- 任务摘要：" + firstLine + "\n"
                + "- 工程链路：Planner、Researcher、Writer、Reviewer 已完成状态流转。\n"
                + "- 后续建议：配置 `DRAI_LLM_API_KEY`、Tavily Key 和 ChromaDB 后验证真实调研质量。\n";
    }
}
