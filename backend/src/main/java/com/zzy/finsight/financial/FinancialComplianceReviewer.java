package com.zzy.finsight.financial;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Component
public class FinancialComplianceReviewer {
    public static final String POLICY_VERSION = "compliance-policy-v1";

    public FinancialComplianceReviewResult review(String report, CitationReviewResult citationReview) {
        List<FinancialComplianceIssue> issues = new ArrayList<>();
        String text = report == null ? "" : report;
        String normalized = text.toLowerCase(Locale.ROOT);

        if (!text.contains("不构成投资建议") && !normalized.contains("disclaimer")) {
            issues.add(new FinancialComplianceIssue(
                    "critical",
                    "disclaimer",
                    "报告缺少投资建议免责声明",
                    "补充“仅作研究辅助，不构成投资建议”等明确表述"
            ));
        }
        if (promisesReturn(text, normalized)) {
            issues.add(new FinancialComplianceIssue(
                    "critical",
                    "regulatory",
                    "报告包含收益保证或绝对化表述",
                    "删除保证性收益承诺，改为风险提示和观察点"
            ));
        }
        if (text.contains("内幕") || normalized.contains("insider")) {
            issues.add(new FinancialComplianceIssue(
                    "critical",
                    "regulatory",
                    "报告可能涉及内幕信息表述",
                    "删除内幕信息相关内容，仅使用公开来源"
            ));
        }
        if (citationReview != null && "FAIL".equals(citationReview.status())) {
            issues.add(new FinancialComplianceIssue(
                    "critical",
                    "citation",
                    citationReview.reason(),
                    "补齐证据、修正引用或显式说明数据缺失"
            ));
        }

        long criticalCount = issues.stream().filter(issue -> "critical".equals(issue.severity())).count();
        BigDecimal score = BigDecimal.valueOf(Math.max(0, 100 - criticalCount * 25))
                .setScale(2, RoundingMode.HALF_UP);
        String status = criticalCount == 0 && score.compareTo(new BigDecimal("60.00")) >= 0 ? "PASS" : "FAIL";
        return new FinancialComplianceReviewResult(status, score, issues);
    }

    private boolean promisesReturn(String text, String normalized) {
        return text.contains("保证收益")
                || text.contains("承诺收益")
                || text.contains("稳赚")
                || text.contains("必涨")
                || text.contains("百分百收益")
                || text.contains("100%收益")
                || text.contains("收益100%")
                || normalized.contains("guaranteed return")
                || normalized.contains("risk-free return");
    }
}
