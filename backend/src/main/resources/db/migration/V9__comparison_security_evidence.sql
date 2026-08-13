ALTER TABLE stock_evidence_item
  ADD COLUMN subject_code VARCHAR(16) NOT NULL DEFAULT '' COMMENT '证据所属规范化证券代码，空值表示主证券历史证据' AFTER evidence_key,
  ADD COLUMN comparison_snapshot_id CHAR(64) NOT NULL DEFAULT '' COMMENT '可比证券独立快照标识，主证券证据为空' AFTER subject_code,
  ADD INDEX idx_stock_evidence_subject (task_id, subject_code);
