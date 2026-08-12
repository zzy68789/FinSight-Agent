package com.zzy.finsight.agent.tool;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;

import java.util.List;

/**
 * 表示白名单研究工具的结构化执行结果。
 * @param status 执行状态。
 * @param summary 面向 Planner 的短观察摘要。
 * @param payload 面向 SSE 和 Trace 的结构化结果。
 * @param evidenceItems 本次新增的原始金融证据。
 * @param errorCode 稳定错误分类。
 * @param retryable 是否允许 Runtime 重试。
 */
public record ToolResult(
        String status,
        String summary,
        ToolPayload payload,
        List<FinancialEvidenceItem> evidenceItems,
        String errorCode,
        boolean retryable
) {
    public ToolResult {
        status = status == null || status.isBlank() ? "SUCCESS" : status;
        summary = summary == null ? "" : summary;
        payload = payload == null ? new ToolPayload.Empty() : payload;
        evidenceItems = evidenceItems == null ? List.of() : List.copyOf(evidenceItems);
        errorCode = errorCode == null ? "" : errorCode;
    }

    /** 创建成功工具结果。 */
    public static ToolResult success(String summary, ToolPayload payload) {
        return new ToolResult("SUCCESS", summary, payload, List.of(), "", false);
    }

    /** 创建无附加负载的成功工具结果。 */
    public static ToolResult success(String summary) {
        return success(summary, new ToolPayload.Empty());
    }

    /** 创建失败工具结果。 */
    public static ToolResult failure(String summary, String errorCode, boolean retryable) {
        return new ToolResult("FAILED", summary, new ToolPayload.Empty(), List.of(), errorCode, retryable);
    }
}
