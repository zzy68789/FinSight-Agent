-- 已执行 Flyway V1～V5、但临时关闭 Flyway 的环境可手动执行本脚本。
ALTER TABLE research_task
  ADD COLUMN event_sequence BIGINT NOT NULL DEFAULT 0 COMMENT 'Agent事件任务内单调序号' AFTER lease_epoch;

CREATE TABLE IF NOT EXISTS agent_planner_call (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  task_id BIGINT NOT NULL COMMENT '关联的Research Agent任务ID',
  decision_type VARCHAR(32) NOT NULL COMMENT 'CREATE_PLAN、NEXT_ACTION或REPLAN',
  requested_model VARCHAR(32) NOT NULL COMMENT '请求的模型档位',
  actual_model VARCHAR(128) COMMENT 'Provider实际返回的模型名称',
  input_tokens INT NOT NULL DEFAULT 0 COMMENT '本次Planner输入Token数',
  output_tokens INT NOT NULL DEFAULT 0 COMMENT '本次Planner输出Token数',
  duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '本次Planner调用耗时毫秒数',
  structure_attempts INT NOT NULL DEFAULT 0 COMMENT '结构化输出尝试次数',
  structured_valid TINYINT(1) NOT NULL DEFAULT 0 COMMENT '最终输出是否通过结构校验',
  route_correct TINYINT(1) NOT NULL DEFAULT 0 COMMENT '动作是否通过确定性路由校验',
  degraded TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否使用确定性降级',
  degraded_reason VARCHAR(128) COMMENT '稳定降级原因',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_agent_planner_call_task (task_id, id),
  INDEX idx_agent_planner_call_model (requested_model, decision_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_event_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  schema_version VARCHAR(32) NOT NULL COMMENT 'Agent事件结构版本',
  task_id BIGINT NOT NULL COMMENT '关联的Research Agent任务ID',
  thread_id VARCHAR(64) NOT NULL COMMENT '会话线程ID',
  sequence_no BIGINT NOT NULL COMMENT '任务内单调事件序号',
  event_id VARCHAR(64) NOT NULL COMMENT '事件全局唯一标识',
  turn_no INT NOT NULL DEFAULT 0 COMMENT '事件对应Agent轮次',
  event_type VARCHAR(64) NOT NULL COMMENT '事件类型',
  status VARCHAR(32) NOT NULL COMMENT '事件执行状态',
  payload_json LONGTEXT NOT NULL COMMENT '事件业务负载JSON',
  error_message LONGTEXT COMMENT '事件关联的失败说明',
  duration_ms BIGINT NOT NULL DEFAULT 0 COMMENT '事件关联步骤耗时毫秒数',
  published_at DATETIME COMMENT '事件投递到运行态通道的时间',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  UNIQUE KEY uk_agent_event_task_sequence (task_id, sequence_no),
  UNIQUE KEY uk_agent_event_id (event_id),
  INDEX idx_agent_event_unpublished (published_at, id),
  INDEX idx_agent_event_task (task_id, sequence_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
