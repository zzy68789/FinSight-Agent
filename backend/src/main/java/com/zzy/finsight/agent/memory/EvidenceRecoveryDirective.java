package com.zzy.finsight.agent.memory;

import com.zzy.finsight.agent.planning.ToolInvocation;

import java.util.ArrayList;
import java.util.List;

/**
 * 表示质量门禁触发的一轮补证据任务及其实际工具尝试。
 *
 * @param recoveryNo 补证据轮次编号。
 * @param status 当前状态，只允许 PENDING 或 SATISFIED。
 * @param issueCodes 触发补证据的稳定问题码。
 * @param summary 门禁问题摘要。
 * @param baselineEffectiveEvidenceCount 开始补证据前的有效证据数。
 * @param addedEffectiveEvidenceCount 本轮累计新增有效证据数。
 * @param attempts 本轮已经执行的证据工具调用。
 */
public record EvidenceRecoveryDirective(
        int recoveryNo,
        String status,
        List<String> issueCodes,
        String summary,
        long baselineEffectiveEvidenceCount,
        long addedEffectiveEvidenceCount,
        List<ToolInvocation> attempts
) {
    public static final String PENDING = "PENDING";
    public static final String SATISFIED = "SATISFIED";

    public EvidenceRecoveryDirective {
        recoveryNo = Math.max(1, recoveryNo);
        status = SATISFIED.equals(status) ? SATISFIED : PENDING;
        issueCodes = issueCodes == null ? List.of() : List.copyOf(issueCodes);
        summary = summary == null ? "" : summary.trim();
        baselineEffectiveEvidenceCount = Math.max(0L, baselineEffectiveEvidenceCount);
        addedEffectiveEvidenceCount = Math.max(0L, addedEffectiveEvidenceCount);
        attempts = attempts == null ? List.of() : List.copyOf(attempts);
    }

    /** 创建一轮尚未执行证据工具的补证据任务。 */
    public static EvidenceRecoveryDirective pending(
            int recoveryNo,
            List<String> issueCodes,
            String summary,
            long baselineEffectiveEvidenceCount
    ) {
        return new EvidenceRecoveryDirective(
                recoveryNo,
                PENDING,
                issueCodes,
                summary,
                baselineEffectiveEvidenceCount,
                0L,
                List.of()
        );
    }

    /** 记录一次证据工具调用，并在获得新增有效证据时完成本轮任务。 */
    public EvidenceRecoveryDirective recordAttempt(ToolInvocation invocation, long newEffectiveEvidenceCount) {
        List<ToolInvocation> updatedAttempts = new ArrayList<>(attempts);
        if (invocation != null) {
            updatedAttempts.add(invocation);
        }
        long updatedEvidenceCount = addedEffectiveEvidenceCount + Math.max(0L, newEffectiveEvidenceCount);
        return new EvidenceRecoveryDirective(
                recoveryNo,
                updatedEvidenceCount > 0L ? SATISFIED : PENDING,
                issueCodes,
                summary,
                baselineEffectiveEvidenceCount,
                updatedEvidenceCount,
                updatedAttempts
        );
    }

    /** 返回当前是否仍要求下一步调用新的证据工具。 */
    public boolean pending() {
        return PENDING.equals(status);
    }
}
