package com.zzy.finsight.agent.quality;

/**
 * 表示质量门禁识别出的稳定失败类型，供 Runtime 决定后续控制流。
 */
public enum QualityGateFailureType {
    EVIDENCE_INSUFFICIENT,
    EVIDENCE_INVALID,
    CITATION_INVALID,
    NUMERIC_MISMATCH,
    REPORT_SEMANTIC_INVALID,
    COMPLIANCE_VIOLATION,
    EVALUATION_FAILED,
    REVIEW_OUTPUT_INVALID
}
