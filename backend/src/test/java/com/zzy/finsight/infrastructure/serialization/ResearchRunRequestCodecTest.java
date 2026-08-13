package com.zzy.finsight.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.dto.agent.ResearchIntent;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ResearchRunRequestCodecTest {
    private final ResearchRunRequestCodec codec = new ResearchRunRequestCodec(
            new ObjectMapper().findAndRegisterModules()
    );

    @Test
    void preservesResearchIntentAcrossTaskRecoveryPayload() {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519.SH");
        request.setResearchQuestion("核验公告影响");
        request.setResearchIntent(ResearchIntent.EVENT_IMPACT);

        ResearchRunRequest restored = codec.fromJson(codec.toJson(request));

        assertThat(restored.getResearchIntent()).isEqualTo(ResearchIntent.EVENT_IMPACT);
    }

    @Test
    void defaultsOldPayloadWithoutIntentToComprehensive() {
        ResearchRunRequest restored = codec.fromJson("""
                {"ticker":"600519.SH","research_question":"分析当前表现"}
                """);

        assertThat(restored.getResearchIntent()).isEqualTo(ResearchIntent.COMPREHENSIVE);
        assertThat(restored.getComparisonTickers()).isEmpty();
    }

    @Test
    void preservesTypedComparisonTickersAcrossTaskRecoveryPayload() {
        ResearchRunRequest request = new ResearchRunRequest();
        request.setTicker("600519.SH");
        request.setResearchQuestion("比较估值与营收变化");
        request.setComparisonTickers(List.of("000858.SZ", "600809.SH"));

        ResearchRunRequest restored = codec.fromJson(codec.toJson(request));

        assertThat(restored.getComparisonTickers()).containsExactly("000858.SZ", "600809.SH");
    }
}
