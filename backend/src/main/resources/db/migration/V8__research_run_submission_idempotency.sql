ALTER TABLE research_task
  ADD COLUMN client_request_id VARCHAR(64) COMMENT '客户端任务创建幂等键' AFTER owner_id,
  ADD UNIQUE KEY uk_research_task_owner_request (owner_id, client_request_id);
