package com.zzy.drai.financial;

import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Component
@Order(0)
public class AShareMasterDataProvider implements FinancialDataProvider {
    private final AShareCompanyDirectory companyDirectory;

    public AShareMasterDataProvider(AShareCompanyDirectory companyDirectory) {
        this.companyDirectory = companyDirectory;
    }

    @Override
    public String name() {
        return "a-share-master-data";
    }

    @Override
    public List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode) {
        return companyDirectory.findByTicker(subject.ticker())
                .map(profile -> List.of(new FinancialEvidenceItem(
                        "LOCAL_CONTEXT",
                        "本地A股主数据样例",
                        "",
                        null,
                        reportPeriod,
                        "LOCAL_CONTEXT",
                        null,
                        null,
                        subject.fullCode() + " 对应 " + profile.companyName()
                                + "，行业分类为 " + profile.industry()
                                + "。该主档仅用于识别公司主体，不代表实时行情或估值数据。",
                        BigDecimal.ONE,
                        LocalDateTime.now(),
                        ""
                )))
                .orElseGet(List::of);
    }
}
