package com.zzy.finsight.dto.agent;

import com.zzy.finsight.domain.AgentPlannerCallRecord;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PlannerPerformanceSummaryTest {

    @Test
    void summarizesModelQualityTokensAndLatencyPercentiles() {
        PlannerPerformanceSummary summary = PlannerPerformanceSummary.from(List.of(
                call(1, "fast-model", 10, true, true),
                call(2, "fast-model", 20, true, false),
                call(3, "smart-model", 30, false, true),
                call(4, "smart-model", 100, true, true)
        ));

        assertThat(summary.callCount()).isEqualTo(4);
        assertThat(summary.structuredValidRate()).isEqualByComparingTo("75.00");
        assertThat(summary.routeCorrectRate()).isEqualByComparingTo("75.00");
        assertThat(summary.inputTokens()).isEqualTo(40);
        assertThat(summary.outputTokens()).isEqualTo(20);
        assertThat(summary.durationP50Ms()).isEqualTo(20L);
        assertThat(summary.durationP95Ms()).isEqualTo(100L);
        assertThat(summary.modelCalls()).containsEntry("fast-model", 2L).containsEntry("smart-model", 2L);
    }

    private AgentPlannerCallRecord call(
            long id,
            String model,
            long duration,
            boolean structuredValid,
            boolean routeCorrect
    ) {
        return new AgentPlannerCallRecord(
                id, 11L, "NEXT_ACTION", "FAST", model, 10, 5, duration, 1,
                structuredValid, routeCorrect, false, "", LocalDateTime.of(2026, 8, 12, 12, 0)
        );
    }
}
