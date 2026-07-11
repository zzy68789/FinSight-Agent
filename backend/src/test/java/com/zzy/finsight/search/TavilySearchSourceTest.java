package com.zzy.finsight.search;

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

class TavilySearchSourceTest {

    @Test
    void mapsTavilyResultsWithSourceNameAndUrl() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        TavilySearchSource source = new TavilySearchSource(builder, "key");
        server.expect(once(), requestTo("https://api.tavily.com/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(containsString("\"query\":\"agent\"")))
                .andRespond(withSuccess("""
                        {
                          "results": [
                            {"title": "Agent", "url": "https://example.com", "content": "agent evidence"}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<SearchResult> results = source.search("agent", 3);

        assertThat(results).containsExactly(new SearchResult(
                "tavily",
                "Agent",
                "https://example.com",
                "agent evidence"
        ));
        server.verify();
    }
}
