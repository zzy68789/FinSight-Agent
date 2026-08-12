-- 手动升级：将固定证券报告工作流演进为受约束 Research Agent Runtime。
-- 正式环境优先由 Flyway V4__research_agent_runtime.sql 执行；仅在无法运行 Flyway 时手工执行本脚本。

ALTER TABLE research_task
  ADD COLUMN runtime_type VARCHAR(32) NOT NULL DEFAULT 'LEGACY_WORKFLOW' COMMENT '任务执行内核类型' AFTER search_mode,
  ADD COLUMN turn_count INT NOT NULL DEFAULT 0 COMMENT '已完成的Agent决策轮次' AFTER attempt_count,
  ADD COLUMN tool_call_count INT NOT NULL DEFAULT 0 COMMENT '已执行的工具调用次数' AFTER turn_count,
  ADD COLUMN stop_reason VARCHAR(128) COMMENT '任务停止原因' AFTER last_error,
  ADD COLUMN planner_version VARCHAR(64) COMMENT 'Planner策略版本' AFTER stop_reason,
  ADD COLUMN toolset_version VARCHAR(64) COMMENT '白名单工具集版本' AFTER planner_version,
  ADD COLUMN policy_version VARCHAR(64) COMMENT 'Agent运行策略版本' AFTER toolset_version,
  ADD COLUMN completed_at DATETIME COMMENT '任务完成或停止时间' AFTER policy_version,
  ADD INDEX idx_research_task_runtime_recovery (runtime_type, status, heartbeat_at);

CREATE TABLE IF NOT EXISTS agent_turn (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  task_id BIGINT NOT NULL COMMENT '关联的Research Agent任务ID',
  turn_no INT NOT NULL COMMENT 'Agent决策轮次',
  phase VARCHAR(32) NOT NULL COMMENT '当前运行阶段',
  action_type VARCHAR(64) NOT NULL COMMENT 'Planner选择的结构化动作类型',
  action_json LONGTEXT NOT NULL COMMENT 'Planner动作JSON',
  observation_summary LONGTEXT COMMENT '本轮观察摘要',
  status VARCHAR(32) NOT NULL COMMENT '本轮执行状态',
  input_tokens INT NOT NULL DEFAULT 0 COMMENT 'Planner输入Token数',
  output_tokens INT NOT NULL DEFAULT 0 COMMENT 'Planner输出Token数',
  duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '本轮执行耗时毫秒数',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  UNIQUE KEY uk_agent_turn_task_no (task_id, turn_no),
  INDEX idx_agent_turn_task (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_tool_call (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  task_id BIGINT NOT NULL COMMENT '关联的Research Agent任务ID',
  turn_id BIGINT NOT NULL COMMENT '关联的Agent轮次ID',
  call_id VARCHAR(64) NOT NULL COMMENT '单次工具调用唯一标识',
  tool_name VARCHAR(128) NOT NULL COMMENT '白名单工具名称',
  arguments_hash CHAR(64) NOT NULL COMMENT '规范化工具参数SHA-256',
  arguments_json LONGTEXT NOT NULL COMMENT '工具参数JSON',
  result_json LONGTEXT COMMENT '工具结构化结果JSON',
  status VARCHAR(32) NOT NULL COMMENT '工具调用状态',
  attempt_no INT NOT NULL DEFAULT 1 COMMENT '工具调用尝试次数',
  duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '工具调用耗时毫秒数',
  error_code VARCHAR(64) COMMENT '稳定错误分类',
  error_message LONGTEXT COMMENT '工具调用失败说明',
  started_at DATETIME NOT NULL COMMENT '工具调用开始时间',
  completed_at DATETIME COMMENT '工具调用完成时间',
  UNIQUE KEY uk_agent_tool_call_id (call_id),
  INDEX idx_agent_tool_task (task_id, id),
  INDEX idx_agent_tool_dedup (task_id, tool_name, arguments_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

ALTER TABLE stock_evidence_item
  ADD COLUMN tool_call_id BIGINT COMMENT '产生该证据的工具调用ID' AFTER task_id,
  ADD COLUMN evidence_key CHAR(64) COMMENT '证据稳定去重摘要' AFTER tool_call_id,
  ADD UNIQUE KEY uk_stock_evidence_task_key (task_id, evidence_key),
  ADD INDEX idx_stock_evidence_tool_call (tool_call_id);

ALTER TABLE checkpoint
  ADD COLUMN state_version VARCHAR(32) NOT NULL DEFAULT 'workflow-v1' COMMENT '检查点状态结构版本' AFTER generation_context_hash,
  ADD COLUMN turn_no INT NOT NULL DEFAULT 0 COMMENT '检查点对应Agent轮次' AFTER state_version;
