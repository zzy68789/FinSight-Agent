package com.zzy.finsight.agent.runtime;

/**
 * 表示带单调 epoch 的任务租约令牌，用于拒绝过期执行者提交状态。
 *
 * @param taskId 任务标识。
 * @param owner 租约持有者。
 * @param epoch 单调递增的租约代次。
 */
public record LeaseToken(long taskId, String owner, long epoch) {
    public LeaseToken {
        owner = owner == null ? "" : owner;
        if (taskId <= 0 || owner.isBlank() || epoch <= 0) {
            throw new IllegalArgumentException("租约令牌缺少 taskId、owner 或 epoch");
        }
    }
}
