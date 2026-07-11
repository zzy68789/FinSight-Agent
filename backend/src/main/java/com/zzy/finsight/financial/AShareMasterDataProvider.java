package com.zzy.finsight.financial;

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
        if (subject.isEtf()) {
            return List.of(new FinancialEvidenceItem(
                    "LOCAL_CONTEXT",
                    "本地ETF代码识别",
                    "",
                    null,
                    reportPeriod,
                    "LOCAL_CONTEXT",
                    null,
                    null,
                    subject.fullCode() + " 已识别为沪深交易所 ETF 标的；该主档仅用于资产类型识别，不代表基金净值、持仓或规模数据。",
                    BigDecimal.ONE,
                    LocalDateTime.now(),
                    ""
            ));
        }
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
