package com.zzy.finsight.domain.stock;


import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/**
 * 表示一个金融报告评测样例。
 * @param ticker 证券代码。
 * @param name 名称。
 * @param reportPeriod 报告期。
 * @param requiredKeypoints 报告必备要点。
 */
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
