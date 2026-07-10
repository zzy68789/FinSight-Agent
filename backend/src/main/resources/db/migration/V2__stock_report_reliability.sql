ALTER TABLE agent_step_log
  ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '当前步骤执行尝试次数' AFTER error_message,
  ADD COLUMN duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '步骤执行耗时毫秒数' AFTER attempt_no;

ALTER TABLE stock_metric_result
  ADD COLUMN formula_version VARCHAR(32) NOT NULL DEFAULT 'v1' COMMENT '指标计算公式版本' AFTER formula;

ALTER TABLE stock_analysis_snapshot
  ADD COLUMN data_snapshot_hash CHAR(64) NOT NULL DEFAULT '' COMMENT '稳定业务字段生成的数据快照SHA-256' AFTER snapshot_json,
  ADD INDEX idx_stock_snapshot_hash (owner_id, ticker, report_period, data_snapshot_hash);

ALTER TABLE report
  ADD COLUMN snapshot_id BIGINT COMMENT '关联的金融数据快照ID' AFTER critique,
  ADD COLUMN data_snapshot_hash CHAR(64) COMMENT '生成报告使用的数据快照SHA-256' AFTER snapshot_id,
  ADD COLUMN generation_context_hash CHAR(64) COMMENT '包含生成规则版本的上下文SHA-256' AFTER data_snapshot_hash,
  ADD COLUMN reused_from_report_id BIGINT COMMENT '缓存复用来源报告ID' AFTER generation_context_hash,
  ADD INDEX idx_report_context_hash (owner_id, generation_context_hash);

ALTER TABLE research_task
  ADD COLUMN stage VARCHAR(64) NOT NULL DEFAULT 'CREATED' COMMENT '金融工作流当前持久化阶段' AFTER revision_number,
  ADD COLUMN attempt_count INT NOT NULL DEFAULT 0 COMMENT '工作流执行尝试次数' AFTER stage,
  ADD COLUMN request_payload LONGTEXT COMMENT '可恢复执行所需的请求JSON' AFTER attempt_count,
  ADD COLUMN last_error LONGTEXT COMMENT '最近一次执行失败原因' AFTER request_payload,
  ADD COLUMN heartbeat_at DATETIME COMMENT '工作流最近心跳时间' AFTER last_error,
  ADD COLUMN lease_owner VARCHAR(128) COMMENT '当前任务租约持有者' AFTER heartbeat_at,
  ADD COLUMN lease_until DATETIME COMMENT '当前任务租约到期时间' AFTER lease_owner,
  ADD INDEX idx_research_task_recovery (status, heartbeat_at);


