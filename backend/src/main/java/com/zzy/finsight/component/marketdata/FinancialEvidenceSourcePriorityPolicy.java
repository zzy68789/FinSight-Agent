package com.zzy.finsight.component.marketdata;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;
import java.util.Locale;

/**
 * 以固定来源层级为金融证据分配可审计的优先级。
 */
@Component
public class FinancialEvidenceSourcePriorityPolicy {
    public static final String POLICY_VERSION = "financial-source-priority-v1";
    private static final List<String> OFFICIAL_DISCLOSURE_DOMAINS = List.of(
            "sse.com.cn",
            "szse.cn",
            "bse.cn",
            "cninfo.com.cn",
            "csrc.gov.cn"
    );

    /** 返回来源优先级，数值越大越应优先作为确定性指标输入。 */
    public int priority(FinancialEvidenceItem item) {
        if (item == null) {
            return 0;
        }
        if (isOfficialDisclosure(item)) {
            return 110;
        }
        return switch (safe(item.sourceType())) {
            case "AUTHORIZED_MARKET" -> 100;
            case "FINANCIAL_REPORT" -> 90;
            case "UPLOADED_REPORT" -> 80;
            case "PUBLIC_MARKET" -> 60;
            case "PUBLIC_RESEARCH" -> 50;
            case "LOCAL_CONTEXT" -> 30;
            case "DATA_PROVIDER" -> 0;
            default -> 10;
        };
    }

    /** 说明来源被归入的优先级层级。 */
    public String describe(FinancialEvidenceItem item) {
        if (isOfficialDisclosure(item)) {
            return "交易所、法定披露平台或监管机构原文";
        }
        return switch (safe(item == null ? null : item.sourceType())) {
            case "AUTHORIZED_MARKET" -> "授权结构化行情与财务数据";
            case "FINANCIAL_REPORT" -> "结构化财务报告";
            case "UPLOADED_REPORT" -> "用户上传报告解析结果";
            case "PUBLIC_MARKET" -> "公开行情或新闻正文";
            case "PUBLIC_RESEARCH" -> "公开研究检索结果";
            case "LOCAL_CONTEXT" -> "本地主档或上下文";
            case "DATA_PROVIDER" -> "数据源失败占位";
            default -> "未分类来源";
        };
    }

    private boolean isOfficialDisclosure(FinancialEvidenceItem item) {
        String host = host(item.url());
        if (item.url() != null && !item.url().isBlank()) {
            return !host.isBlank() && OFFICIAL_DISCLOSURE_DOMAINS.stream()
                    .anyMatch(domain -> host.equals(domain) || host.endsWith("." + domain));
        }
        String sourceName = safe(item.sourceName());
        return sourceName.contains("证券交易所")
                || sourceName.contains("巨潮资讯")
                || sourceName.contains("证监会");
    }

    private String host(String url) {
        if (url == null || url.isBlank()) {
            return "";
        }
        try {
            String host = URI.create(url.trim()).getHost();
            return host == null ? "" : host.toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException ignored) {
            return "";
        }
    }

    private String safe(String value) {
        return value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
    }
}
