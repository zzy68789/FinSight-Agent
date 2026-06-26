ALTER TABLE app_user
  ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN last_login_at DATETIME NULL;

CREATE INDEX idx_app_user_role ON app_user(role);
CREATE INDEX idx_app_user_status ON app_user(status);

CREATE TABLE IF NOT EXISTS admin_audit_log (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  admin_user_id BIGINT NOT NULL,
  action VARCHAR(64) NOT NULL,
  target_type VARCHAR(64) NOT NULL,
  target_id BIGINT NOT NULL,
  detail LONGTEXT,
  created_at DATETIME NOT NULL,
  INDEX idx_admin_audit_admin_user_id (admin_user_id),
  INDEX idx_admin_audit_target (target_type, target_id),
  INDEX idx_admin_audit_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
