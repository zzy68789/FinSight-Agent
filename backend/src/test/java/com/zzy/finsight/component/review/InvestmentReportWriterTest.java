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

                    > 仅作研究辅助，不构成投资建议。

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
                    ## 引用与数据快照
                    - [E1] LLM 不应控制最终引用附录。
                    """;
        });
        FinancialSnapshot snapshot = etfSnapshot();

        String report = llmWriter.write(snapshot, etfMetrics(), risk(snapshot), null);

        assertThat(capturedModel.get()).isEqualTo(LlmClient.ModelType.SMART);
        assertThat(capturedPrompt.get()).contains("588200.SH", "只允许使用给定事实", "确定性报告草稿");
        assertThat(report).contains("LLM 生成的基金概况");
        assertThat(report).contains("<!-- FinSight generation-mode: llm -->");
        assertThat(report).contains("[E1] AUTHORIZED_MARKET / TuShare Pro");
        assertThat(report).doesNotContain("LLM 不应控制最终引用附录");
    }

    @Test
    void fallsBackToDeterministicTemplateWhenLlmFails() {
        FinancialSnapshot snapshot = etfSnapshot();

        String report = writer.write(snapshot, etfMetrics(), risk(snapshot), null);

        assertThat(report).contains("<!-- FinSight generation-mode: template-fallback -->");
        assertThat(report).contains("588200.SH ETF研究报告");
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

    private FinancialRiskAssessment risk(FinancialSnapshot snapshot) {
        return new FinancialRiskScorer().assess(List.of(), snapshot.evidenceItems());
    }

    private FinancialMetricResult metric(String name, String displayValue) {
        return new FinancialMetricResult(name, null, displayValue, name + "来自ETF行情证据", "OK", "", List.of(name));
    }

    private FinancialEvidenceItem evidence(String metricName, String excerpt) {
        return new FinancialEvidenceItem(
                "AUTHORIZED_MARKET",
                "TuShare Pro",
                "https://tushare.pro",
                null,
                "latest",
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
