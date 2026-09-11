# MMCove

自托管的 AI Agent 平台 —— 前后端单仓库。基于 Spring AI 的多渠道、可编排、带 RAG 与配额计费的 AI Agent 后端，外加两套 Vue 3 SPA（AI 问答工作台 + 后台管理）。

## 仓库结构

```
MMCove/
├─ mmcove-*          # 后端 Maven 多模块（com.mmcove.agent）
│  ├─ mmcove-common  # 通用：DTO / 枚举 / 异常 / 上下文 / 加密
│  ├─ mmcove-infra   # 基础设施：MySQL / Redis / SSE / WebSocket
│  ├─ mmcove-llm     # LLM：ChatClient / 网关 / 动态渠道 / 记忆
│  ├─ mmcove-tools   # 工具框架 + 可移植工具
│  ├─ mmcove-core    # 核心：编排 / 路由 / 对话 / 思维链 / 提示词
│  ├─ mmcove-rag     # RAG：Milvus 向量库 / 入库 / 检索
│  └─ mmcove-app     # 应用入口：控制器 / 服务 / 配置 / 鉴权 + 启动类
├─ web/
│  ├─ chat/          # AI 问答工作台（Vite6 + Vue3.5 + TS）
│  └─ admin/         # 后台管理（Vite6 + Vue3.5 + TS）
├─ docker-compose.yml   # MySQL + Redis + Milvus 一键起
└─ build.ps1 / build.sh # 构建两个前端 → 后端 static
```

## 核心能力

- **多渠道 AI 网关**：OpenAI / Anthropic / OpenAI 兼容（智谱 GLM 等）多渠道负载均衡、优先级 + 权重、熔断。
- **Agent 编排**：自研 ReAct 循环、意图路由、思维链、任务拆分。
- **工具框架**：`@Tool` 自动扫描、`@DangerousOperation` 二次确认、SSE 事件观测。
- **RAG 知识库**：Milvus 向量库、文档入库 / 分块 / 检索、场景路由。
- **配额计费**：API Token（`sk-`）、按模型计价、分组倍率、用量审计。
- **OpenAI 兼容接口**：`/v1/chat/completions`、`/v1/models`。
- **自建用户体系**：注册 / 登录 / JWT、角色（用户 / 管理员 / 超管），不依赖外部用户中心。
- **无模板引擎**：Spring Boot 托管两套 Vue SPA，history 模式回落。

## 快速开始

详见 `docs/deployment.md`。概要：

```bash
# 1. 起基础设施
docker compose up -d

# 2. 后端
mvn clean package -DskipTests
MMCOVE_JWT_SECRET=... OPENAI_API_KEY=... java -jar mmcove-app/target/mmcove-app-*.jar

# 3. 前端（开发）
pnpm -C web/chat dev    # http://localhost:4008
pnpm -C web/admin dev   # http://localhost:3000/admin

# 4. 前端构建并接入后端（生产）
./build.ps1             # 产物写入 mmcove-app/src/main/resources/static/{chat,admin}
```

- 聊天端：http://localhost:8808/
- 后台端：http://localhost:8808/admin （默认管理员 `admin` / `admin123`）

## 技术栈

- **后端**：Java 21、Spring Boot、Spring AI、MyBatis-Plus、MySQL、Redis、Milvus
- **前端**：Vue 3、Vite 6、TypeScript、Element Plus、Pinia
