CREATE TABLE IF NOT EXISTS app_user (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  username VARCHAR(64) NOT NULL COMMENT '唯一登录用户名',
  email VARCHAR(128) COMMENT '用户邮箱地址',
  password_hash VARCHAR(255) NOT NULL COMMENT '加密后的登录密码',
  role VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '用户角色，如USER或ADMIN',
  status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户账号状态',
  last_login_at DATETIME COMMENT '最近一次成功登录时间',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  updated_at DATETIME NOT NULL COMMENT '记录最后更新时间',
  UNIQUE KEY uk_app_user_username (username),
  INDEX idx_app_user_role (role),
  INDEX idx_app_user_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS research_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  owner_id BIGINT NOT NULL DEFAULT 0 COMMENT '任务所属用户ID',
  thread_id VARCHAR(64) NOT NULL COMMENT '会话线程ID',
  query LONGTEXT NOT NULL COMMENT '原始调研问题',
  search_mode VARCHAR(32) NOT NULL COMMENT '调研工作流使用的搜索模式',
  status VARCHAR(32) NOT NULL COMMENT '任务执行状态',
  revision_number INT NOT NULL DEFAULT 0 COMMENT '当前评审修订次数',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  updated_at DATETIME NOT NULL COMMENT '记录最后更新时间',
  INDEX idx_research_task_owner_id (owner_id),
  INDEX idx_research_task_thread_id (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_step_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  task_id BIGINT NOT NULL COMMENT '关联的调研任务ID',
  step_name VARCHAR(64) NOT NULL COMMENT 'Agent工作流步骤名称',
  input_snapshot LONGTEXT COMMENT '序列化后的步骤输入快照',
  output_snapshot LONGTEXT COMMENT '序列化后的步骤输出快照',
  status VARCHAR(32) NOT NULL COMMENT '步骤执行状态',
  error_message LONGTEXT COMMENT '步骤失败时的错误信息',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_agent_step_log_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  owner_id BIGINT NOT NULL DEFAULT 0 COMMENT '报告所属用户ID',
  task_id BIGINT COMMENT '关联的调研任务ID',
  thread_id VARCHAR(64) NOT NULL COMMENT '会话线程ID',
  content LONGTEXT NOT NULL COMMENT '生成的报告内容',
  version INT NOT NULL DEFAULT 1 COMMENT '同一线程下的报告版本号',
  review_status VARCHAR(32) COMMENT '报告评审结果',
  critique LONGTEXT COMMENT '评审意见或修订反馈',
  favorite TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否标记为收藏',
  indexed_at DATETIME COMMENT '报告被索引用于检索的时间',
  deleted_at DATETIME COMMENT '软删除时间',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_report_owner_id (owner_id),
  INDEX idx_report_thread_id (thread_id),
  INDEX idx_report_favorite (favorite),
  INDEX idx_report_deleted_at (deleted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checkpoint (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  thread_id VARCHAR(64) NOT NULL COMMENT '会话线程ID',
  task_id BIGINT COMMENT '关联的调研任务ID',
  state_json LONGTEXT NOT NULL COMMENT '序列化后的工作流状态快照',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_checkpoint_thread_id (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS admin_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  admin_user_id BIGINT NOT NULL COMMENT '执行操作的管理员用户ID',
  action VARCHAR(64) NOT NULL COMMENT '管理员操作名称',
  target_type VARCHAR(64) NOT NULL COMMENT '目标资源类型',
  target_id BIGINT NOT NULL COMMENT '目标资源ID',
  detail LONGTEXT COMMENT '补充审计详情',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_admin_audit_admin_user_id (admin_user_id),
  INDEX idx_admin_audit_target (target_type, target_id),
  INDEX idx_admin_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_analysis_snapshot (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  owner_id BIGINT NOT NULL DEFAULT 0 COMMENT '快照所属用户ID',
  task_id BIGINT NOT NULL COMMENT '关联的股票分析任务ID',
  thread_id VARCHAR(64) NOT NULL COMMENT '会话线程ID',
  ticker VARCHAR(16) NOT NULL COMMENT '六位股票代码',
  exchange_code VARCHAR(8) NOT NULL COMMENT '交易所代码，如SH或SZ',
  company_name VARCHAR(128) NOT NULL COMMENT '公司名称',
  industry VARCHAR(128) NOT NULL COMMENT '所属行业',
  report_period VARCHAR(32) NOT NULL COMMENT '报告期口径',
  search_mode VARCHAR(32) NOT NULL COMMENT '数据搜索模式',
  snapshot_json LONGTEXT NOT NULL COMMENT '本次报告生成使用的数据快照JSON',
  status VARCHAR(32) NOT NULL COMMENT '快照采集状态',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  updated_at DATETIME NOT NULL COMMENT '记录最后更新时间',
  INDEX idx_stock_snapshot_owner_task (owner_id, task_id),
  INDEX idx_stock_snapshot_ticker (ticker, exchange_code),
  INDEX idx_stock_snapshot_thread (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_evidence_item (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  snapshot_id BIGINT NOT NULL COMMENT '关联的数据快照ID',
  task_id BIGINT NOT NULL COMMENT '关联的股票分析任务ID',
  source_type VARCHAR(64) NOT NULL COMMENT '证据来源类型',
  source_name VARCHAR(255) NOT NULL COMMENT '证据来源名称',
  url VARCHAR(1024) COMMENT '网页证据URL',
  page_number INT COMMENT 'PDF或报告页码',
  report_period VARCHAR(32) COMMENT '证据对应报告期',
  metric_name VARCHAR(128) COMMENT '证据关联指标名称',
  raw_value DECIMAL(24,6) COMMENT '原始数值',
  normalized_value DECIMAL(24,6) COMMENT '标准化后的数值',
  excerpt LONGTEXT COMMENT '证据片段',
  confidence DECIMAL(8,6) COMMENT '证据置信度',
  as_of_time DATETIME COMMENT '证据截至时间',
  issue_code VARCHAR(64) COMMENT '数据问题标记，如DATA_MISSING',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_stock_evidence_snapshot (snapshot_id),
  INDEX idx_stock_evidence_task (task_id),
  INDEX idx_stock_evidence_metric (metric_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_metric_result (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  snapshot_id BIGINT NOT NULL COMMENT '关联的数据快照ID',
  task_id BIGINT NOT NULL COMMENT '关联的股票分析任务ID',
  metric_name VARCHAR(128) NOT NULL COMMENT '财务指标名称',
  formula VARCHAR(512) NOT NULL COMMENT '指标计算公式说明',
  metric_value DECIMAL(24,6) COMMENT '指标计算结果数值',
  display_value VARCHAR(64) NOT NULL COMMENT '报告展示值',
  status VARCHAR(32) NOT NULL COMMENT '指标计算状态',
  reason VARCHAR(512) COMMENT '缺失或异常原因',
  evidence_refs LONGTEXT COMMENT '参与计算的证据字段JSON',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_stock_metric_snapshot (snapshot_id),
  INDEX idx_stock_metric_task (task_id),
  INDEX idx_stock_metric_name (metric_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS stock_bad_case_feedback (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  owner_id BIGINT NOT NULL DEFAULT 0 COMMENT '反馈所属用户ID',
  task_id BIGINT NOT NULL COMMENT '关联的股票分析任务ID',
  feedback_type VARCHAR(64) NOT NULL COMMENT '反馈类型，如数字错、引用错、逻辑错或信息过期',
  detail LONGTEXT COMMENT '用户补充的问题描述',
  replay_snapshot_json LONGTEXT COMMENT '提交反馈时绑定的回放快照JSON',
  created_at DATETIME NOT NULL COMMENT '记录创建时间',
  INDEX idx_stock_feedback_owner_task (owner_id, task_id),
  INDEX idx_stock_feedback_type (feedback_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

