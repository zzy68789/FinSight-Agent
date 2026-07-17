package com.zzy.finsight.domain.stock;

import com.fasterxml.jackson.databind.json.JsonMapper;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FinancialSnapshotBackwardCompatibilityTest {

    @Test
    void readsLegacySnapshotWithoutMarketSeriesAndEtfDepth() throws Exception {
        String legacyJson = """
                {
                  "subject": {
                    "ticker": "600519",
                    "exchange": "SH",
                    "fullCode": "600519.SH",
                    "companyName": "贵州茅台",
                    "industry": "食品饮料",
                    "assetType": "EQUITY"
                  },
                  "reportPeriod": "latest",
                  "searchMode": "hybrid",
                  "evidenceItems": [],
                  "stageResults": [],
                  "retrievalResults": [],
                  "createdAt": "2026-07-17T10:00:00"
                }
                """;

        FinancialSnapshot snapshot = JsonMapper.builder()
                .findAndAddModules()
                .build()
                .readValue(legacyJson, FinancialSnapshot.class);

        assertThat(snapshot.subject().fullCode()).isEqualTo("600519.SH");
        assertThat(snapshot.marketSeries()).isEmpty();
        assertThat(snapshot.etfDeepData()).isNull();
    }
}
