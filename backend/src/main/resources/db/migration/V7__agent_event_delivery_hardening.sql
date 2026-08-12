ALTER TABLE agent_event_outbox
  ADD COLUMN claim_owner VARCHAR(64) COMMENT '当前事件投递领取实例' AFTER published_at,
  ADD COLUMN claimed_until DATETIME COMMENT '事件投递领取到期时间' AFTER claim_owner,
  ADD COLUMN publish_attempts INT NOT NULL DEFAULT 0 COMMENT '事件投递尝试次数' AFTER claimed_until,
  ADD COLUMN next_attempt_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '事件下次允许投递时间' AFTER publish_attempts,
  ADD COLUMN last_error LONGTEXT COMMENT '最近一次事件投递失败说明' AFTER next_attempt_at,
  ADD COLUMN dead_lettered_at DATETIME COMMENT '事件进入死信状态时间' AFTER last_error,
  ADD INDEX idx_agent_event_claim (published_at, dead_lettered_at, next_attempt_at, claimed_until, id);

ALTER TABLE agent_planner_call
  ADD COLUMN turn_id BIGINT COMMENT '关联的Agent轮次ID，建计划与独立重规划可为空' AFTER task_id,
  ADD UNIQUE KEY uk_agent_planner_turn (turn_id);
