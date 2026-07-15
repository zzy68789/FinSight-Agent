package com.zzy.finsight.component.evaluation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 将评测运行结果写为机器可读 JSON 和人工可读 Markdown。
 */
@Component
public class EvaluationReportWriter {
    private final ObjectMapper objectMapper;

    public EvaluationReportWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** 在目标运行目录生成 results.json 与 summary.md。 */
    public Path write(Path outputRoot, EvaluationRunResult result) {
        Path runDirectory = outputRoot.resolve(result.runId());
        try {
            Files.createDirectories(runDirectory);
            objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValue(runDirectory.resolve("results.json").toFile(), result);
            Files.writeString(
                    runDirectory.resolve("summary.md"),
                    markdown(result),
                    StandardCharsets.UTF_8
            );
            return runDirectory;
        } catch (IOException exception) {
            throw new IllegalStateException("写入评测报告失败：" + runDirectory, exception);
        }
    }

    private String markdown(EvaluationRunResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append("# FinSight 离线评测报告\n\n")
                .append("- 运行 ID：`").append(result.runId()).append("`\n")
                .append("- 模式：`").append(result.mode()).append("`\n")
                .append("- 数据集：`").append(result.datasetVersion()).append("`\n")
                .append("- Git Commit：`").append(result.gitCommit()).append("`\n")
                .append("- 规则版本：`").append(result.policyVersion()).append("`\n")
                .append("- 状态：**").append(result.status()).append("**\n")
                .append("- 耗时：").append(result.durationMs()).append(" ms\n\n")
                .append("## 聚合指标\n\n")
                .append("| 指标 | 当前值 | 基线差异 |\n")
                .append("| --- | ---: | ---: |\n");
        result.aggregateMetrics().forEach((name, value) -> builder
                .append("| ").append(name).append(" | ").append(value).append(" | ")
                .append(result.baselineComparison().deltas().getOrDefault(name, java.math.BigDecimal.ZERO))
                .append(" |\n"));
        builder.append("\n## 样例结果\n\n")
                .append("| 样例 | 场景 | 期望 | 实际 | 断言 |\n")
                .append("| --- | --- | --- | --- | --- |\n");
        result.caseResults().forEach(item -> builder
                .append("| ").append(item.caseId()).append(" | ").append(item.scenario())
                .append(" | ").append(item.expectedStatus()).append(" | ").append(item.actualStatus())
                .append(" | ").append(item.status()).append(" |\n"));
        builder.append("\n## 检索结果\n\n")
                .append("| 问题 | 状态 | 失败原因 |\n")
                .append("| --- | --- | --- |\n");
        result.retrievalResults().forEach(item -> builder
                .append("| ").append(item.query().replace("|", "\\|"))
                .append(" | ").append(item.status())
                .append(" | ").append(String.join("；", item.failedReasons())).append(" |\n"));
        if (!result.judgeResults().isEmpty()) {
            builder.append("\n## Judge 结果\n\n")
                    .append("| 样例 | 状态 | 模型 | Token | 错误 |\n")
                    .append("| --- | --- | --- | ---: | --- |\n");
            result.judgeResults().forEach(item -> builder
                    .append("| ").append(item.caseId()).append(" | ").append(item.status())
                    .append(" | ").append(item.modelName()).append(" | ").append(item.totalTokens())
                    .append(" | ").append(item.errorMessage().replace("|", "\\|"))
                    .append(" |\n"));
        }
        if (!result.baselineComparison().failures().isEmpty()) {
            builder.append("\n## 回归失败\n\n");
            result.baselineComparison().failures().forEach(value -> builder.append("- ").append(value).append("\n"));
        }
        if (!result.baselineComparison().warnings().isEmpty()) {
            builder.append("\n## 非阻断告警\n\n");
            result.baselineComparison().warnings().forEach(value -> builder.append("- ").append(value).append("\n"));
        }
        return builder.toString();
    }
}
