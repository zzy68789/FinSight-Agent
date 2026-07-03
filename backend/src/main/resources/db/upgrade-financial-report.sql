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
