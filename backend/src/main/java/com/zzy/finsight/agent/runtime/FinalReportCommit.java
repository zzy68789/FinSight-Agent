package com.zzy.finsight.agent.runtime;

/**
 * 表示通过全部门禁后需要与最终 turn 原子提交的报告版本。
 * @param content 报告正文。
 * @param critique 评审说明。
 * @param dataSnapshotHash 数据快照摘要。
 * @param generationContextHash 生成上下文摘要。
 * @param reusedFromReportId 复用来源报告标识。
 */
public record FinalReportCommit(
        String content,
        String critique,
        String dataSnapshotHash,
        String generationContextHash,
        Long reusedFromReportId
) {
    public FinalReportCommit {
        content = content == null ? "" : content;
        critique = critique == null ? "" : critique;
        dataSnapshotHash = dataSnapshotHash == null ? "" : dataSnapshotHash;
        generationContextHash = generationContextHash == null ? "" : generationContextHash;
    }
}
