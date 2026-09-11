-- ============================================================
-- MMCove 补丁: 产品线体系 + Vibe 流水线提示词工厂
-- 日期: 2026-09-11
-- 说明: 三部分——
--   A) 产品线维度(knowledge_base/api_token 挂产品线,产品线间默认隔离);
--   B) Vibe 6 表(阶段库/流水线模板/序列引用/产品线绑定/执行实例/产物);
--   C) 种子(12 全局阶段 + bug-fix/feature-dev 两条流水线)。
-- 幂等可重复执行(IF NOT EXISTS + INSERT IGNORE)。
-- ============================================================

-- ==================== A) 产品线维度 ====================
-- 1. 产品线表
CREATE TABLE IF NOT EXISTS `product_line` (
    `id`          BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `code`        VARCHAR(64)  NOT NULL COMMENT '产品线编码(唯一;用于 Milvus collection 命名与外部引用)',
    `name`        VARCHAR(128) NOT NULL COMMENT '产品线名称',
    `description` VARCHAR(512) COMMENT '产品线描述',
    `status`      TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`  DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品线';

-- 2. knowledge_base 挂产品线 + 共享标志
--    tenant_id 顺带补 DEFAULT 0: 代码不写该字段, MyBatis-Plus insert 省略 null 列,
--    严格模式下无默认值会导致建库失败。
ALTER TABLE `knowledge_base`
    ADD COLUMN `product_line_id` BIGINT      NULL COMMENT '所属产品线ID' AFTER `tenant_id`,
    ADD COLUMN `is_shared`       TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否共享库:0私有 1共享(所有产品线可读,仅归属线可写)' AFTER `product_line_id`,
    ADD INDEX `idx_product_line` (`product_line_id`, `status`),
    MODIFY COLUMN `tenant_id` BIGINT NOT NULL DEFAULT 0 COMMENT '租户ID(未启用,默认0)';

-- 3. api_token 关联产品线(NULL=未绑定,外部 MCP 只能读共享库)
ALTER TABLE `api_token`
    ADD COLUMN `product_line_id` BIGINT NULL COMMENT '绑定产品线ID(外部MCP调用方身份)' AFTER `group_name`,
    ADD INDEX `idx_product_line` (`product_line_id`);

-- 4. 存量数据: 建「默认产品线」,存量知识库全部归入
INSERT INTO `product_line` (`code`, `name`, `description`)
VALUES ('default', '默认产品线', '初始默认产品线');

UPDATE `knowledge_base`
SET `product_line_id` = (SELECT `id` FROM `product_line` WHERE `code` = 'default')
WHERE `product_line_id` IS NULL;



-- ==================== B) Vibe 6 表 ====================
-- 1. 全局阶段库(跨流水线共享)
CREATE TABLE IF NOT EXISTS `pipeline_stage` (
    `id`              BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `stage_code`      VARCHAR(64)  NOT NULL COMMENT '阶段编码(全局唯一,流水线以此引用;如 root-cause)',
    `name`            VARCHAR(128) NOT NULL COMMENT '阶段名称(如 根因分析)',
    `description`     VARCHAR(512) COMMENT '阶段说明(管理侧查看用)',
    `role_prompt`     TEXT         COMMENT '角色提示词模板,占位符: {{task}}/{{knowledge}}(锚点全文,慎用,多阶段重复注入会撑大提示词;种子模板用引用式)/{{product_line_name}}/{{product_line_desc}}/{{pipeline_name}}/{{prev_outputs}}',
    `sub_agent_count` INT          NOT NULL DEFAULT 1 COMMENT '子agent数(>1 表示并行多视角产出后互审)',
    `deliverable`     VARCHAR(512) COMMENT '产出物要求(一句话,组装进阶段地图)',
    `gate_rule`       VARCHAR(512) COMMENT '门禁定义:进入下一阶段必须满足的条件(自然语言,由外部AI自检)',
    `status`          TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_stage_code` (`stage_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线阶段库(全局共享)';

-- 2. 流水线模板(intent_type 唯一约束 = 一类意图仅一条流水线,选择退化为一次索引查)
CREATE TABLE IF NOT EXISTS `pipeline_template` (
    `id`                   BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `pipeline_code`        VARCHAR(64)  NOT NULL COMMENT '流水线编码(如 bug-fix)',
    `name`                 VARCHAR(128) NOT NULL COMMENT '流水线名称(如 缺陷修复流水线)',
    `intent_type`          VARCHAR(32)  NOT NULL COMMENT '意图类型: BUG_FIX/FEATURE_DEV/REFACTOR/TEST_ENHANCE(同scope内应用层保唯一)',
    `product_line_id`      BIGINT       NULL COMMENT '归属产品线(NULL=全局共享模板;非NULL=该线私有副本,compose选线私有优先)',
    `description`          VARCHAR(512) COMMENT '流水线说明(LLM 意图分类候选描述)',
    `keywords`             VARCHAR(512) COMMENT '意图关键词,逗号分隔(关键词前置匹配)',
    `execution_convention` TEXT         COMMENT '执行约定段(追加在主提示词尾部;空则用服务端内置默认约定)',
    `status`               TINYINT      NOT NULL DEFAULT 1 COMMENT '1启用 0停用',
    `created_at`           DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`           DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_pipeline_code` (`pipeline_code`),
    INDEX `idx_owner_line` (`product_line_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vibe流水线模板';

-- 3. 流水线↔阶段 序列引用表
CREATE TABLE IF NOT EXISTS `pipeline_stage_step` (
    `id`           BIGINT   NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `pipeline_id`  BIGINT   NOT NULL COMMENT '所属流水线(pipeline_template.id)',
    `stage_id`     BIGINT   NOT NULL COMMENT '引用阶段(pipeline_stage.id)',
    `seq`          INT      NOT NULL COMMENT '执行顺序(留间隔10/20/30便于中间插阶段;与pipeline_id联合唯一)',
    `step_params`  JSON     COMMENT '步骤级覆盖(JSON: {"subAgentCount":2,"extraPrompt":"..."};NULL=用阶段默认)',
    `created_at`   DATETIME DEFAULT CURRENT_TIMESTAMP,
    `updated_at`   DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_pipeline_seq` (`pipeline_id`, `seq`),
    INDEX `idx_stage` (`stage_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='流水线阶段序列(引用+顺序+步骤覆盖)';

-- 4. 产品线绑定+覆盖
CREATE TABLE IF NOT EXISTS `product_line_pipeline` (
    `id`              BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `product_line_id` BIGINT      NOT NULL COMMENT '产品线(product_line.id)',
    `pipeline_id`     BIGINT      NOT NULL COMMENT '流水线(pipeline_template.id)',
    `stage_overrides` JSON        COMMENT '阶段覆盖(JSON按stage_code键: {"peer-review":{"enabled":false,"subAgentCount":3}})',
    `extra_context`   TEXT        COMMENT '产品线补充背景段(追加到主提示词产品线背景段;如技术栈/目录约定/发版要求)',
    `enabled`         TINYINT     NOT NULL DEFAULT 1 COMMENT '1该流水线对本线生效 0忽略绑定',
    `created_at`      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `updated_at`      DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_line_pipeline` (`product_line_id`, `pipeline_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='产品线↔流水线 绑定与覆盖';

-- 5. 任务执行实例(防上下文压缩: getRunContext 恢复的底稿)
CREATE TABLE IF NOT EXISTS `vibe_run` (
    `id`                 BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `run_code`           VARCHAR(32)  NOT NULL COMMENT '执行实例短码(如 RUN-A1B2C3;埋入提示词头部供恢复引用)',
    `product_line_id`    BIGINT       NOT NULL COMMENT '产品线(product_line.id)',
    `owner_token_id`     BIGINT       NULL COMMENT '归属调用方(api_token.id;空runId恢复时优先按此隔离,防跨调用方劫持)',
    `pipeline_id`        BIGINT       NOT NULL COMMENT '流水线(pipeline_template.id)',
    `task_text`          TEXT         NOT NULL COMMENT '用户任务原文',
    `intent_type`        VARCHAR(32)  NOT NULL COMMENT '意图分类结果',
    `status`             VARCHAR(16)  NOT NULL DEFAULT 'RUNNING' COMMENT 'RUNNING/COMPLETED/FAILED/ABORTED',
    `current_stage_code` VARCHAR(64)  COMMENT '当前执行到的阶段编码',
    `completed_stages`   TEXT         COMMENT '已完成阶段编码 JSON 数组(权威进度状态源,如 ["reproduce-locate","root-cause"])',
    `effective_stages`   TEXT         COMMENT 'compose时固化的生效阶段完整快照JSON(EffectiveStage列表,应用产品线覆盖后;状态机/校验/恢复的权威基准,脱离活配置)',
    `composed_prompt`    MEDIUMTEXT   COMMENT '首次组装的完整提示词快照(getRunContext恢复底稿)',
    `created_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP,
    `updated_at`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY `uk_run_code` (`run_code`),
    INDEX `idx_line_status` (`product_line_id`, `status`),
    INDEX `idx_owner_running` (`owner_token_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vibe任务执行实例';

-- 6. 阶段产物(reportStage 上报落库;完成后可沉淀回知识库)
CREATE TABLE IF NOT EXISTS `vibe_run_artifact` (
    `id`         BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    `run_id`     BIGINT      NOT NULL COMMENT '执行实例(vibe_run.id)',
    `stage_code` VARCHAR(64) NOT NULL COMMENT '阶段编码(pipeline_stage.stage_code)',
    `title`      VARCHAR(256) NOT NULL COMMENT '产物标题',
    `content`    MEDIUMTEXT  COMMENT '产物内容(文本直存,markdown)',
    `summary`    VARCHAR(512) COMMENT '一句话摘要(可空)',
    `created_at` DATETIME    DEFAULT CURRENT_TIMESTAMP,
    `updated_at` DATETIME    DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX `idx_run` (`run_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Vibe阶段产物';

-- ============================================================


-- ==================== C) 种子数据 ====================
-- 种子数据(幂等: INSERT IGNORE,uk 冲突即跳过)

