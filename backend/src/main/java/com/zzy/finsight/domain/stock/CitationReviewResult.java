package com.zzy.finsight.domain.stock;


/**
 * 表示引用审查结果。
 * @param status 当前状态。
 * @param code 稳定问题码，通过时为空。
 * @param reason 状态原因。
 */
public record CitationReviewResult(String status, String code, String reason) {
    public CitationReviewResult {
        status = status == null ? "FAIL" : status;
        reason = reason == null ? "" : reason.trim();
        code = code == null || code.isBlank() ? extractCode(reason) : code.trim();
    }

    /** 保留旧的状态与原因构造方式，并从原因前缀提取稳定问题码。 */
    public CitationReviewResult(String status, String reason) {
        this(status, "", reason);
    }

    public static CitationReviewResult pass() {
        return new CitationReviewResult("PASS", "", "");
    }

    public static CitationReviewResult fail(String reason) {
        return new CitationReviewResult("FAIL", "", reason);
    }

    /** 使用独立稳定码和可读原因创建失败结果。 */
    public static CitationReviewResult fail(String code, String reason) {
        return new CitationReviewResult("FAIL", code, reason);
    }

    private static String extractCode(String reason) {
        String normalized = reason == null ? "" : reason.trim();
        int asciiSeparator = normalized.indexOf(':');
        int chineseSeparator = normalized.indexOf('：');
        int separator = asciiSeparator < 0 ? chineseSeparator
                : chineseSeparator < 0 ? asciiSeparator : Math.min(asciiSeparator, chineseSeparator);
        String candidate = separator < 0 ? normalized : normalized.substring(0, separator);
        String code = candidate.toUpperCase(java.util.Locale.ROOT)
                .replaceAll("[^A-Z0-9_]+", "_")
                .replaceAll("^_+|_+$", "");
        return code;
    }
}
