package com.zzy.finsight.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.agent.memory.EvidenceMemory;
import com.zzy.finsight.agent.planning.ToolInvocation;
import com.zzy.finsight.search.SearchService;
import com.zzy.finsight.search.TavilyExtractClient;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class ResearchToolContractTest {

    @Test
    void decodesPlannerJsonToTypedArgumentsAndExposesStableSchema() {
        ResearchToolRegistry registry = registry();

        PreparedToolCall prepared = registry.prepare(new ToolInvocation(
                "search_public_evidence", Map.of("query", "核验现金流质量")
        ));

        assertThat(prepared.arguments()).isEqualTo(new SearchPublicEvidenceArguments("核验现金流质量"));
        assertThat(registry.catalog().get(0).get("arguments")).isEqualTo(List.of(
                ToolParameterSchema.optionalString("query", "用于收窄公开资料检索范围的研究问题", 500)
        ));
    }

    @Test
    void rejectsWrongArgumentTypeBeforeToolExecution() {
        ResearchToolRegistry registry = registry();

        assertThatThrownBy(() -> registry.prepare(new ToolInvocation(
                "search_public_evidence", Map.of("query", 123)
        )))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("query 必须是字符串");
    }

    private ResearchToolRegistry registry() {
        SearchPublicEvidenceTool tool = new SearchPublicEvidenceTool(
                mock(SearchService.class),
                mock(TavilyExtractClient.class),
                mock(EvidenceMemory.class),
                3
        );
        return new ResearchToolRegistry(List.of(tool), new ObjectMapper().findAndRegisterModules());
    }
}
