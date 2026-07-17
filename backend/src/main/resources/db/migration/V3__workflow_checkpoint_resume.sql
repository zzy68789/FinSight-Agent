ALTER TABLE checkpoint
  ADD COLUMN stage VARCHAR(64) NOT NULL DEFAULT 'UNKNOWN' COMMENT '检查点对应的工作流阶段' AFTER task_id,
  ADD COLUMN attempt_no INT NOT NULL DEFAULT 1 COMMENT '阶段执行尝试次数' AFTER stage,
  ADD COLUMN generation_context_hash CHAR(64) COMMENT '检查点对应的报告生成上下文SHA-256' AFTER attempt_no,
  ADD INDEX idx_checkpoint_task_stage_context (task_id, stage, generation_context_hash, id);
