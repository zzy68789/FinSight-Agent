package com.zzy.finsight.search;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class TavilyExtractClientTest {

    @Test
    void extractsMultipleUrlsWithBearerAuthorization() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TavilyExtractClient client = new TavilyExtractClient(builder, "key", 10, 1);
        server.expect(once(), requestTo("https://api.tavily.com/extract"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer key"))
                .andExpect(content().string(containsString("\"extract_depth\":\"basic\"")))
                .andExpect(content().string(containsString("https://example.com/news")))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {
                              "url": "https://example.com/news",
                              "raw_content": "公司发布年度经营公告，正文包含可复核的经营数据与风险说明。"
                            }
                          ],
                          "failed_results": []
                        }
                        """, MediaType.APPLICATION_JSON));

        List<SearchResult> results = client.extract(List.of(
                new SearchResult("tavily", "公司公告", "https://example.com/news", "搜索摘要")
        ), 3);

        assertThat(results).containsExactly(new SearchResult(
                "tavily-extract",
                "公司公告",
                "https://example.com/news",
                "公司发布年度经营公告，正文包含可复核的经营数据与风险说明。"
        ));
        server.verify();
    }
}
