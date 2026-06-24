CREATE TABLE IF NOT EXISTS research_task (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  thread_id VARCHAR(64) NOT NULL,
  query LONGTEXT NOT NULL,
  search_mode VARCHAR(32) NOT NULL,
  status VARCHAR(32) NOT NULL,
  revision_number INT NOT NULL DEFAULT 0,
  created_at DATETIME NOT NULL,
  updated_at DATETIME NOT NULL,
  INDEX idx_research_task_thread_id (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS agent_step_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  step_name VARCHAR(64) NOT NULL,
  input_snapshot LONGTEXT,
  output_snapshot LONGTEXT,
  status VARCHAR(32) NOT NULL,
  error_message LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_agent_step_log_task_id (task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS report (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT,
  thread_id VARCHAR(64) NOT NULL,
  content LONGTEXT NOT NULL,
  version INT NOT NULL DEFAULT 1,
  review_status VARCHAR(32),
  critique LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_report_thread_id (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS checkpoint (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  thread_id VARCHAR(64) NOT NULL,
  task_id BIGINT,
  state_json LONGTEXT NOT NULL,
  created_at DATETIME NOT NULL,
  INDEX idx_checkpoint_thread_id (thread_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
