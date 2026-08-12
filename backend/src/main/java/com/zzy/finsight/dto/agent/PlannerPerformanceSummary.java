package com.zzy.finsight.dto.agent;

import com.zzy.finsight.domain.AgentPlannerCallRecord;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 汇总单次 Agent 任务的 Planner 模型质量与成本基线。
 * @param callCount Planner调用数量。
 * @param structuredValidRate 结构合法率百分比。
 * @param routeCorrectRate 路由正确率百分比。
 * @param inputTokens 输入Token总数。
 * @param outputTokens 输出Token总数。
 * @param durationP50Ms Planner耗时P50。
 * @param durationP95Ms Planner耗时P95。
 * @param modelCalls 各实际模型调用数量。
 */
public record PlannerPerformanceSummary(
        int callCount,
        BigDecimal structuredValidRate,
        BigDecimal routeCorrectRate,
        int inputTokens,
        int outputTokens,
        long durationP50Ms,
        long durationP95Ms,
        Map<String, Long> modelCalls
) {
    public PlannerPerformanceSummary {
        structuredValidRate = structuredValidRate == null ? BigDecimal.ZERO : structuredValidRate;
        routeCorrectRate = routeCorrectRate == null ? BigDecimal.ZERO : routeCorrectRate;
        modelCalls = modelCalls == null ? Map.of() : Map.copyOf(modelCalls);
    }

    /** 根据持久化 Planner 调用计算可审计基线。 */
    public static PlannerPerformanceSummary from(List<AgentPlannerCallRecord> calls) {
        List<AgentPlannerCallRecord> values = calls == null ? List.of() : List.copyOf(calls);
        if (values.isEmpty()) {
            return new PlannerPerformanceSummary(0, BigDecimal.ZERO, BigDecimal.ZERO,
                    0, 0, 0L, 0L, Map.of());
        }
        List<Long> durations = values.stream().map(AgentPlannerCallRecord::durationMs).sorted().toList();
        Map<String, Long> models = new LinkedHashMap<>();
        values.forEach(call -> models.merge(
                call.actualModel() == null || call.actualModel().isBlank()
                        ? call.requestedModel() : call.actualModel(),
                1L,
                Long::sum
        ));
        return new PlannerPerformanceSummary(
                values.size(),
                percent(values.stream().filter(AgentPlannerCallRecord::structuredValid).count(), values.size()),
                percent(values.stream().filter(AgentPlannerCallRecord::routeCorrect).count(), values.size()),
                values.stream().mapToInt(AgentPlannerCallRecord::inputTokens).sum(),
                values.stream().mapToInt(AgentPlannerCallRecord::outputTokens).sum(),
                percentile(durations, 0.50d),
                percentile(durations, 0.95d),
                models
        );
    }

    private static BigDecimal percent(long numerator, long denominator) {
        return denominator <= 0 ? BigDecimal.ZERO : BigDecimal.valueOf(numerator)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP);
    }

    private static long percentile(List<Long> sorted, double percentile) {
        if (sorted.isEmpty()) {
            return 0L;
        }
        int index = Math.max(0, (int) Math.ceil(percentile * sorted.size()) - 1);
        return sorted.get(Math.min(index, sorted.size() - 1));
    }
}
