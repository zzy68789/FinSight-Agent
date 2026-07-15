package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.component.marketdata.FinancialEvidenceParser;


import com.zzy.finsight.rag.RagDocument;
import com.zzy.finsight.rag.RagRetrievalResult;
import com.zzy.finsight.service.RagService;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 从已上传报告的 RAG 结果中提取金融证据。
 */
@Component
public class UploadedReportProvider implements FinancialDataProvider {
    private final RagService ragService;
    private final FinancialEvidenceParser evidenceParser;

    public UploadedReportProvider(RagService ragService, FinancialEvidenceParser evidenceParser) {
        this.ragService = ragService;
        this.evidenceParser = evidenceParser;
    }

    @Override
    public String name() {
        return "uploaded-report";
    }

    @Override
    public List<FinancialEvidenceItem> collect(StockSubject subject, String reportPeriod, String searchMode) {
        return collectWithTrace(subject, reportPeriod, searchMode).evidenceItems();
    }

    @Override
    public FinancialDataCollection collectWithTrace(StockSubject subject, String reportPeriod, String searchMode) {
        if ("web".equalsIgnoreCase(searchMode)) {
            return FinancialDataCollection.evidenceOnly(List.of());
        }
        String query = subject.isEtf()
                ? subject.fullCode() + " ETF 基金 招募说明书 定期报告 净值 持仓 跟踪指数 规模"
                : subject.fullCode() + " " + subject.companyName() + " 年报 季报 营业收入 净利润 经营现金流 总资产 总负债";
        RagRetrievalResult retrievalResult = ragService.retrieveWithTrace(query, 6);
        List<RagDocument> documents = retrievalResult.documents();
        List<FinancialEvidenceItem> items = new ArrayList<>();
        for (RagDocument document : documents) {
            BigDecimal confidence = BigDecimal.valueOf(Math.max(0.1d, Math.min(1.0d, document.score())));
            List<FinancialEvidenceItem> parsed = evidenceParser.parse(
                    document.content(),
                    "UPLOADED_REPORT",
                    document.source(),
                    "",
                    reportPeriod,
                    confidence
            );
            if (parsed.isEmpty()) {
                items.add(new FinancialEvidenceItem(
                        "UPLOADED_REPORT",
                        document.source(),
                        "",
                        null,
                        reportPeriod,
                        "LOCAL_CONTEXT",
                        null,
                        null,
                        trim(document.content()),
                        confidence,
                        LocalDateTime.now(),
                        ""
                ));
            } else {
                items.addAll(parsed);
            }
        }
        return new FinancialDataCollection(items, retrievalResult);
    }

    private String trim(String content) {
        if (content == null) {
            return "";
        }
        String normalized = content.replaceAll("\\s+", " ").trim();
        return normalized.length() <= 300 ? normalized : normalized.substring(0, 300);
    }
}
