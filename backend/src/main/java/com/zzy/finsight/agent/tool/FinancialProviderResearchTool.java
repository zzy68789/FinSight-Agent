package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.EvidenceMemory;
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
    private final EvidenceMemory evidenceMemory;

    public FinancialProviderResearchTool(
            String name,
            String description,
            FinancialDataProvider provider,
            EvidenceMemory evidenceMemory
    ) {
        this.name = name;
        this.description = description;
        this.provider = provider;
        this.evidenceMemory = evidenceMemory;
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
        if (context.state().getSubject() == null) {
            return ToolResult.failure("尚未解析证券主体", "SUBJECT_REQUIRED", false);
        }
        long startedAt = System.nanoTime();
        String reportPeriod = context.request().getAsOfDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        FinancialDataCollection collection = provider.collectWithTrace(
                context.ownerId(),
                context.state().getSubject(),
                reportPeriod,
                context.request().getSearchMode()
        );
        long durationMs = Math.max(0L, (System.nanoTime() - startedAt) / 1_000_000L);
        List<FinancialEvidenceItem> added = evidenceMemory.merge(
                context.state(), name, collection, durationMs, "SUCCESS", ""
        );
        long effective = added.stream().filter(FinancialEvidenceItem::effective).count();
        return new ToolResult(
                "SUCCESS",
                "%s 新增 %d 条证据，其中有效 %d 条".formatted(name, added.size(), effective),
                Map.of(
                        "provider", provider.name(),
                        "evidence", added,
                        "evidenceCount", added.size(),
                        "effectiveCount", effective,
                        "marketSeries", collection == null ? List.of() : collection.marketSeries(),
                        "etfDeepData", collection == null || collection.etfDeepData() == null
                                ? Map.of() : collection.etfDeepData()
                ),
                added,
                "",
                false
        );
    }
}
