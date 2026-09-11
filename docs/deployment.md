# 部署与本地启动

## 1. 起基础设施

```bash
cp .env.example .env        # 按需修改密码 / 端口
docker compose up -d
```

将启动 MySQL（:3306）、Redis（:6379）、Milvus（:19530，含 etcd + minio）。

## 2. 初始化数据库

首次启动后建表：

```bash
docker exec -i mmcove-mysql mysql -ummcove -pmmcove mmcove < mmcove-app/src/main/resources/db/schema.sql
```

（种子管理员会在后端首次启动时由 `DataInitializer` 自动创建：`admin` / `admin123`）

## 3. 运行后端

```bash
mvn clean package -DskipTests
MMCOVE_JWT_SECRET=... OPENAI_API_KEY=... java -jar mmcove-app/target/mmcove-app-*.jar
```

或开发期：`mvn -pl mmcove-app spring-boot:run`（配合前端 Vite dev server 代理）。

后端端口 `8808`。健康检查：`http://localhost:8808/actuator/health`。

## 4. 前端

### 开发模式（热更新）

```bash
pnpm -C web/chat install && pnpm -C web/chat dev    # http://localhost:4008
pnpm -C web/admin install && pnpm -C web/admin dev  # http://localhost:3000/admin
```

两个 dev server 都把 `/api`、`/v1`、`/flux-images` 代理到 `localhost:8808`。

### 生产模式（接入后端，单 fat jar）

```bash
./build.ps1          # 或 ./build.sh：构建两端 → static/{chat,admin}
mvn clean package -DskipTests
java -jar mmcove-app/target/mmcove-app-*.jar
```

- 聊天端：http://localhost:8808/
- 后台端：http://localhost:8808/admin

## 5. 默认账号

- 管理员：`admin` / `admin123`（首次启动自动创建，登录后请改密）

## 6. 配置一个 AI 渠道

1. 登录后台 `/admin` → **AI 渠道**：新增一条 OpenAI 兼容渠道（或智谱 GLM `https://open.bigmodel.cn/api/paas/v4`），填 apiKey / 模型。
2. **Token**：审批用户的 API Token（或在聊天端直接用 JWT 免费档）。
3. 回到聊天端 `/` 发起对话。
