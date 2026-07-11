package com.zzy.finsight.financial;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public record FinancialEvaluationCase(
        String ticker,
        String name,
        @JsonProperty("report_period") String reportPeriod,
        @JsonProperty("required_keypoints") List<String> requiredKeypoints
) {
    public FinancialEvaluationCase {
        requiredKeypoints = requiredKeypoints == null ? List.of() : List.copyOf(requiredKeypoints);
    }
}
