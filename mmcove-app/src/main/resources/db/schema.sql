-- Mmcove AI Agent 数据库建表脚本
-- MySQL 8+

CREATE DATABASE IF NOT EXISTS mmcove_agent DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE mmcove_agent;

-- 会话表
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

-- Agent 定义表
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

-- 初始 Agent 数据
INSERT INTO agent_definition (agent_id, name, description, system_prompt, temperature, max_tokens, is_default, status) VALUES
('general-assistant', '通用助手', '默认 Agent，处理通用任务，可使用所有工具',
 '你是一个由 Mmcove AI Agent 驱动的智能 AI 助手，回答请使用中文。\n\n你可以使用以下工具来完成任务，当用户的问题涉及这些场景时，请主动调用对应工具：\n- getCurrentTime: 获取当前日期和时间\n- queryWeather: 查询城市天气预报\n- calculate: 数学计算（支持加减乘除和括号）\n- calculateHash: 计算文本哈希值（md5/sha256）\n- base64Codec: Base64 编码或解码\n- generateUuid: 生成 UUID\n- timestampConvert: 时间戳与日期互转\n- webSearch: 搜索互联网信息\n\n重要规则：\n1. 涉及实时数据（时间、天气）必须调用工具，不要用训练数据猜测\n2. 用户明确要求计算哈希、编解码等操作时，使用对应工具\n3. 其他通用问题可以直接回答\n4. 回复保持简洁有用',
 0.7, 4096, 1, 'ACTIVE');

INSERT INTO agent_definition (agent_id, name, description, system_prompt, temperature, max_tokens, is_default, status) VALUES
('code-expert', '代码专家', '专注编程问题，代码生成和调试',
 '你是一位资深的软件工程师和编程专家。\n你精通 Java、Python、JavaScript、TypeScript、Go、Rust 等主流编程语言。\n你可以帮助用户编写代码、调试问题、优化性能、设计架构。\n请给出高质量的代码示例和详细的技术解释。\n回答请使用中文，代码注释可以用英文。',
 0.3, 8192, 0, 'ACTIVE');

INSERT INTO agent_definition (agent_id, name, description, system_prompt, temperature, max_tokens, is_default, status) VALUES
('data-analyst', '数据分析师', '专注数据分析和可视化建议',
 '你是一位专业的数据分析师。\n你可以帮助用户进行数据分析、生成 SQL 查询、提供数据可视化建议。\n你熟悉统计分析、机器学习基础、数据清洗和数据建模。\n请给出清晰的分析思路和实用的建议。\n回答请使用中文。',
 0.5, 4096, 0, 'ACTIVE');

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

-- ===== SCENE 模板（通用场景格式） =====

INSERT INTO response_template (template_code, template_name, scope, scene_type, template_content, priority, status) VALUES
('scene-general', '通用输出格式', 'SCENE', 'GENERAL',
 '【通用输出规范】\n1. 使用 Markdown 格式组织回答\n2. 优先使用表格展示结构化数据\n3. 关键信息使用 **加粗** 标注\n4. 多个要点使用有序或无序列表\n5. 避免大段纯文本，合理分段并使用标题',
 10, 'ACTIVE');

INSERT INTO response_template (template_code, template_name, scope, scene_type, template_content, priority, status) VALUES
('scene-query-result', '查询结果格式', 'SCENE', 'QUERY_RESULT',
 '【查询结果输出规范】\n1. 使用表格展示查询结果数据\n2. 表格需包含表头，字段名使用中文\n3. 在表格上方注明数据总量，如"共查询到 N 条记录"\n4. 空结果时给出友好提示和建议\n5. 数据量大时展示关键字段并提示可查看完整数据',
 20, 'ACTIVE');

INSERT INTO response_template (template_code, template_name, scope, scene_type, template_content, priority, status) VALUES
('scene-error', '错误提示格式', 'SCENE', 'ERROR',
 '【错误提示输出规范】\n1. 用简洁的语言说明错误原因\n2. 使用 > 引用块标注错误信息\n3. 给出可能的解决方案或建议（使用列表）\n4. 如需用户操作，明确说明操作步骤\n5. 语气友好，避免使用技术术语吓到用户',
 20, 'ACTIVE');

INSERT INTO response_template (template_code, template_name, scope, scene_type, template_content, priority, status) VALUES
('scene-confirm', '操作确认格式', 'SCENE', 'CONFIRM',
 '【操作确认输出规范】\n1. 使用列表列出即将执行的操作详情\n2. 每个操作项包含：操作名称、目标对象、预期结果\n3. 使用 > 引用块标注 **需要用户确认** 的提示\n4. 明确说明确认后才会执行，取消则不执行',
 15, 'ACTIVE');

INSERT INTO response_template (template_code, template_name, scope, scene_type, template_content, priority, status) VALUES
('scene-list', '列表数据格式', 'SCENE', 'LIST',
 '【列表数据输出规范】\n1. 使用表格展示列表数据，第一列为序号列\n2. 表格底部注明总数，如"共 N 条记录"\n3. 每条记录的关键字段使用 **加粗** 标注\n4. 数据较多时，展示前 10 条并提示可查看更多',
 15, 'ACTIVE');

-- ===== TOOL 模板（工具结果格式） =====
-- TOOL 模板：按需为工具定制输出格式在此追加。
-- 如需为保留的工具（知识库检索/文生图/UI 渲染等）定制输出格式，在此追加。

-- ===== ReAct 思维链 + 任务拆分 + SSE 增强 =====

-- SSE 事件记录表
CREATE TABLE IF NOT EXISTS sse_event_record (
    id              BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id      VARCHAR(64) NOT NULL,
    event_id        VARCHAR(64) NOT NULL,
    event_type      VARCHAR(32) NOT NULL,
    sub_task_id     VARCHAR(64),
    data            JSON NOT NULL,
    created_at      DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_sse_session_created (session_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 思考轮次表
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

-- 子任务表
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

-- ===== 定期清理事件（MySQL Event Scheduler，保留 7 天） =====
-- 注意：需要确保 event_scheduler=ON（SET GLOBAL event_scheduler = ON;）

DELIMITER //
CREATE EVENT IF NOT EXISTS evt_cleanup_sse_events
ON SCHEDULE EVERY 1 DAY
STARTS CURRENT_TIMESTAMP + INTERVAL 1 HOUR
DO BEGIN
    DELETE FROM sse_event_record WHERE created_at < DATE_SUB(NOW(), INTERVAL 7 DAY);
    DELETE FROM thinking_turn WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY);
END //
DELIMITER ;
