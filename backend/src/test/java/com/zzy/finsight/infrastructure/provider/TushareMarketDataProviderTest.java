package com.zzy.finsight.infrastructure.provider;

import com.zzy.finsight.domain.stock.FinancialEvidenceItem;
import com.zzy.finsight.domain.stock.FinancialDataCollection;
import com.zzy.finsight.domain.stock.StockAssetType;
import com.zzy.finsight.domain.stock.StockSubject;
import com.zzy.finsight.domain.stock.metric.FinancialMetricInputNames;


import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TushareMarketDataProviderTest {

    @Test
    void skipsExternalCallWhenTokenMissingOrDocumentMode() {
        RestClient.Builder builder = RestClient.builder();
        TushareMarketDataProvider provider = new TushareMarketDataProvider(
                builder,
                new ObjectMapper(),
                true,
                "https://api.tushare.pro",
                "",
                "PT10S",
                false
        );
        StockSubject subject = new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料");

        assertThat(provider.collect(7L, subject, "latest", "hybrid")).isEmpty();

        provider = new TushareMarketDataProvider(
                builder,
                new ObjectMapper(),
                true,
                "https://api.tushare.pro",
                "token",
                "PT10S",
                false
        );

        assertThat(provider.collect(7L, subject, "latest", "document")).isEmpty();
    }

    @Test
    void requestsTushareApisAndMapsFinancialEvidence() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TushareMarketDataProvider provider = new TushareMarketDataProvider(
                builder,
                new ObjectMapper(),
                true,
                "https://api.tushare.pro",
                "token-123",
                "PT10S",
                false
        );
        StockSubject subject = new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料");

        expectApi(server, "income", "ts_code,ann_date,f_ann_date,end_date,report_type,update_flag,revenue,oper_cost,n_income_attr_p", """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "ann_date", "f_ann_date", "end_date", "report_type", "update_flag", "revenue", "oper_cost", "n_income_attr_p"],
                    "items": [
                      ["600519.SH", "20260425", "20260425", "20260331", "1", "0", 53000000000.00, 5500000000.00, 27000000000.00],
                      ["600519.SH", "20260425", "20260425", "20260331", "1", "1", 53909252220.51, 5520729200.32, 27242512886.45],
                      ["600519.SH", "20250430", "20250430", "20250331", "1", "1", 50600957885.78, 4061430550.43, 26847474238.76]
                    ]
                  }
                }
                """);
        expectApi(server, "balancesheet", "ts_code,ann_date,f_ann_date,end_date,report_type,update_flag,total_assets,total_liab,total_hldr_eqy_exc_min_int", """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "ann_date", "f_ann_date", "end_date", "report_type", "update_flag", "total_assets", "total_liab", "total_hldr_eqy_exc_min_int"],
                    "items": [
                      ["600519.SH", "20260425", "20260425", "20260331", "1", "0", 310000000000.00, 38000000000.00, 260000000000.00],
                      ["600519.SH", "20260425", "20260425", "20260331", "1", "1", 319918844905.58, 38782958469.89, 270894035676.27],
                      ["600519.SH", "20260417", "20260417", "20251231", "1", "1", 303834844021.44, 49875590112.37, 244637811032.18]
                    ]
                  }
                }
                """);
        expectApi(server, "cashflow", "ts_code,ann_date,f_ann_date,end_date,report_type,update_flag,n_cashflow_act", """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "ann_date", "f_ann_date", "end_date", "report_type", "update_flag", "n_cashflow_act"],
                    "items": [
                      ["600519.SH", "20260425", "20260425", "20260331", "1", "0", 25000000000.00],
                      ["600519.SH", "20260425", "20260425", "20260331", "1", "1", 26909891269.13]
                    ]
                  }
                }
                """);
        expectApi(server, "daily_basic", "ts_code,trade_date,pe_ttm,pb,total_mv", """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "trade_date", "pe_ttm", "pb", "total_mv"],
                    "items": [
                      ["600519.SH", "20250703", 22.35, 8.65, 178000000.00]
                    ]
                  }
                }
                """);

        List<FinancialEvidenceItem> items = provider.collect(7L, subject, "latest", "hybrid");

        assertThat(items).extracting(FinancialEvidenceItem::metricName)
                .contains(
                        FinancialMetricInputNames.OPERATING_REVENUE,
                        FinancialMetricInputNames.OPERATING_REVENUE_PRIOR,
                        FinancialMetricInputNames.OPERATING_COST,
                        FinancialMetricInputNames.NET_PROFIT,
                        FinancialMetricInputNames.TOTAL_ASSETS,
                        FinancialMetricInputNames.TOTAL_LIABILITIES,
                        FinancialMetricInputNames.BEGINNING_EQUITY,
                        FinancialMetricInputNames.ENDING_EQUITY,
                        FinancialMetricInputNames.OPERATING_CASH_FLOW,
                        "PE_TTM",
                        "PB",
                        "TOTAL_MARKET_VALUE"
                );
        assertThat(items).allMatch(FinancialEvidenceItem::effective);
        assertThat(items).allMatch(item -> "AUTHORIZED_MARKET".equals(item.sourceType()));
        assertThat(items).allMatch(item -> "TuShare Pro".equals(item.sourceName()));
        assertThat(items.stream()
                .filter(item -> FinancialMetricInputNames.OPERATING_REVENUE.equals(item.metricName()))
                .findFirst()
                .orElseThrow()
                .normalizedValue()).isEqualByComparingTo("539.092522");
        FinancialEvidenceItem priorRevenue = items.stream()
                .filter(item -> FinancialMetricInputNames.OPERATING_REVENUE_PRIOR.equals(item.metricName()))
                .findFirst()
                .orElseThrow();
        assertThat(priorRevenue.reportPeriod()).isEqualTo("20250331");
        assertThat(priorRevenue.normalizedValue()).isEqualByComparingTo("506.009579");
        server.verify();
    }

    @Test
    void requestsFundDailyForEtfInsteadOfCompanyFinancialApis() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TushareMarketDataProvider provider = new TushareMarketDataProvider(
                builder,
                new ObjectMapper(),
                true,
                "https://api.tushare.pro",
                "token-123",
                "PT10S",
                false
        );
        StockSubject subject = new StockSubject("588200", "SH", "588200.SH", "待识别ETF", "ETF", StockAssetType.ETF);

        expectApi(server, "fund_daily", "588200.SH", "ts_code,trade_date,open,high,low,close,pre_close,pct_chg,vol,amount", "\"end_date\":\"20260706\"", """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "trade_date", "open", "high", "low", "close", "pre_close", "pct_chg", "vol", "amount"],
                    "items": [
                      ["588200.SH", "20260706", 1.20, 1.25, 1.19, 1.234, 1.208, 2.15, 280000.00, 35000.00],
                      ["588200.SH", "20260703", 1.18, 1.22, 1.17, 1.208, 1.19, 1.51, 240000.00, 31000.00]
                    ]
                  }
                }
                """);
        expectApi(server, "fund_basic", "588200.SH",
                "ts_code,name,management,custodian,fund_type,list_date,m_fee,c_fee,benchmark,invest_type", null, """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "name", "management", "custodian", "fund_type", "list_date", "m_fee", "c_fee", "benchmark", "invest_type"],
                    "items": [["588200.SH", "科创芯片ETF", "示例基金", "示例银行", "股票型", "20220101", 0.50, 0.10, "科创芯片指数", "被动指数型"]]
                  }
                }
                """);
        expectApi(server, "fund_nav", "588200.SH",
                "ts_code,ann_date,nav_date,unit_nav,accum_nav,total_netasset", "\"end_date\":\"20260706\"", """
                {
                  "code": 0,
                  "msg": "",
                  "data": {
                    "fields": ["ts_code", "ann_date", "nav_date", "unit_nav", "accum_nav", "total_netasset"],
                    "items": [["588200.SH", "20260706", "20260706", 1.200, 1.320, 4500000000.00]]
                  }
                }
                """);

        FinancialDataCollection collection = provider.collectWithTrace(7L, subject, "20260706", "hybrid");
        List<FinancialEvidenceItem> items = collection.evidenceItems();

        assertThat(items).extracting(FinancialEvidenceItem::metricName)
                .contains(
                        FinancialMetricInputNames.ETF_CLOSE,
                        FinancialMetricInputNames.ETF_PCT_CHANGE,
                        FinancialMetricInputNames.ETF_AMOUNT,
                        FinancialMetricInputNames.ETF_UNIT_NAV,
                        FinancialMetricInputNames.ETF_ACCUMULATED_NAV,
                        FinancialMetricInputNames.ETF_TOTAL_NET_ASSET,
                        FinancialMetricInputNames.ETF_PREMIUM_DISCOUNT_RATE,
                        FinancialMetricInputNames.ETF_PROFILE
                );
        assertThat(items).allMatch(FinancialEvidenceItem::effective);
        assertThat(collection.marketSeries()).hasSize(2);
        assertThat(collection.marketSeries().get(0).tradeDate()).isEqualTo("20260703");
        assertThat(collection.etfDeepData().fundName()).isEqualTo("科创芯片ETF");
        assertThat(collection.etfDeepData().premiumDiscountRate()).isEqualByComparingTo("2.8333");
        server.verify();
    }

    @Test
    void emitsDataMissingEvidenceWhenTushareReturnsError() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TushareMarketDataProvider provider = new TushareMarketDataProvider(
                builder,
                new ObjectMapper(),
                true,
                "https://api.tushare.pro",
                "token-123",
                "PT10S",
                false
        );
        StockSubject subject = new StockSubject("600519", "SH", "600519.SH", "贵州茅台", "食品饮料");

        server.expect(once(), requestTo("https://api.tushare.pro"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"api_name\":\"income\"")))
                .andRespond(withSuccess("""
                        {"code": 40001, "msg": "抱歉，您没有访问该接口的权限", "data": {"fields": [], "items": []}}
                        """, MediaType.APPLICATION_JSON));

        List<FinancialEvidenceItem> items = provider.collect(7L, subject, "latest", "hybrid");

        assertThat(items).hasSize(1);
        assertThat(items.get(0).sourceType()).isEqualTo("AUTHORIZED_MARKET");
        assertThat(items.get(0).issueCode()).isEqualTo("DATA_MISSING");
        assertThat(items.get(0).effective()).isFalse();
        assertThat(items.get(0).excerpt()).contains("TuShare Pro 数据不可用", "40001");
        server.verify();
    }

    private void expectApi(MockRestServiceServer server, String apiName, String fields, String response) {
        expectApi(server, apiName, "600519.SH", fields, response);
    }

    private void expectApi(MockRestServiceServer server, String apiName, String tsCode, String fields, String response) {
        expectApi(server, apiName, tsCode, fields, null, response);
    }

    private void expectApi(MockRestServiceServer server, String apiName, String tsCode, String fields, String expectedParam, String response) {
        server.expect(once(), requestTo("https://api.tushare.pro"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"api_name\":\"" + apiName + "\"")))
                .andExpect(content().string(containsString("\"token\":\"token-123\"")))
                .andExpect(content().string(containsString("\"ts_code\":\"" + tsCode + "\"")))
                .andExpect(content().string(containsString("\"fields\":\"" + fields + "\"")))
                .andExpect(request -> {
                    if (expectedParam != null) {
                        content().string(containsString(expectedParam)).match(request);
                    }
                })
                .andRespond(withSuccess(response, MediaType.APPLICATION_JSON));
    }
}
