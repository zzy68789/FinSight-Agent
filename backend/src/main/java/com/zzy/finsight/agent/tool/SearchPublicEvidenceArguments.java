package com.zzy.finsight.agent.tool;

/**
 * 表示公开证据检索的类型化参数。
 *
 * @param query 可选的聚焦检索问题。
 */
public record SearchPublicEvidenceArguments(String query) implements ToolArguments {
    public SearchPublicEvidenceArguments {
        query = query == null ? "" : query.trim();
    }
}
