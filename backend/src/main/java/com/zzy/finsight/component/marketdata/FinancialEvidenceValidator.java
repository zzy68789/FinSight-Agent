package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceIssueCodes;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialSnapshot;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

/**
 * 校验金融证据的报告期、重复项、财务关系和公开内容质量。
 */
@Component
public class FinancialEvidenceValidator {
    public static final String POLICY_VERSION = "financial-evidence-policy-v1";
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.BASIC_ISO_DATE;

    private final Clock clock;

    public FinancialEvidenceValidator() {
        this(Clock.systemDefaultZone());
    }

    FinancialEvidenceValidator(Clock clock) {
        this.clock = clock;
    }

    /** 返回写入问题编码后的不可变金融快照。 */
    public FinancialSnapshot validate(FinancialSnapshot snapshot) {
        if (snapshot == null) {
            throw new IllegalArgumentException("金融快照不能为空");
        }
        List<FinancialEvidenceItem> items = markBasicIssues(snapshot.evidenceItems());
        items = markPriorPeriodMismatch(items);
        items = markInvalidFinancialRelation(items);
        return new FinancialSnapshot(
                snapshot.subject(),
                snapshot.reportPeriod(),
                snapshot.searchMode(),
                items,
                snapshot.stageResults(),
                snapshot.retrievalResults(),
                snapshot.createdAt()
        );
    }

    /** 标记重复、未来报告期和明显无效的网页摘要。 */
    private List<FinancialEvidenceItem> markBasicIssues(List<FinancialEvidenceItem> sourceItems) {
        List<FinancialEvidenceItem> items = new ArrayList<>();
        Set<String> evidenceKeys = new HashSet<>();
        for (FinancialEvidenceItem item : sourceItems) {
            if (!item.effective()) {
                items.add(item);
                continue;
            }
            String issueCode = "";
            String evidenceKey = evidenceKey(item);
            if (!evidenceKeys.add(evidenceKey)) {
                issueCode = FinancialEvidenceIssueCodes.DUPLICATE_PERIOD;
            } else if (isFuturePeriod(item.reportPeriod())) {
                issueCode = FinancialEvidenceIssueCodes.FUTURE_DATA;
            } else if (isLowQualityNews(item)) {
                issueCode = FinancialEvidenceIssueCodes.LOW_QUALITY_CONTENT;
            }
            items.add(issueCode.isBlank() ? item : withIssue(item, issueCode));
        }
        return List.copyOf(items);
    }

    /** 标记没有与本期严格相差一年的营收同期证据。 */
    private List<FinancialEvidenceItem> markPriorPeriodMismatch(List<FinancialEvidenceItem> sourceItems) {
        List<FinancialEvidenceItem> items = new ArrayList<>(sourceItems);
        for (int index = 0; index < items.size(); index++) {
            FinancialEvidenceItem prior = items.get(index);
            if (!prior.effective()
                    || !FinancialMetricInputNames.OPERATING_REVENUE_PRIOR.equals(prior.metricName())) {
                continue;
            }
            Optional<FinancialEvidenceItem> current = items.stream()
                    .filter(FinancialEvidenceItem::effective)
                    .filter(item -> sameSource(prior, item))
                    .filter(item -> FinancialMetricInputNames.OPERATING_REVENUE.equals(item.metricName()))
                    .max((left, right) -> safe(left.reportPeriod()).compareTo(safe(right.reportPeriod())));
            if (current.isEmpty()) {
                continue;
            }
            LocalDate currentDate = parsePeriod(current.orElseThrow().reportPeriod());
            LocalDate priorDate = parsePeriod(prior.reportPeriod());
            if (currentDate != null && priorDate != null && !currentDate.minusYears(1).equals(priorDate)) {
                items.set(index, withIssue(prior, FinancialEvidenceIssueCodes.PRIOR_PERIOD_MISMATCH));
            }
        }
        return List.copyOf(items);
    }

    /** 标记同源同报告期内总负债大于总资产的异常关系。 */
    private List<FinancialEvidenceItem> markInvalidFinancialRelation(List<FinancialEvidenceItem> sourceItems) {
        List<FinancialEvidenceItem> items = new ArrayList<>(sourceItems);
        for (int liabilityIndex = 0; liabilityIndex < items.size(); liabilityIndex++) {
            FinancialEvidenceItem liabilities = items.get(liabilityIndex);
            if (!liabilities.effective()
                    || !FinancialMetricInputNames.TOTAL_LIABILITIES.equals(liabilities.metricName())
                    || liabilities.normalizedValue() == null) {
                continue;
            }
            for (int assetIndex = 0; assetIndex < items.size(); assetIndex++) {
                FinancialEvidenceItem assets = items.get(assetIndex);
                if (!assets.effective()
                        || !FinancialMetricInputNames.TOTAL_ASSETS.equals(assets.metricName())
                        || assets.normalizedValue() == null
                        || !sameSourceAndPeriod(liabilities, assets)) {
                    continue;
                }
                if (liabilities.normalizedValue().compareTo(assets.normalizedValue()) > 0) {
                    items.set(liabilityIndex, withIssue(liabilities, FinancialEvidenceIssueCodes.INVALID_FINANCIAL_RELATION));
                    items.set(assetIndex, withIssue(assets, FinancialEvidenceIssueCodes.INVALID_FINANCIAL_RELATION));
                }
                break;
            }
        }
        return List.copyOf(items);
    }

    private boolean isFuturePeriod(String period) {
        LocalDate date = parsePeriod(period);
        return date != null && date.isAfter(LocalDate.now(clock));
    }

    /** 识别搜索导航、登录页和动态占位符等非正文内容。 */
    private boolean isLowQualityNews(FinancialEvidenceItem item) {
        if (!"NEWS_SUMMARY".equals(item.metricName())) {
            return false;
        }
        String excerpt = safe(item.excerpt()).toLowerCase(Locale.ROOT);
        String compact = excerpt.replaceAll("\\s+", "");
        int navigationHits = countContaining(excerpt, List.of(
                "行情首页", "财务分析指标", "业绩预告", "业绩快报", "申购状况",
                "大宗交易", "融资融券", "股东统计", "基金持仓", "分红派息"
        ));
        int quotePageHits = countContaining(excerpt, List.of(
                "最新实时行情", "历史走势图", "未来股价预测", "分析师评级", "股票价格", "股票行情"
        ));
        int markdownLinkCount = countOccurrences(excerpt, "](");
        return excerpt.isBlank()
                || ("PUBLIC_MARKET".equals(item.sourceType()) && compact.length() < 60)
                || excerpt.contains("查看「」的全部搜索结果")
                || excerpt.contains("@open@")
                || excerpt.contains("@volume@")
                || (excerpt.contains("[登录]") && excerpt.contains("公司简介"))
                || navigationHits >= 4
                || quotePageHits >= 4
                || (markdownLinkCount >= 6 && markdownLinkCount * 25 > compact.length())
                || (excerpt.contains("概览") && excerpt.contains("行情") && excerpt.contains("财报全文"));
    }

    private String evidenceKey(FinancialEvidenceItem item) {
        if ("NEWS_SUMMARY".equals(item.metricName()) && item.url() != null && !item.url().isBlank()) {
            return "url|" + item.url().trim().toLowerCase(Locale.ROOT);
        }
        return String.join("|",
                safe(item.sourceType()),
                safe(item.sourceName()),
                safe(item.reportPeriod()),
                safe(item.metricName()));
    }

    private int countContaining(String text, List<String> tokens) {
        int hits = 0;
        for (String token : tokens) {
            if (text.contains(token.toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    private int countOccurrences(String text, String token) {
        int count = 0;
        int offset = 0;
        while ((offset = text.indexOf(token, offset)) >= 0) {
            count++;
            offset += token.length();
        }
        return count;
    }

    private LocalDate parsePeriod(String period) {
        if (period == null || !period.matches("\\d{8}")) {
            return null;
        }
        try {
            return LocalDate.parse(period, BASIC_DATE);
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean sameSource(FinancialEvidenceItem left, FinancialEvidenceItem right) {
        return safe(left.sourceType()).equals(safe(right.sourceType()))
                && safe(left.sourceName()).equals(safe(right.sourceName()));
    }

    private boolean sameSourceAndPeriod(FinancialEvidenceItem left, FinancialEvidenceItem right) {
        return sameSource(left, right)
                && safe(left.reportPeriod()).equals(safe(right.reportPeriod()));
    }

    private FinancialEvidenceItem withIssue(FinancialEvidenceItem item, String issueCode) {
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
                issueCode
        );
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
