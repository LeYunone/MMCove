-- Mmcove AI Agent 数据库建表脚本
-- MySQL 8+

CREATE DATABASE IF NOT EXISTS mmcove_agent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mmcove_agent;

-- 会话表
CREATE TABLE IF NOT EXISTS conversation (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL UNIQUE,
    tenant_id       BIGINT NOT NULL DEFAULT 0,
    user_id         BIGINT NOT NULL DEFAULT 0,
    title           VARCHAR(255),
    status          VARCHAR(20) DEFAULT 'ACTIVE',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_session_user (user_id, status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 消息表
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
    INDEX idx_msg_session (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 异步任务表
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
    INDEX idx_task_status (status, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 响应格式模板表
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

-- 工具定义表（管理 @Tool 方法的描述、参数、危险标记等）
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

-- AI角色定义表（大屏 AI 问答接口）
CREATE TABLE IF NOT EXISTS ai_role (
    id              BIGINT NOT NULL AUTO_INCREMENT,
    name            VARCHAR(100) NOT NULL COMMENT '角色名称',
    code            VARCHAR(100) NOT NULL COMMENT '角色编码（如 def_translator）',
    content         LONGTEXT COMMENT '系统 prompt 模板（含 {{key}} 占位符）',
    user_content    VARCHAR(1000) DEFAULT '' COMMENT '用户内容模板',
    content_length  INT DEFAULT 0 COMMENT '内容固定长度',
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_code (code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI角色定义表';

-- AI渠道配置表
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
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='AI渠道配置表';

-- 角色-渠道-分组关联表
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

-- 模型能力映射表（负载均衡核心）
CREATE TABLE IF NOT EXISTS ai_ability (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64)  NOT NULL DEFAULT 'default' COMMENT '分组',
    model       VARCHAR(255) NOT NULL                   COMMENT '模型名称',
    channel_id  BIGINT       NOT NULL                   COMMENT '渠道ID',
    enabled     TINYINT(1)   NOT NULL DEFAULT 1         COMMENT '是否启用',
    priority    BIGINT       NOT NULL DEFAULT 0         COMMENT '优先级',
    weight      INT          NOT NULL DEFAULT 10        COMMENT '权重',
    INDEX idx_group_model (group_name, model),
    INDEX idx_channel_id (channel_id),
    UNIQUE KEY uk_group_model_channel (group_name, model, channel_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='模型能力映射表(负载均衡核心)';

-- 模型计费配置表
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

-- 分组倍率配置表
CREATE TABLE IF NOT EXISTS group_ratio_config (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    group_name  VARCHAR(64) NOT NULL UNIQUE COMMENT '分组名称',
    ratio       DOUBLE      NOT NULL DEFAULT 1.0 COMMENT '分组倍率',
    created_at  DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='分组倍率配置表';

-- =====================================================
-- 用户 / API Token / 使用记录（自建用户体系 + 配额计费）
-- =====================================================

-- 用户表（自建用户体系：注册/登录账户，同时承载 API Token 配额）
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

-- API Token 表（OpenAI 兼容 sk- 密钥 + 配额/分组路由）
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

-- API Token 使用记录表（用量审计 / 仪表盘统计）
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
    PRIMARY KEY (`id`),
    INDEX `idx_token_id` (`token_id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Token使用记录表';
