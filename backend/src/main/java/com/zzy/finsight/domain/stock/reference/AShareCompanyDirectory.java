package com.zzy.finsight.domain.stock.reference;


import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * 维护本地 A 股公司名称和行业主档。
 */
public class AShareCompanyDirectory {
    private final Map<String, AShareCompanyProfile> profiles = Map.ofEntries(
            Map.entry("600519", new AShareCompanyProfile("600519", "贵州茅台", "食品饮料")),
            Map.entry("300750", new AShareCompanyProfile("300750", "宁德时代", "电力设备")),
            Map.entry("000001", new AShareCompanyProfile("000001", "平安银行", "银行")),
            Map.entry("601318", new AShareCompanyProfile("601318", "中国平安", "非银金融")),
            Map.entry("600036", new AShareCompanyProfile("600036", "招商银行", "银行")),
            Map.entry("000858", new AShareCompanyProfile("000858", "五粮液", "食品饮料")),
            Map.entry("601899", new AShareCompanyProfile("601899", "紫金矿业", "有色金属")),
            Map.entry("002594", new AShareCompanyProfile("002594", "比亚迪", "汽车")),
            Map.entry("600276", new AShareCompanyProfile("600276", "恒瑞医药", "医药生物")),
            Map.entry("688981", new AShareCompanyProfile("688981", "中芯国际", "电子"))
    );

    public Optional<AShareCompanyProfile> findByTicker(String ticker) {
        return Optional.ofNullable(profiles.get(ticker));
    }

    /** 按代码或公司名称搜索本地主档，并按匹配程度稳定排序。 */
    public List<AShareCompanyProfile> search(String query, int limit) {
        if (query == null || query.isBlank() || limit <= 0) {
            return List.of();
        }
        String normalized = query.trim().toUpperCase(Locale.ROOT);
        return profiles.values().stream()
                .filter(profile -> profile.ticker().contains(normalized)
                        || profile.companyName().toUpperCase(Locale.ROOT).contains(normalized))
                .sorted(Comparator
                        .comparingInt((AShareCompanyProfile profile) -> relevance(profile, normalized))
                        .thenComparing(AShareCompanyProfile::ticker))
                .limit(Math.min(limit, 100))
                .toList();
    }

    private int relevance(AShareCompanyProfile profile, String query) {
        String name = profile.companyName().toUpperCase(Locale.ROOT);
        if (profile.ticker().equals(query)) {
            return 0;
        }
        if (name.equals(query)) {
            return 1;
        }
        if (profile.ticker().startsWith(query)) {
            return 2;
        }
        if (name.startsWith(query)) {
            return 3;
        }
        return 4;
    }

    /**
     * 表示本地维护的 A 股公司档案。
     * @param ticker 证券代码。
     * @param companyName 公司名称。
     * @param industry 所属行业。
     */
    public record AShareCompanyProfile(String ticker, String companyName, String industry) {
    }
}
