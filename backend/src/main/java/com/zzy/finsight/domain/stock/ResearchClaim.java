package com.zzy.finsight.domain.stock;

import java.util.List;

/**
 * 表示由多头或空头研究角色生成的一条证据约束论据。
 * @param side 研究立场，取值为 BULL 或 BEAR。
 * @param title 论据标题。
 * @param statement 条件化陈述，不包含交易建议。
 * @param evidenceRefs 关联的证据编号，例如 E1。
 * @param strength 论据强度，取值为 HIGH、MEDIUM 或 LOW。
 */
public record ResearchClaim(
        String side,
        String title,
        String statement,
        List<String> evidenceRefs,
        String strength
) {
    public ResearchClaim {
        evidenceRefs = evidenceRefs == null ? List.of() : List.copyOf(evidenceRefs);
    }
}
