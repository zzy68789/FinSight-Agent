package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 检查当前研究计划的证据覆盖率和关键缺口。
 */
@Component
public class CheckEvidenceCoverageTool implements ResearchTool<NoToolArguments> {
    @Override
    public String name() {
        return "check_evidence_coverage";
    }

    @Override
    public String description() {
        return "检查当前计划是否已有可引用证据、确定性指标和风险结论，并返回缺失项。";
    }

    @Override
    public ToolDefinition definition() {
        return ToolDefinition.readOnly(
                "check_evidence_coverage",
                "检查当前计划是否已有可引用证据、确定性指标和风险结论，并返回缺失项。",
                Map.of(
                        "coverage", "decimal",
                        "effectiveEvidenceCount", "integer",
                        "missing", "string[]",
                        "ready", "boolean"
                )
        );
    }

    @Override
    public NoToolArguments decode(Map<String, Object> arguments, ObjectMapper objectMapper) {
        return ToolDecoders.noArguments(arguments);
    }

    @Override
    public ToolResult execute(ToolContext context, NoToolArguments arguments) {
        List<FinancialEvidenceItem> evidence = context.snapshot() == null
                ? List.of() : context.snapshot().evidenceItems();
        long effective = evidence.stream().filter(FinancialEvidenceItem::effective).count();
        List<String> missing = new ArrayList<>();
        if (context.subject() == null) {
            missing.add("证券主体");
        }
        if (effective < 3) {
            missing.add("至少 3 条有效证据");
        }
        if (context.metrics().isEmpty()) {
            missing.add("确定性金融指标");
        }
        if (context.riskAssessment() == null) {
            missing.add("研究风险评估");
        }
        int totalChecks = 4;
        BigDecimal coverage = BigDecimal.valueOf(totalChecks - missing.size())
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(totalChecks), 2, RoundingMode.HALF_UP);
        String summary = missing.isEmpty()
                ? "证据覆盖检查通过"
                : "证据覆盖不足：" + String.join("、", missing);
        return ToolResult.success(summary, new ToolPayload.Coverage(
                coverage, effective, missing, missing.isEmpty()
        ));
    }
}
