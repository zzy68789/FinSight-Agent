package com.zzy.finsight.financial;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialComplianceReviewerTest {

    private final FinancialComplianceReviewer reviewer = new FinancialComplianceReviewer();

    @Test
    void failsWhenReportPromisesGuaranteedReturnOrOmitsDisclaimer() {
        FinancialComplianceReviewResult result = reviewer.review(
                "## 结论\n本报告保证收益 100%，可以直接买入。",
                CitationReviewResult.pass()
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.score()).isLessThan(new BigDecimal("60.00"));
        assertThat(result.issues())
                .extracting(FinancialComplianceIssue::category)
                .contains("disclaimer", "regulatory");
    }

    @Test
    void passesWhenDisclaimerAndCitationReviewAreClean() {
        FinancialComplianceReviewResult result = reviewer.review(
                "## 报告\n仅作研究辅助，不构成投资建议。\n\n## 引用与数据快照\n- E1",
                CitationReviewResult.pass()
        );

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.score()).isEqualByComparingTo("100.00");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void doesNotTreatPlainPercentageMetricAsGuaranteedReturn() {
        FinancialComplianceReviewResult result = reviewer.review(
                "仅作研究辅助，不构成投资建议。\n毛利率为 100%，该指标来自财务报表。",
                CitationReviewResult.pass()
        );

        assertThat(result.status()).isEqualTo("PASS");
        assertThat(result.issues()).isEmpty();
    }

    @Test
    void carriesCitationReviewerFailureIntoComplianceIssues() {
        FinancialComplianceReviewResult result = reviewer.review(
                "仅作研究辅助，不构成投资建议。",
                CitationReviewResult.fail("EVIDENCE_INSUFFICIENT: 有效证据少于 3 条")
        );

        assertThat(result.status()).isEqualTo("FAIL");
        assertThat(result.issues()).extracting(FinancialComplianceIssue::category).contains("citation");
    }
}
