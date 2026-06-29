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
