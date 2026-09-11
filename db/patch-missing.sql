-- =========================================================================
-- MMCove 补丁：在已建好的 mmcove 库上执行一次即可（修复 init.sql 早期版本的实体-表漂移）
--   1) ai_role 补 model 列；ai_channel 补 role 列
--   2) 补建 9 张缺失表：system_config / group_tool_config / knowledge_base /
--      knowledge_document / knowledge_chunk / agent_knowledge_base /
--      resolved_entity / token_usage_quantity / usage_duration_statistics
-- 用法： mysql -uroot -p mmcove < db/patch-missing.sql
-- =========================================================================
USE mmcove;

-- ---------- 补列（MySQL 8 不支持 ADD COLUMN IF NOT EXISTS，每条只跑一次） ----------
ALTER TABLE ai_role    ADD COLUMN model VARCHAR(100) DEFAULT NULL COMMENT '默认模型'   AFTER content_length;
ALTER TABLE ai_channel ADD COLUMN role VARCHAR(100)  DEFAULT NULL COMMENT '角色编码'   AFTER group_name;

-- ---------- 系统配置（鉴权开关等；AuthInterceptor 读 KEY_CHAT_AUTH_REQUIRED，空则默认需认证） ----------
CREATE TABLE IF NOT EXISTS system_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key    VARCHAR(128) NOT NULL UNIQUE COMMENT '配置键',
    config_value  TEXT COMMENT '配置值',
    description   VARCHAR(255) DEFAULT NULL COMMENT '说明',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

-- ---------- 分组-工具配置（每个分组启用哪些工具） ----------
CREATE TABLE IF NOT EXISTS group_tool_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64)  NOT NULL COMMENT '分组',
    tool_name   VARCHAR(128) NOT NULL COMMENT '工具名',
    enabled     TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_tool (group_name, tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组工具配置表';

-- ---------- 知识库 ----------
CREATE TABLE IF NOT EXISTS knowledge_base (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    name               VARCHAR(200) NOT NULL COMMENT '知识库名称',
    description        VARCHAR(500) DEFAULT NULL,
    keywords           VARCHAR(500) DEFAULT NULL COMMENT '关键词',
    scene_tags         VARCHAR(500) DEFAULT NULL COMMENT '场景标签',
    embedding_channel  VARCHAR(100) DEFAULT NULL COMMENT '向量渠道',
    collection_name    VARCHAR(200) DEFAULT NULL COMMENT 'Milvus collection',
    dimensions         INT DEFAULT NULL COMMENT '向量维度',
    priority           INT DEFAULT 0,
    tenant_id          BIGINT DEFAULT 0,
    status             INT DEFAULT 1,
    created_at         DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at         DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库表';

-- ---------- 知识库文档 ----------
CREATE TABLE IF NOT EXISTS knowledge_document (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    kb_id       BIGINT NOT NULL COMMENT '知识库ID',
    title       VARCHAR(500) DEFAULT NULL,
    source_url  VARCHAR(1000) DEFAULT NULL,
    mime        VARCHAR(100) DEFAULT NULL,
    status      INT DEFAULT 0 COMMENT '处理状态',
    tenant_id   BIGINT DEFAULT 0,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_kb_id (kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库文档表';

-- ---------- 知识库分块 ----------
CREATE TABLE IF NOT EXISTS knowledge_chunk (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    doc_id       BIGINT NOT NULL COMMENT '文档ID',
    seq          INT NOT NULL DEFAULT 0 COMMENT '分块序号',
    content      MEDIUMTEXT COMMENT '分块文本',
    vector_id    VARCHAR(200) DEFAULT NULL COMMENT '向量ID',
    token_count  INT DEFAULT 0,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_doc_id (doc_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='知识库分块表';

-- ---------- Agent-知识库绑定 ----------
CREATE TABLE IF NOT EXISTS agent_knowledge_base (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id    VARCHAR(64) NOT NULL COMMENT 'AgentID',
    kb_id       BIGINT NOT NULL COMMENT '知识库ID',
    tenant_id   BIGINT DEFAULT 0,
    enabled     TINYINT(1) DEFAULT 1,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_agent_kb (agent_id, kb_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Agent知识库绑定表';

-- ---------- 实体记忆（会话内解析出的实体） ----------
CREATE TABLE IF NOT EXISTS resolved_entity (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id    VARCHAR(64) NOT NULL,
    entity_type   VARCHAR(64) DEFAULT NULL,
    entity_key    VARCHAR(128) DEFAULT NULL,
    display_name  VARCHAR(255) DEFAULT NULL,
    source_tool   VARCHAR(128) DEFAULT NULL,
    expires_at    DATETIME DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session (session_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='实体记忆表';

-- ---------- 设备/终端用量上报（保留表，原设备授权链路已删，但实体仍在） ----------
CREATE TABLE IF NOT EXISTS token_usage_quantity (
    id           BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id      VARCHAR(64) DEFAULT NULL,
    token_key    VARCHAR(64) DEFAULT NULL,
    created_time BIGINT DEFAULT NULL,
    used_quota   INT DEFAULT 0,
    sn           VARCHAR(128) DEFAULT NULL,
    mac          VARCHAR(128) DEFAULT NULL,
    created_at   DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Token用量数量表';

-- ---------- 使用时长统计（原设备授权链路统计，实体仍在） ----------
CREATE TABLE IF NOT EXISTS usage_duration_statistics (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    start_time  BIGINT DEFAULT NULL,
    end_time    BIGINT DEFAULT NULL,
    role_code   VARCHAR(100) DEFAULT NULL,
    role_name   VARCHAR(100) DEFAULT NULL,
    unique_key  VARCHAR(128) DEFAULT NULL,
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='使用时长统计表';
