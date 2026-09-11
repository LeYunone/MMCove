-- =========================================================================
-- MMCove 全量建库脚本（MySQL 8+）
-- 用法： mysql -uroot -p < db/init.sql
--   或先连上 MySQL 再：SOURCE db/init.sql;
-- 建库后启动后端即可（DataInitializer 会自动种子 admin/admin123 + mmcove/mmcove123
-- 两个 BCrypt 超管；如设了 OPENAI_API_KEY，还会种子默认 AI 渠道）。
-- 如想换库名：把下面的 mmcove 改成你的库名，并设环境变量 MYSQL_DB=<库名>。
-- =========================================================================

CREATE DATABASE IF NOT EXISTS mmcove DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mmcove;

-- ---------------------- 会话 / 消息 ----------------------
CREATE TABLE IF NOT EXISTS conversation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL UNIQUE,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    user_id         VARCHAR(64) DEFAULT NULL,
    agent_type      VARCHAR(64),
    title           VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS conversation_message (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    role            VARCHAR(20) NOT NULL,
    content         TEXT,
    tool_calls      JSON,
    tool_results    JSON,
    model_name      VARCHAR(50),
    input_tokens    INT,
    output_tokens   INT,
    latency_ms      INT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_msg_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------- Agent 定义 ----------------------
CREATE TABLE IF NOT EXISTS agent_definition (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    agent_id        VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(100) NOT NULL,
    description     VARCHAR(500),
    system_prompt   TEXT,
    tool_group_name VARCHAR(64),
    temperature     DOUBLE DEFAULT 0.7,
    max_tokens      INT DEFAULT 4096,
    is_default      TINYINT(1) DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO agent_definition (agent_id, name, description, system_prompt, temperature, max_tokens, is_default, status) VALUES
('general-assistant', '通用助手', '默认 Agent，处理通用任务，可使用所有工具',
 '你是一个由 MMCove AI Agent 驱动的智能 AI 助手，回答请使用中文。\n\n你可以使用以下工具来完成任务，当用户的问题涉及这些场景时，请主动调用对应工具：\n- getCurrentTime: 获取当前日期和时间\n- queryWeather: 查询城市天气预报\n- calculate: 数学计算（支持加减乘除和括号）\n- calculateHash: 计算文本哈希值（md5/sha256）\n- base64Codec: Base64 编码或解码\n- generateUuid: 生成 UUID\n- timestampConvert: 时间戳与日期互转\n\n重要规则：\n1. 涉及实时数据（时间、天气）必须调用工具，不要用训练数据猜测\n2. 用户明确要求计算哈希、编解码等操作时，使用对应工具\n3. 其他通用问题可以直接回答\n4. 回复保持简洁有用，使用 Markdown 格式',
 0.7, 4096, 1, 'ACTIVE');

INSERT INTO agent_definition (agent_id, name, description, system_prompt, temperature, max_tokens, is_default, status) VALUES
('code-expert', '代码专家', '专注编程问题，代码生成和调试',
 '你是一位资深的软件工程师和编程专家。\n你精通 Java、Python、JavaScript、TypeScript、Go、Rust 等主流编程语言。\n你可以帮助用户编写代码、调试问题、优化性能、设计架构。\n请给出高质量的代码示例和详细的技术解释。\n回答请使用中文，代码注释可以用英文。',
 0.3, 8192, 0, 'ACTIVE');

INSERT INTO agent_definition (agent_id, name, description, system_prompt, temperature, max_tokens, is_default, status) VALUES
('data-analyst', '数据分析师', '专注数据分析和可视化建议',
 '你是一位专业的数据分析师。\n你可以帮助用户进行数据分析、生成 SQL 查询、提供数据可视化建议。\n你熟悉统计分析、机器学习基础、数据清洗和数据建模。\n请给出清晰的分析思路和实用的建议。\n回答请使用中文。',
 0.5, 4096, 0, 'ACTIVE');

-- ---------------------- 异步任务 / 响应模板 / 工具 ----------------------
CREATE TABLE IF NOT EXISTS agent_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64),
    task_type       VARCHAR(50) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    params          JSON,
    result          JSON,
    error_message   TEXT,
    retry_count     INT DEFAULT 0,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    completed_at    DATETIME,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_task_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS response_template (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_code   VARCHAR(64) NOT NULL UNIQUE,
    template_name   VARCHAR(100) NOT NULL,
    scope           VARCHAR(20) NOT NULL,
    scene_type      VARCHAR(50),
    tool_name       VARCHAR(64),
    agent_id        VARCHAR(64),
    template_content TEXT NOT NULL,
    priority        INT DEFAULT 0,
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

INSERT INTO response_template (template_code, template_name, scope, scene_type, template_content, priority, status) VALUES
('scene-general', '通用输出格式', 'SCENE', 'GENERAL',
 '【通用输出规范】\n1. 使用 Markdown 格式组织回答\n2. 优先使用表格展示结构化数据\n3. 关键信息使用 **加粗** 标注\n4. 多个要点使用有序或无序列表\n5. 避免大段纯文本，合理分段并使用标题',
 10, 'ACTIVE'),
('scene-query-result', '查询结果格式', 'SCENE', 'QUERY_RESULT',
 '【查询结果输出规范】\n1. 使用表格展示查询结果数据\n2. 表格需包含表头，字段名使用中文\n3. 在表格上方注明数据总量，如“共查询到 N 条记录”\n4. 空结果时给出友好提示和建议\n5. 数据量大时展示关键字段并提示可查看完整数据',
 20, 'ACTIVE'),
('scene-error', '错误提示格式', 'SCENE', 'ERROR',
 '【错误提示输出规范】\n1. 用简洁的语言说明错误原因\n2. 使用 > 引用块标注错误信息\n3. 给出可能的解决方案或建议（使用列表）\n4. 如需用户操作，明确说明操作步骤\n5. 语气友好，避免使用技术术语吓到用户',
 20, 'ACTIVE'),
('scene-confirm', '操作确认格式', 'SCENE', 'CONFIRM',
 '【操作确认输出规范】\n1. 使用列表列出即将执行的操作详情\n2. 每个操作项包含：操作名称、目标对象、预期结果\n3. 使用 > 引用块标注 **需要用户确认** 的提示\n4. 明确说明确认后才会执行，取消则不执行',
 15, 'ACTIVE'),
('scene-list', '列表数据格式', 'SCENE', 'LIST',
 '【列表数据输出规范】\n1. 使用表格展示列表数据，第一列为序号列\n2. 表格底部注明总数，如“共 N 条记录”\n3. 每条记录的关键字段使用 **加粗** 标注\n4. 数据较多时，展示前 10 条并提示可查看更多',
 15, 'ACTIVE');

CREATE TABLE IF NOT EXISTS tool_definition (
    id                   BIGINT AUTO_INCREMENT PRIMARY KEY,
    tool_name            VARCHAR(128) NOT NULL UNIQUE,
    description          TEXT NOT NULL,
    input_schema         JSON,
    dangerous            TINYINT(1) DEFAULT 0,
    status               VARCHAR(20) DEFAULT 'ACTIVE',
    source_class         VARCHAR(255),
    original_description TEXT,
    created_at           DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at           DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------- AI 角色 / 渠道 / 负载均衡 / 计费 ----------------------
CREATE TABLE IF NOT EXISTS ai_role (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '角色名称',
    code            VARCHAR(100) NOT NULL COMMENT '角色编码（如 def_translator）',
    content         LONGTEXT COMMENT '系统 prompt 模板（含 {{key}} 占位符）',
    user_content    VARCHAR(1000) DEFAULT '' COMMENT '用户内容模板',
    content_length  INT DEFAULT 0 COMMENT '内容固定长度',
    model           VARCHAR(100) DEFAULT NULL COMMENT '默认模型',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI角色定义表';

CREATE TABLE IF NOT EXISTS ai_channel (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '渠道名称',
    models          TEXT COMMENT '模型列表（逗号分隔）',
    status          INT DEFAULT 1 COMMENT '状态（1=启用）',
    type            INT DEFAULT 1 COMMENT '渠道类型: 1=OpenAI, 2=Anthropic',
    api_key         VARCHAR(512) DEFAULT '' COMMENT 'API密钥',
    base_url        VARCHAR(512) DEFAULT '' COMMENT 'API基础URL',
    weight          INT DEFAULT 10 COMMENT '权重(负载均衡)',
    priority        BIGINT DEFAULT 0 COMMENT '优先级(越大越高)',
    auto_ban        TINYINT(1) DEFAULT 1 COMMENT '是否自动禁用: 0=否, 1=是',
    model_mapping   TEXT DEFAULT NULL COMMENT '模型名映射JSON',
    response_time   INT DEFAULT 0 COMMENT '最近响应时间(ms)',
    used_quota      BIGINT DEFAULT 0 COMMENT '已使用Token数',
    is_system       TINYINT(1) DEFAULT 0 COMMENT '是否系统默认渠道(yml配置)',
    group_name      VARCHAR(64) DEFAULT 'default' COMMENT '所属分组',
    role            VARCHAR(100) DEFAULT NULL COMMENT '角色编码',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI渠道配置表';

CREATE TABLE IF NOT EXISTS ai_role_channel_group (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    group_name      VARCHAR(64) NOT NULL DEFAULT 'default' COMMENT '分组',
    channel_id      BIGINT NOT NULL COMMENT '渠道ID',
    role_code       VARCHAR(50) NOT NULL COMMENT '角色编码',
    priority        BIGINT DEFAULT 0 COMMENT '优先级',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_group_role (group_name, role_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='角色-渠道-分组关联表';

CREATE TABLE IF NOT EXISTS ai_ability (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '分组',
    model       VARCHAR(255) NOT NULL                   COMMENT '模型名称',
    channel_id  BIGINT       NOT NULL                   COMMENT '渠道ID',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1         COMMENT '是否启用',
    priority    BIGINT       NOT NULL DEFAULT 0         COMMENT '优先级',
    weight      INT          NOT NULL DEFAULT 10        COMMENT '权重',
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_group_model (group_name, model),
    INDEX idx_channel_id (channel_id),
    UNIQUE KEY uk_group_model_channel (group_name, model, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型能力映射表(负载均衡核心)';

CREATE TABLE IF NOT EXISTS model_price_config (
    id                BIGINT AUTO_INCREMENT PRIMARY KEY,
    model_name        VARCHAR(255) NOT NULL UNIQUE      COMMENT '模型名称',
    input_ratio       DOUBLE       NOT NULL DEFAULT 1.0 COMMENT '输入倍率(相对基础价格)',
    output_ratio      DOUBLE       NOT NULL DEFAULT 1.0 COMMENT '输出倍率(相对基础价格)',
    use_fixed_price   TINYINT(1)   NOT NULL DEFAULT 0   COMMENT '是否使用固定价格: 0=倍率, 1=固定',
    fixed_price       INT          DEFAULT 0             COMMENT '固定价格(Token数/次)',
    enabled           TINYINT(1)   NOT NULL DEFAULT 1   COMMENT '是否启用',
    created_at        DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at        DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_model (model_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型计费配置表';

CREATE TABLE IF NOT EXISTS group_ratio_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64) NOT NULL UNIQUE COMMENT '分组名称',
    ratio       DOUBLE      NOT NULL DEFAULT 1.0 COMMENT '分组倍率',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组倍率配置表';

-- ---------------------- 用户 / API Token / 使用记录 ----------------------
-- 注：管理员账号不在此处 INSERT，后端首次启动时 DataInitializer 用 BCrypt 自动种子
--     admin/admin123 与 mmcove/mmcove123（均为超级管理员）。

CREATE TABLE IF NOT EXISTS `api_user` (
    `id`           VARCHAR(64) NOT NULL COMMENT '主键ID（雪花算法生成）',
    `username`     VARCHAR(100) NOT NULL COMMENT '用户名',
    `password`     VARCHAR(255) NOT NULL COMMENT '密码（BCrypt）',
    `role`         TINYINT NOT NULL DEFAULT 1 COMMENT '角色: 1=普通用户, 10=管理员, 100=超级管理员',
    `status`       TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1=启用, 2=禁用',
    `quota`        BIGINT NOT NULL DEFAULT 0 COMMENT '用户配额',
    `access_token` VARCHAR(255) DEFAULT NULL COMMENT '访问令牌',
    `deleted_at`   DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`   DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

CREATE TABLE IF NOT EXISTS `api_token` (
    `id`                     BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id`                VARCHAR(64) NOT NULL DEFAULT '' COMMENT '所属用户ID',
    `token_key`              VARCHAR(64) NOT NULL COMMENT 'Token密钥 sk-xxx',
    `status`                 INT NOT NULL DEFAULT 1 COMMENT '状态: 0=待审核 1=启用 2=禁用 3=过期 4=额度用尽 5=已拒绝',
    `name`                   VARCHAR(100) NOT NULL DEFAULT '' COMMENT 'Token名称',
    `created_time`           BIGINT NOT NULL DEFAULT 0 COMMENT '创建时间戳',
    `prohibited_time`        BIGINT NOT NULL DEFAULT 0 COMMENT '禁用时间戳',
    `accessed_time`          BIGINT NOT NULL DEFAULT 0 COMMENT '最后访问时间戳',
    `expired_time`           BIGINT NOT NULL DEFAULT -1 COMMENT '过期时间戳，-1永不过期',
    `remain_quota`           INT NOT NULL DEFAULT 0 COMMENT '剩余配额',
    `unlimited_quota`        TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否无限配额',
    `model_limits_enabled`   TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否启用模型限制',
    `model_limits`           VARCHAR(1024) NOT NULL DEFAULT '' COMMENT '允许的模型列表',
    `allow_ips`              VARCHAR(1024) NOT NULL DEFAULT '' COMMENT 'IP白名单',
    `used_quota`             INT NOT NULL DEFAULT 0 COMMENT '已使用配额',
    `group_name`             VARCHAR(100) NOT NULL DEFAULT '' COMMENT '分组名称',
    `apply_reason`           VARCHAR(500) DEFAULT NULL COMMENT '申请理由',
    `review_remark`          VARCHAR(500) DEFAULT NULL COMMENT '审批备注',
    `reviewed_by`            VARCHAR(64) DEFAULT NULL COMMENT '审批人',
    `reviewed_time`          BIGINT DEFAULT NULL COMMENT '审批时间戳',
    `unique_key`             VARCHAR(128) DEFAULT NULL COMMENT '唯一键',
    `source`                 VARCHAR(32) DEFAULT NULL COMMENT '来源',
    `fingerprint`            VARCHAR(255) DEFAULT NULL COMMENT '指纹',
    `use_verification`       VARCHAR(64) DEFAULT NULL COMMENT '使用验证',
    `use_verification_time`  BIGINT DEFAULT NULL COMMENT '使用验证时间戳',
    `sn`                     VARCHAR(128) DEFAULT NULL COMMENT '序列号',
    `mac`                    VARCHAR(128) DEFAULT NULL COMMENT 'MAC地址',
    `deleted_at`             DATETIME DEFAULT NULL COMMENT '软删除时间',
    `created_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`             DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_token_key` (`token_key`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_name` (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Token密钥表';

CREATE TABLE IF NOT EXISTS `api_token_usage_log` (
    `id`                  BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `token_id`            BIGINT DEFAULT NULL COMMENT 'Token ID',
    `token_key`           VARCHAR(64) DEFAULT NULL COMMENT 'Token密钥(脱敏)',
    `model`               VARCHAR(100) NOT NULL DEFAULT '' COMMENT '调用的模型',
    `input_tokens`        INT NOT NULL DEFAULT 0 COMMENT '输入Token数',
    `output_tokens`       INT NOT NULL DEFAULT 0 COMMENT '输出Token数',
    `quota_used`          INT NOT NULL DEFAULT 0 COMMENT '消耗配额',
    `ip_address`          VARCHAR(50) DEFAULT NULL COMMENT '请求IP',
    `request_path`        VARCHAR(255) DEFAULT NULL COMMENT '请求路径',
    `request_method`      VARCHAR(20) DEFAULT NULL COMMENT '请求方法',
    `response_time_ms`    INT DEFAULT NULL COMMENT '响应时间(毫秒)',
    `request_start_time`  DATETIME DEFAULT NULL COMMENT '请求开始时间',
    `request_end_time`    DATETIME DEFAULT NULL COMMENT '请求结束时间',
    `input_text_length`   INT DEFAULT NULL COMMENT '输入文本长度',
    `output_text_length`  INT DEFAULT NULL COMMENT '输出文本长度',
    `status_code`         INT DEFAULT NULL COMMENT '响应状态码',
    `error_message`       TEXT DEFAULT NULL COMMENT '错误信息',
    `channel_id`          BIGINT DEFAULT NULL COMMENT '渠道ID',
    `user_id`             VARCHAR(64) DEFAULT NULL COMMENT '用户ID',
    `group_name`          VARCHAR(100) DEFAULT NULL COMMENT '分组名称',
    `is_stream`           TINYINT(1) DEFAULT NULL COMMENT '是否流式',
    `created_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `updated_at`          DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    INDEX `idx_token_id` (`token_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Token使用记录表';

-- ---------------------- ReAct 思维链 / 任务拆分 / SSE ----------------------
CREATE TABLE IF NOT EXISTS sse_event_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    sub_task_id     VARCHAR(64),
    data            JSON NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_sse_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS thinking_turn (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    sub_task_id     VARCHAR(64),
    turn_index      INT NOT NULL DEFAULT 0,
    thought         TEXT,
    action_tool     VARCHAR(64),
    action_args     JSON,
    observation     TEXT,
    status          VARCHAR(20) DEFAULT 'THINKING',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_thinking_session (session_id, turn_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS sub_task (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    task_plan_id    VARCHAR(64),
    task_index      INT NOT NULL DEFAULT 0,
    title           VARCHAR(255) NOT NULL,
    status          VARCHAR(20) DEFAULT 'PENDING',
    parallel        TINYINT(1) DEFAULT 0,
    result          TEXT,
    error_message   TEXT,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_subtask_session (session_id, task_index)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- ---------------------- 系统配置 / 分组工具 / 知识库 / 实体记忆 / 用量统计 ----------------------
CREATE TABLE IF NOT EXISTS system_config (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    config_key    VARCHAR(128) NOT NULL UNIQUE COMMENT '配置键',
    config_value  TEXT COMMENT '配置值',
    description   VARCHAR(255) DEFAULT NULL COMMENT '说明',
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统配置表';

CREATE TABLE IF NOT EXISTS group_tool_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64)  NOT NULL COMMENT '分组',
    tool_name   VARCHAR(128) NOT NULL COMMENT '工具名',
    enabled     TINYINT(1)   DEFAULT 1 COMMENT '是否启用',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY uk_group_tool (group_name, tool_name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组工具配置表';

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

-- ---------------------- 定期清理（需 event_scheduler=ON）----------------------
DELIMITER //
CREATE EVENT IF NOT EXISTS evt_cleanup_sse_events
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP + INTERVAL 1 HOUR
DO BEGIN
    DELETE FROM sse_event_record WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
    DELETE FROM thinking_turn WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
END //
DELIMITER ;
