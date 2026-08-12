package com.zzy.finsight.agent.quality;

/**
 * 表示质量门禁允许 Runtime 执行的下一步动作。
 */
public enum QualityGateRoute {
    PASS,
    COLLECT_MORE_EVIDENCE,
    RECALCULATE_DETERMINISTIC_RESULTS,
    REWRITE_REPORT,
    STOP_FAILED
}
