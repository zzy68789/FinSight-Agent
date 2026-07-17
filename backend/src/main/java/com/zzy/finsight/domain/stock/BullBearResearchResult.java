package com.zzy.finsight.domain.stock;

import java.util.List;

/**
 * 表示多空研究 Agent 的结构化输出。
 * @param bullCases 多头研究角色给出的正向条件。
 * @param bearCases 空头研究角色给出的风险条件。
 * @param synthesis 中立综合结论。
 * @param status 生成状态。
 */
public record BullBearResearchResult(
        List<ResearchClaim> bullCases,
        List<ResearchClaim> bearCases,
        String synthesis,
        String status
) {
    public BullBearResearchResult {
        bullCases = bullCases == null ? List.of() : List.copyOf(bullCases);
        bearCases = bearCases == null ? List.of() : List.copyOf(bearCases);
        synthesis = synthesis == null ? "" : synthesis;
        status = status == null || status.isBlank() ? "COMPLETED" : status;
    }

    /** 返回没有论据的安全默认结果。 */
    public static BullBearResearchResult empty() {
        return new BullBearResearchResult(List.of(), List.of(),
                "当前证据不足以形成多空对照，结论保持开放。", "INSUFFICIENT_EVIDENCE");
    }
}
