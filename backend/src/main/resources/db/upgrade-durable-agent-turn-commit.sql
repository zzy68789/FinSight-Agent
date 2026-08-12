-- 已部署 V4 环境手动升级：加入 Agent 租约 fencing 代次。
ALTER TABLE research_task
  ADD COLUMN lease_epoch BIGINT NOT NULL DEFAULT 0 COMMENT 'Agent租约单调代次，用于拒绝过期执行者提交' AFTER lease_until,
  ADD INDEX idx_research_task_lease_fence (id, lease_owner, lease_epoch);
