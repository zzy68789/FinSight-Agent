package com.zzy.finsight.agent.quality;

/**
 * 表示一项可持久化、可路由的质量门禁问题。
 *
 * @param type 稳定失败类型。
 * @param source 问题来源。
 * @param code 来源模块给出的稳定问题码。
 * @param detail 面向诊断和重写的详细说明。
 */
public record QualityGateIssue(
        QualityGateFailureType type,
        QualityGateSource source,
        String code,
        String detail
) {
    public QualityGateIssue {
        if (type == null || source == null) {
            throw new IllegalArgumentException("质量门禁问题必须包含类型和来源");
        }
        code = code == null || code.isBlank() ? "UNKNOWN" : code.trim();
        detail = detail == null ? "" : detail.trim();
    }
}
