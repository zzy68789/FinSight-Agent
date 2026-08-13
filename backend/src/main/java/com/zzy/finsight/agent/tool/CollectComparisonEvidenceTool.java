package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.component.analysis.StockCodeResolver;
import com.zzy.finsight.domain.stock.ComparisonSecuritySnapshot;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.infrastructure.provider.AShareMasterDataProvider;
import com.zzy.finsight.infrastructure.provider.FinancialDataProvider;
import com.zzy.finsight.infrastructure.provider.TushareMarketDataProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 为已确认的可比证券采集只读结构化证据并生成独立快照标识。
 */
@Component
public class CollectComparisonEvidenceTool implements ResearchTool<NoToolArguments> {
    public static final String TOOL_NAME = "collect_comparison_evidence";
    private final StockCodeResolver stockCodeResolver;
    private final List<FinancialDataProvider> providers;

    @Autowired
    public CollectComparisonEvidenceTool(
            StockCodeResolver stockCodeResolver,
            AShareMasterDataProvider masterDataProvider,
            TushareMarketDataProvider marketDataProvider
    ) {
        this(stockCodeResolver, List.of(masterDataProvider, marketDataProvider));
    }

    /** 供不启动 Spring 容器的工具契约测试注入固定 Provider。 */
    CollectComparisonEvidenceTool(
            StockCodeResolver stockCodeResolver,
            List<FinancialDataProvider> providers
    ) {
        this.stockCodeResolver = stockCodeResolver;
        this.providers = providers == null ? List.of() : List.copyOf(providers);
    }

    @Override
    public String name() {
        return TOOL_NAME;
    }

    @Override
    public String description() {
        return "采集请求中最多三个可比证券的主档、财务和估值证据；每条证据标注所属证券与独立快照标识。";
    }

    @Override
    public ToolDefinition definition() {
        return new ToolDefinition(
                TOOL_NAME,
                description(),
                false,
                false,
                true,
                true,
                List.of(),
                Map.of(
                        "evidence", "FinancialEvidenceItem[]",
                        "snapshots", "ComparisonSecuritySnapshot[]"
                )
        );
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
            return ToolResult.failure("尚未解析主证券主体", "SUBJECT_REQUIRED", false);
        }
        List<String> comparisonTickers = context.request().comparisonTickers();
        if (comparisonTickers.isEmpty()) {
            return ToolResult.success("本次请求未配置可比证券", new ToolPayload.ComparisonEvidence(List.of(), List.of()));
        }
        String reportPeriod = context.request().asOfDate().format(DateTimeFormatter.BASIC_ISO_DATE);
        List<FinancialEvidenceItem> allEvidence = new ArrayList<>();
        List<ComparisonSecuritySnapshot> snapshots = new ArrayList<>();
        for (String ticker : comparisonTickers) {
            StockSubject subject = stockCodeResolver.resolve(ticker);
            List<FinancialEvidenceItem> collected = collectSubject(
                    context.ownerId(), subject, reportPeriod, context.request().searchMode()
            );
            String snapshotId = snapshotId(subject, reportPeriod, collected);
            List<FinancialEvidenceItem> tagged = collected.stream()
                    .map(item -> tag(item, subject.fullCode(), snapshotId))
                    .toList();
            long effectiveNumeric = tagged.stream()
                    .filter(FinancialEvidenceItem::effective)
                    .filter(item -> item.normalizedValue() != null)
                    .count();
            String status = effectiveNumeric > 0 ? "SUCCESS" : FinancialEvidenceIssueCodes.DATA_MISSING;
            snapshots.add(new ComparisonSecuritySnapshot(
                    snapshotId,
                    subject,
                    reportPeriod,
                    tagged.size(),
                    tagged.stream().filter(FinancialEvidenceItem::effective).count(),
                    status
            ));
            allEvidence.addAll(tagged);
        }
        long missing = snapshots.stream()
                .filter(item -> FinancialEvidenceIssueCodes.DATA_MISSING.equals(item.status()))
                .count();
        String summary = "已采集 %d 个可比证券、%d 条归属明确的证据%s".formatted(
                snapshots.size(),
                allEvidence.size(),
                missing == 0 ? "" : "；其中 " + missing + " 个标记 DATA_MISSING"
        );
        return ToolResult.success(summary, new ToolPayload.ComparisonEvidence(allEvidence, snapshots));
    }

    private List<FinancialEvidenceItem> collectSubject(
            long ownerId,
            StockSubject subject,
            String reportPeriod,
            String searchMode
    ) {
        List<FinancialEvidenceItem> evidence = new ArrayList<>();
        for (FinancialDataProvider provider : providers) {
            try {
                FinancialDataCollection collection = provider.collectWithTrace(
                        ownerId, subject, reportPeriod, searchMode
                );
                if (collection != null) {
                    evidence.addAll(collection.evidenceItems());
                }
            } catch (RuntimeException exception) {
                evidence.add(dataMissing(subject, reportPeriod, provider.name()));
            }
        }
        if (evidence.isEmpty()) {
            evidence.add(dataMissing(subject, reportPeriod, "可比证券数据源"));
        }
        return List.copyOf(evidence);
    }

    private FinancialEvidenceItem dataMissing(StockSubject subject, String reportPeriod, String provider) {
        return new FinancialEvidenceItem(
                "COMPARISON_PROVIDER",
                provider,
                "",
                null,
                reportPeriod,
                "COMPARISON_DATA",
                null,
                null,
                subject.fullCode() + " 未取得完整可比数据，报告必须标记 DATA_MISSING。",
                BigDecimal.ZERO,
                LocalDateTime.now(),
                FinancialEvidenceIssueCodes.DATA_MISSING
        );
    }

    private FinancialEvidenceItem tag(FinancialEvidenceItem item, String subjectCode, String snapshotId) {
        return new FinancialEvidenceItem(
                item.sourceType(),
                item.sourceName(),
                item.url(),
                item.pageNumber(),
                item.reportPeriod(),
                item.metricName(),
                item.rawValue(),
                item.normalizedValue(),
                item.excerpt(),
                item.confidence(),
                item.asOf(),
                item.issueCode(),
                subjectCode,
                snapshotId
        );
    }

    private String snapshotId(
            StockSubject subject,
            String reportPeriod,
            List<FinancialEvidenceItem> evidence
    ) {
        String canonicalEvidence = evidence.stream()
                .map(item -> String.join("|",
                        safe(item.sourceType()),
                        safe(item.sourceName()),
                        safe(item.metricName()),
                        safe(item.reportPeriod()),
                        decimal(item.normalizedValue()),
                        safe(item.issueCode())
                ))
                .sorted(Comparator.naturalOrder())
                .reduce("", (left, right) -> left + "\n" + right);
        String canonical = subject.fullCode() + "|" + reportPeriod + "|" + canonicalEvidence;
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(canonical.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", exception);
        }
    }

    private String decimal(BigDecimal value) {
        return value == null ? "" : value.stripTrailingZeros().toPlainString();
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }
}
