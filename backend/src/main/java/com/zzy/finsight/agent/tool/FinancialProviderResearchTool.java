package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.infrastructure.provider.FinancialDataProvider;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 将单个金融数据 Provider 暴露为稳定、只读的 Agent 工具。
 */
public class FinancialProviderResearchTool implements ResearchTool<NoToolArguments> {
    private final String name;
    private final String description;
    private final FinancialDataProvider provider;

    public FinancialProviderResearchTool(
            String name,
            String description,
            FinancialDataProvider provider
    ) {
        this.name = name;
        this.description = description;
        this.provider = provider;
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public String description() {
        return description;
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                name,
                description,
                true,
                true,
                true,
                true,
                List.of(),
                Map.of(
                        "provider", "string",
                        "evidence", "FinancialEvidenceItem[]",
                        "evidenceCount", "integer",
                        "effectiveCount", "integer",
                        "marketSeries", "MarketDataPoint[]",
                        "etfDeepData", "object"
                )
        );
    }

    @Override
    public boolean allowParallel() {
        return true;
    }

    @Override
    public boolean producesEvidence() {
        return true;
    }

    @Override
    public NoToolArguments decode(Map<String, Object> arguments, ObjectMapper objectMapper) {
        return ToolDecoders.noArguments(arguments);
    }

    @Override
    public ToolResult execute(ToolContext context, NoToolArguments arguments) {
        if (context.subject() == null) {
            return ToolResult.failure("尚未解析证券主体", "SUBJECT_REQUIRED", false);
        }
        long startedAt = System.nanoTime();
        String reportPeriod = context.request().asOfDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        FinancialDataCollection collection = provider.collectWithTrace(
                context.ownerId(),
                context.subject(),
                reportPeriod,
                context.request().searchMode()
        );
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        List<FinancialEvidenceItem> evidence = collection == null ? List.of() : collection.evidenceItems();
        long effective = evidence.stream().filter(FinancialEvidenceItem::effective).count();
        return new ToolResult(
                "SUCCESS",
                "%s 返回 %d 条待归约证据，其中有效 %d 条".formatted(name, evidence.size(), effective),
                new ToolPayload.Evidence(
                        provider.name(),
                        "",
                        evidence,
                        collection == null ? null : collection.retrievalResult(),
                        collection == null ? List.of() : collection.marketSeries(),
                        collection == null ? null : collection.etfDeepData(),
                        evidence.size(),
                        effective
                ),
                List.of(),
                "",
                false
        );
    }
}
