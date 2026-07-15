package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;


import java.util.List;

/**
 * 定义金融证据数据源的统一采集接口。
 */
public interface FinancialDataProvider {
    /** 返回数据源名称。 */
    String name();

    /** 按用户、证券和报告期采集金融证据。 */
    List<FinancialEvidenceItem> collect(long ownerId, StockSubject subject, String reportPeriod, String searchMode);

    /** 采集证据并保留可选的检索追踪信息。 */
    default FinancialDataCollection collectWithTrace(
            long ownerId,
            StockSubject subject,
            String reportPeriod,
            String searchMode
    ) {
        return FinancialDataCollection.evidenceOnly(collect(ownerId, subject, reportPeriod, searchMode));
    }
}
