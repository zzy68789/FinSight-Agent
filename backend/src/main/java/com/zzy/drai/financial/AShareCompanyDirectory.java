package com.zzy.drai.financial;

import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;

@Component
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

    public record AShareCompanyProfile(String ticker, String companyName, String industry) {
    }
}
