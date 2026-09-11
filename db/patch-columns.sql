-- =========================================================================
-- MMCove 补丁2：补 BaseEntity 漂移的 updated_at（及 ai_ability 的 created_at）
-- 实体继承 BaseEntity（含 createdAt/updatedAt），但早期 init.sql 部分表漏了这些列。
-- 用法： mysql -uroot -p mmcove < db/patch-columns.sql
-- =========================================================================
USE mmcove;

ALTER TABLE conversation_message ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE agent_task           ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE sse_event_record     ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
ALTER TABLE api_token_usage_log  ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;

-- ai_ability 早期完全没有时间列，补 created_at + updated_at
ALTER TABLE ai_ability ADD COLUMN created_at DATETIME DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE ai_ability ADD COLUMN updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP;
