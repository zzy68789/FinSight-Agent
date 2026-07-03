package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.StringJoiner;

@Component
public class InvestmentReportWriter {

    public String write(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment,
            CitationReviewResult previousReview
    ) {
        StockSubject subject = snapshot.subject();
        StringBuilder report = new StringBuilder();
        report.append("# ").append(subject.fullCode()).append(" A股投研报告\n\n");
        report.append("> 仅作研究辅助，不构成投资建议。报告基于本次数据快照生成，缺失数据不会由模型补写。\n\n");
        if (previousReview != null && "FAIL".equals(previousReview.status())) {
            report.append("> 上轮审查反馈：").append(previousReview.reason()).append("\n\n");
        }
        report.append("## 1. 公司概况\n\n");
        report.append("- 股票代码：").append(subject.fullCode()).append("\n");
        report.append("- 公司名称：").append(subject.companyName()).append("\n");
        report.append("- 所属行业：").append(subject.industry()).append("\n");
        report.append("- 报告期口径：").append(snapshot.reportPeriod()).append("\n\n");

        report.append("## 2. 核心业务与行业位置\n\n");
        report.append(evidenceSentence(snapshot, "LOCAL_CONTEXT", "当前上传报告或公开资料不足以稳定抽取核心业务描述，需结合原始年报复核。")).append("\n\n");

        report.append("## 3. 财务表现\n\n");
        appendMetric(report, metrics, "营收同比");
        appendMetric(report, metrics, "净利率");

        report.append("## 4. 盈利能力与现金流\n\n");
        appendMetric(report, metrics, "毛利率");
        appendMetric(report, metrics, "ROE");
        appendMetric(report, metrics, "经营现金流 / 净利润");

        report.append("## 5. 行情与估值观察\n\n");
        report.append(evidenceSentence(snapshot, "NEWS_SUMMARY", "公开行情、估值或新闻数据缺失，本节仅保留后续复核入口。")).append("\n\n");

        report.append("## 6. 新闻与催化因素\n\n");
        report.append(evidenceSentence(snapshot, "NEWS_SUMMARY", "未取得足够新闻摘要，暂不输出催化因素判断。")).append("\n\n");

        report.append("## 7. 主要风险\n\n");
        if (riskAssessment != null) {
            report.append("- 综合风险评分：").append(riskAssessment.finalScore()).append("/10（")
                    .append(riskAssessment.riskLevel()).append("）。\n");
            for (FinancialRiskDimension dimension : riskAssessment.dimensions()) {
                report.append("- ").append(dimension.name()).append("：")
                        .append(dimension.score()).append("/10，")
                        .append(dimension.reason()).append("\n");
            }
        } else {
            report.append("- 数据风险：若有效证据不足 3 条，本报告只能作为流程回放样例，不能作为正式研究结论。\n");
            report.append("- 口径风险：不同报告期或单位混用时，必须回到原始财报页码/URL 复核。\n");
            report.append("- 市场风险：未接入实时行情时，不输出估值高低或买卖建议。\n");
        }
        report.append("\n");

        report.append("## 8. 结论与后续观察点\n\n");
        report.append("- 结论：优先复核数据快照中标记为 `MISSING_INPUT` 或 `DATA_MISSING` 的条目。\n");
        report.append("- 后续观察：补齐财报 PDF、行情数据源和新闻来源后，重新运行本工作流。\n\n");

        report.append("## 引用与数据快照\n\n");
        int index = 1;
        for (FinancialEvidenceItem item : snapshot.evidenceItems()) {
            report.append("- [E").append(index++).append("] ")
                    .append(item.sourceType()).append(" / ")
                    .append(item.sourceName()).append(" / ")
                    .append(blankToDash(item.reportPeriod())).append(" / ")
                    .append(blankToDash(item.metricName())).append(" / ")
                    .append(item.issueCode() == null || item.issueCode().isBlank() ? "OK" : item.issueCode())
                    .append("：").append(blankToDash(item.excerpt()));
            if (item.url() != null && !item.url().isBlank()) {
                report.append(" (").append(item.url()).append(")");
            }
            report.append("\n");
        }
        report.append("\n### 指标计算公式\n\n");
        for (FinancialMetricResult metric : metrics) {
            report.append("- ").append(metric.metricName()).append("：")
                    .append(metric.status()).append("，")
                    .append(metric.formula()).append("，")
                    .append(metric.displayValue()).append("，证据字段：")
                    .append(String.join(", ", metric.evidenceRefs()))
                    .append(metric.reason().isBlank() ? "" : "，原因：" + metric.reason())
                    .append("\n");
        }
        if (riskAssessment != null) {
            report.append("\n### 风险评分明细\n\n");
            for (FinancialRiskDimension dimension : riskAssessment.dimensions()) {
                report.append("- ").append(dimension.name()).append("：权重")
                        .append(dimension.weight()).append("%，评分")
                        .append(dimension.score()).append("/10，证据：")
                        .append(dimension.evidenceRef()).append("，说明：")
                        .append(dimension.reason()).append("\n");
            }
        }
        return report.toString();
    }

    private void appendMetric(StringBuilder report, List<FinancialMetricResult> metrics, String metricName) {
        FinancialMetricResult metric = metrics.stream()
                .filter(item -> metricName.equals(item.metricName()))
                .findFirst()
                .orElseThrow();
        if ("OK".equals(metric.status())) {
            report.append("- ").append(metric.metricName()).append("：").append(metric.displayValue())
                    .append("（公式：").append(metric.formula()).append("；证据字段：")
                    .append(String.join(", ", metric.evidenceRefs())).append("）。\n");
        } else {
            report.append("- ").append(metric.metricName()).append("：数据缺失（")
                    .append(metric.reason()).append("）。\n");
        }
        report.append("\n");
    }

    private String evidenceSentence(FinancialSnapshot snapshot, String metricName, String fallback) {
        StringJoiner joiner = new StringJoiner("\n");
        snapshot.evidenceItems().stream()
                .filter(FinancialEvidenceItem::effective)
                .filter(item -> metricName.equals(item.metricName()))
                .limit(2)
                .forEach(item -> joiner.add("- " + item.excerpt()));
        String value = joiner.toString();
        return value.isBlank() ? fallback : value;
    }

    private String blankToDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }
}
