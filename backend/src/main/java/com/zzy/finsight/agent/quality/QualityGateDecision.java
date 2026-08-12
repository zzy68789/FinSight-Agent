package com.zzy.finsight.agent.quality;

import java.util.List;

/**
 * 表示一次统一质量门禁判定及其唯一控制流路由。
 *
 * @param status 门禁状态，仅允许 PASS 或 FAIL。
 * @param route Runtime 应执行的稳定路由。
 * @param issues 归一化后的问题列表。
 * @param summary 可记录到日志并反馈给 Writer 的摘要。
 */
public record QualityGateDecision(
        String status,
        QualityGateRoute route,
        List<QualityGateIssue> issues,
        String summary
) {
    public QualityGateDecision {
        issues = issues == null ? List.of() : List.copyOf(issues);
        summary = summary == null ? "" : summary.trim();
        if (!"PASS".equals(status) && !"FAIL".equals(status)) {
            throw new IllegalArgumentException("质量门禁状态只允许 PASS 或 FAIL");
        }
        if (route == null) {
            throw new IllegalArgumentException("质量门禁必须指定路由");
        }
        if ("PASS".equals(status) && (route != QualityGateRoute.PASS || !issues.isEmpty())) {
            throw new IllegalArgumentException("通过的质量门禁不得包含失败路由或问题");
        }
        if ("FAIL".equals(status) && (route == QualityGateRoute.PASS || issues.isEmpty())) {
            throw new IllegalArgumentException("失败的质量门禁必须包含问题和失败路由");
        }
    }

    /** 创建通过判定。 */
    public static QualityGateDecision pass() {
        return new QualityGateDecision("PASS", QualityGateRoute.PASS, List.of(), "");
    }

    /** 返回门禁是否通过。 */
    public boolean passed() {
        return "PASS".equals(status);
    }
}
