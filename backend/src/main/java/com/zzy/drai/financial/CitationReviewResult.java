package com.zzy.drai.financial;

public record CitationReviewResult(String status, String reason) {
    public static CitationReviewResult pass() {
        return new CitationReviewResult("PASS", "");
    }

    public static CitationReviewResult fail(String reason) {
        return new CitationReviewResult("FAIL", reason);
    }
}
