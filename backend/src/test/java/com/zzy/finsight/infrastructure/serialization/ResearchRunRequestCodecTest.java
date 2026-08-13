package com.zzy.finsight.infrastructure.serialization;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zzy.finsight.dto.agent.ResearchIntent;
import com.zzy.finsight.dto.agent.ResearchRunRequest;
import org.junit.jupiter.api.Test;

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
    }
}
