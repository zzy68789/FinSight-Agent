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
        if (subject.isEtf()) {
            return writeEtfReport(snapshot, metrics, riskAssessment, previousReview);
        }
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

    private String writeEtfReport(
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment,
            CitationReviewResult previousReview
    ) {
        StockSubject subject = snapshot.subject();
        StringBuilder report = new StringBuilder();
        report.append("# ").append(subject.fullCode()).append(" ETF研究报告\n\n");
        report.append("> 仅作研究辅助，不构成投资建议。ETF 报告基于本次数据快照生成，缺失的净值、持仓、规模或流动性数据不会由模型补写。\n\n");
        if (previousReview != null && "FAIL".equals(previousReview.status())) {
            report.append("> 上轮审查反馈：").append(previousReview.reason()).append("\n\n");
        }

        report.append("## 1. 基金概况\n\n");
        report.append("- 基金代码：").append(subject.fullCode()).append("\n");
        report.append("- 资产类型：ETF\n");
        report.append("- 识别名称：").append(subject.companyName()).append("\n");
        report.append("- 报告期口径：").append(snapshot.reportPeriod()).append("\n\n");

        report.append("## 2. 跟踪标的与产品信息\n\n");
        report.append(evidenceSentence(snapshot, "LOCAL_CONTEXT", "当前公开资料或上传材料不足以稳定识别跟踪指数、基金管理人、费率和持仓结构，需结合基金招募说明书或定期报告复核。")).append("\n\n");

        report.append("## 3. 二级市场行情\n\n");
        appendOptionalMetric(report, metrics, "ETF收盘价");
        appendOptionalMetric(report, metrics, "ETF涨跌幅");
        appendOptionalMetric(report, metrics, "ETF成交额");

        report.append("## 4. 流动性与交易观察\n\n");
        report.append(evidenceSentence(snapshot, FinancialMetricInputNames.ETF_AMOUNT, "暂未取得可复核成交额或流动性数据，不能判断交易活跃度。")).append("\n\n");

        report.append("## 5. 净值、规模与持仓缺口\n\n");
        report.append("- 当前 MVP 优先接入 ETF 二级市场行情；基金净值、规模、持仓、跟踪误差和申赎清单仍需后续数据源补齐。\n");
        report.append("- 若数据快照中存在 `DATA_MISSING` 或 `MISSING_INPUT`，应优先补齐基金定期报告、净值和持仓数据后再复核。\n\n");

        report.append("## 6. 新闻与催化因素\n\n");
        report.append(evidenceSentence(snapshot, "NEWS_SUMMARY", "未取得足够 ETF 新闻、指数或行业催化摘要，暂不输出方向性判断。")).append("\n\n");

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
            report.append("- 数据风险：净值、持仓、规模或流动性证据不足时，本报告只能作为流程回放样例。\n");
            report.append("- 跟踪风险：ETF 可能存在跟踪误差、折溢价、流动性和标的指数波动风险。\n");
        }
        report.append("\n");

        report.append("## 8. 结论与后续观察点\n\n");
        report.append("- 结论：优先复核 ETF 净值、持仓、规模、成交额和跟踪误差数据，不输出买卖建议。\n");
        report.append("- 后续观察：补齐基金定期报告、行情序列和指数信息后，重新运行本工作流。\n\n");

        appendCitationSection(report, snapshot, metrics, riskAssessment);
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

    private void appendOptionalMetric(StringBuilder report, List<FinancialMetricResult> metrics, String metricName) {
        FinancialMetricResult metric = metrics.stream()
                .filter(item -> metricName.equals(item.metricName()))
                .findFirst()
                .orElse(null);
        if (metric == null || !"OK".equals(metric.status())) {
            String reason = metric == null ? "缺少输入：" + metricName : metric.reason();
            report.append("- ").append(metricName).append("：数据缺失（").append(reason).append("）。\n\n");
            return;
        }
        report.append("- ").append(metric.metricName()).append("：").append(metric.displayValue())
                .append("（口径：").append(metric.formula()).append("；证据字段：")
                .append(String.join(", ", metric.evidenceRefs())).append("）。\n\n");
    }

    private void appendCitationSection(
            StringBuilder report,
            FinancialSnapshot snapshot,
            List<FinancialMetricResult> metrics,
            FinancialRiskAssessment riskAssessment
    ) {
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
