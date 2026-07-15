package com.zzy.finsight.component.review;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialMetricResult;
import com.zzy.finsight.domain.stock.FinancialRiskAssessment;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import com.zzy.finsight.component.analysis.FinancialRiskScorer;


import com.zzy.finsight.llm.LlmClient;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class InvestmentReportWriterTest {
    private final InvestmentReportWriter writer = new InvestmentReportWriter((prompt, modelType) -> {
        throw new IllegalStateException("测试环境未配置 LLM");
    });

    @Test
    void usesSmartLlmAndKeepsDeterministicCitationAppendix() {
        AtomicReference<String> capturedPrompt = new AtomicReference<>();
        AtomicReference<LlmClient.ModelType> capturedModel = new AtomicReference<>();
        InvestmentReportWriter llmWriter = new InvestmentReportWriter((prompt, modelType) -> {
            capturedPrompt.set(prompt);
            capturedModel.set(modelType);
            return """
                    # 588200.SH ETF研究报告

                    ## 1. 基金概况
                    LLM 生成的基金概况。
                    ## 2. 跟踪标的与产品信息
                    LLM 生成的产品分析。
                    ## 3. 二级市场行情
                    ETF收盘价为 1.234。
                    ## 4. 流动性与交易观察
                    流动性需要持续复核。
                    ## 5. 净值、规模与持仓缺口
                    当前证据仍有缺口。
                    ## 6. 新闻与催化因素
                    暂不输出方向性判断。
                    ## 7. 主要风险
                    关注跟踪误差和流动性风险。
                    ## 8. 结论与后续观察点
                    补齐证据后继续观察。后续应持续核对基金定期报告、指数资料和交易所公开信息，所有判断必须回到证据原文复核。当前信息不足以形成方向性判断，也不能据此给出买卖或仓位建议。
                    """;
        });
        FinancialSnapshot snapshot = etfSnapshot();

        String report = llmWriter.write(snapshot, etfMetrics(), risk(snapshot), null);

        assertThat(capturedModel.get()).isEqualTo(LlmClient.ModelType.SMART);
        assertThat(capturedPrompt.get()).contains("588200.SH", "只允许使用给定事实", "确定性报告草稿");
        assertThat(capturedPrompt.get())
                .contains(
                        "最终引用附录由系统确定性覆盖",
                        "八章节正文总长度不超过 2500 个中文字符",
                        "可用证据索引",
                        "[E1] ETF_CLOSE"
                )
                .doesNotContain("[E1] AUTHORIZED_MARKET / TuShare Pro", "### 指标计算公式", "### 风险评分明细");
        assertThat(report).contains("LLM 生成的基金概况");
        assertThat(report).contains("仅作研究辅助，不构成投资建议");
        assertThat(report).contains("<!-- FinSight generation-mode: llm -->");
        assertThat(report).contains("[E1] AUTHORIZED_MARKET / TuShare Pro");
    }

    @Test
    void fallsBackToDeterministicTemplateWhenLlmFails() {
        InvestmentReportWriter timeoutWriter = new InvestmentReportWriter((prompt, modelType) -> {
            throw new IllegalStateException("request timed out");
        });
        FinancialSnapshot snapshot = etfSnapshot();

        String report = timeoutWriter.write(snapshot, etfMetrics(), risk(snapshot), null);

        assertThat(report).contains("<!-- FinSight generation-mode: template-fallback -->");
        assertThat(report).contains("<!-- FinSight fallback-reason: LLM_TIMEOUT -->");
        assertThat(InvestmentReportWriter.generationMode(report)).isEqualTo("template-fallback");
        assertThat(InvestmentReportWriter.fallbackReason(report)).isEqualTo("LLM_TIMEOUT");
        assertThat(report).contains("588200.SH ETF研究报告");
    }

    @Test
    void recordsInvalidStructureAsFallbackReason() {
        InvestmentReportWriter invalidWriter = new InvestmentReportWriter((prompt, modelType) -> "内容过短");
        FinancialSnapshot snapshot = etfSnapshot();

        String report = invalidWriter.write(snapshot, etfMetrics(), risk(snapshot), null);

        assertThat(report).contains("<!-- FinSight fallback-reason: LLM_INVALID_STRUCTURE -->");
    }

    @Test
    void writesEtfReportWithoutCompanyFinancialSections() {
        FinancialSnapshot snapshot = etfSnapshot();

        String report = writer.write(snapshot, etfMetrics(), risk(snapshot), null);

        assertThat(report).contains("588200.SH ETF研究报告");
        assertThat(report).contains("基金代码：588200.SH");
        assertThat(report).contains("ETF收盘价");
        assertThat(report).doesNotContain("A股投研报告");
        assertThat(report).doesNotContain("ROE");
        assertThat(report).doesNotContain("毛利率");
    }

    @Test
    void marksInterimRoeAsUnannualizedAndAddsDeterministicCitations() {
        FinancialSnapshot snapshot = aShareSnapshot();
        List<FinancialMetricResult> metrics = List.of(
                metric("营收同比", "6.54%", List.of(
                        FinancialMetricInputNames.OPERATING_REVENUE,
                        FinancialMetricInputNames.OPERATING_REVENUE_PRIOR
                )),
                metric("净利率", "50.53%", List.of(
                        FinancialMetricInputNames.NET_PROFIT,
                        FinancialMetricInputNames.OPERATING_REVENUE
                )),
                metric("毛利率", "89.76%", List.of(
                        FinancialMetricInputNames.OPERATING_REVENUE,
                        FinancialMetricInputNames.OPERATING_COST
                )),
                metric("ROE", "10.57%", List.of(
                        FinancialMetricInputNames.NET_PROFIT,
                        FinancialMetricInputNames.BEGINNING_EQUITY,
                        FinancialMetricInputNames.ENDING_EQUITY
                )),
                metric("经营现金流 / 净利润", "0.99", List.of(
                        FinancialMetricInputNames.OPERATING_CASH_FLOW,
                        FinancialMetricInputNames.NET_PROFIT
                )),
                metric("资产负债率", "12.12%", List.of(
                        FinancialMetricInputNames.TOTAL_LIABILITIES,
                        FinancialMetricInputNames.TOTAL_ASSETS
                ))
        );

        String report = writer.write(snapshot, metrics, new FinancialRiskScorer().assess(metrics, snapshot.evidenceItems()), null);

        assertThat(report).contains("2026年一季度口径；ROE未年化");
        assertThat(report).contains("ROE：10.57%（2026年一季度口径，未年化");
        assertThat(report).contains("[E1]", "[E2]", "本次结构化财务指标未发现");
    }

    private FinancialSnapshot etfSnapshot() {
        return new FinancialSnapshot(
                new StockSubject("588200", "SH", "588200.SH", "待识别ETF", "ETF", StockAssetType.ETF),
                "latest",
                "hybrid",
                List.of(
                        evidence(FinancialMetricInputNames.ETF_CLOSE, "ETF收盘价 1.234"),
                        evidence(FinancialMetricInputNames.ETF_PCT_CHANGE, "ETF涨跌幅 2.15%"),
                        evidence(FinancialMetricInputNames.ETF_AMOUNT, "ETF成交额 35000")
                ),
                LocalDateTime.of(2026, 7, 6, 10, 0)
        );
    }

    private List<FinancialMetricResult> etfMetrics() {
        return List.of(
                metric("ETF收盘价", "1.234"),
                metric("ETF涨跌幅", "2.15%"),
                metric("ETF成交额", "35000")
        );
    }

    private FinancialSnapshot aShareSnapshot() {
        return new FinancialSnapshot(
                new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料"),
                "20260331",
                "hybrid",
                List.of(
                        evidence(FinancialMetricInputNames.OPERATING_REVENUE, "20260331", "营业收入 539.09 亿元"),
                        evidence(FinancialMetricInputNames.OPERATING_REVENUE_PRIOR, "20250331", "上年同期营业收入 506.01 亿元"),
                        evidence(FinancialMetricInputNames.OPERATING_COST, "20260331", "营业成本 55.21 亿元"),
                        evidence(FinancialMetricInputNames.NET_PROFIT, "20260331", "归母净利润 272.43 亿元"),
                        evidence(FinancialMetricInputNames.BEGINNING_EQUITY, "20251231", "年初归母权益 2446.38 亿元"),
                        evidence(FinancialMetricInputNames.ENDING_EQUITY, "20260331", "期末归母权益 2708.94 亿元"),
                        evidence(FinancialMetricInputNames.OPERATING_CASH_FLOW, "20260331", "经营现金流 269.10 亿元"),
                        evidence(FinancialMetricInputNames.TOTAL_ASSETS, "20260331", "总资产 3199.19 亿元"),
                        evidence(FinancialMetricInputNames.TOTAL_LIABILITIES, "20260331", "总负债 387.83 亿元")
                ),
                LocalDateTime.of(2026, 7, 15, 10, 0)
        );
    }

    private FinancialRiskAssessment risk(FinancialSnapshot snapshot) {
        return new FinancialRiskScorer().assess(List.of(), snapshot.evidenceItems());
    }

    private FinancialMetricResult metric(String name, String displayValue) {
        return new FinancialMetricResult(name, null, displayValue, name + "来自ETF行情证据", "OK", "", List.of(name));
    }

    private FinancialMetricResult metric(String name, String displayValue, List<String> evidenceRefs) {
        return new FinancialMetricResult(
                name,
                new BigDecimal(displayValue.replace("%", "")),
                displayValue,
                name + "确定性公式",
                "OK",
                "",
                evidenceRefs
        );
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return evidence(metricName, "latest", excerpt);
    }

    private FinancialEvidenceItem evidence(String metricName, String period, String excerpt) {
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                "https://tushare.pro",
                null,
                period,
                metricName,
                BigDecimal.ONE,
                BigDecimal.ONE,
                excerpt,
                new BigDecimal("0.90"),
                LocalDateTime.of(2026, 7, 6, 10, 0),
                ""
        );
    }
}
