package com.zzy.drai.llm;

import java.util.List;

public final class PromptTemplates {
    private PromptTemplates() {
    }

    public static String planner(String query, String critique) {
        return """
                你是调研规划助手。请针对用户问题生成 3-5 个简短检索子问题，用逗号分隔，不要输出额外解释。
                用户问题：%s
                上轮审查意见：%s
                """.formatted(query, critique == null ? "" : critique);
    }

    public static String writer(String query, List<String> searchResults, String critique) {
        return """
                你是专业技术报告撰写人。请基于资料生成结构化 Markdown 调研报告，不要编造资料外事实。
                用户问题：%s
                资料：
                %s
                上轮审查意见：%s
                """.formatted(query, String.join("\n\n", searchResults), critique == null ? "" : critique);
    }

    public static String reviewer(String query, String report) {
        return """
                请审查报告是否充分回答用户问题，并只输出合法 JSON：
                {"status":"PASS","feedback":""}
                或
                {"status":"FAIL","feedback":"一条具体改进建议"}
                用户问题：%s
                报告：
                %s
                """.formatted(query, report);
    }

    public static String refine(String oldReport, String instruction) {
        return """
                你是报告编辑。请根据用户修改指令改写旧报告，保持 Markdown 结构，直接输出完整新版报告。
                旧报告：
                %s
                修改指令：
                %s
                """.formatted(oldReport, instruction);
    }
}
