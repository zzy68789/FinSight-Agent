package com.zzy.finsight.domain.stock;

import java.util.Set;

/**
 * 集中定义金融证据问题编码及关键语义问题范围。
 */
public final class FinancialEvidenceIssueCodes {
    public static final String DATA_MISSING = "DATA_MISSING";
    public static final String DUPLICATE_PERIOD = "DUPLICATE_PERIOD";
    public static final String PRIOR_PERIOD_MISMATCH = "PRIOR_PERIOD_MISMATCH";
    public static final String INVALID_FINANCIAL_RELATION = "INVALID_FINANCIAL_RELATION";
    public static final String LOW_QUALITY_CONTENT = "LOW_QUALITY_CONTENT";
    public static final String FUTURE_DATA = "FUTURE_DATA";

    private static final Set<String> CRITICAL_CODES = Set.of(
            PRIOR_PERIOD_MISMATCH,
            INVALID_FINANCIAL_RELATION,
            FUTURE_DATA
    );

    private FinancialEvidenceIssueCodes() {
    }

    /** 判断问题是否会破坏关键财务指标的语义正确性。 */
    public static boolean critical(String issueCode) {
        return issueCode != null && CRITICAL_CODES.contains(issueCode);
    }
}
