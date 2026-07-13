package com.zzy.finsight.domain.stock;


/**
 * 表示引用审查结果。
 * @param status 当前状态。
 * @param reason 状态原因。
 */
public record CitationReviewResult(String status, String reason) {
    public static CitationReviewResult pass() {
        return new CitationReviewResult("PASS", "");
    }

    public static CitationReviewResult fail(String reason) {
        return new CitationReviewResult("FAIL", reason);
    }
}
