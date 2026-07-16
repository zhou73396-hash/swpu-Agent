# swpu-agent

基于 Java 17、Spring Boot 3、MyBatis-Plus、MySQL、Redis 和 JWT 的 Java AI Agent 后端网关，重点解决外部长耗时服务的稳定、安全接入。

## 项目职责边界

本仓库主要维护 Java Gateway。

Java Gateway 负责：

- 用户认证与刷新会话。
- HTTP/SSE 请求转发。
- 外部 Agent 调用和接口契约。
- 超时、HTTP 错误和协议异常转换。
- 流式任务生命周期和上游调用取消。

真实 Python Agent 的内部工作流由小组其他成员开发，不属于本仓库的主要维护范围。为保证 Java Gateway 可以独立运行和测试，本项目提供最小 Mock Agent，用于模拟正常响应、SSE、超时、HTTP 500、协议异常和连接中断。

## 当前真实能力

- 邮箱验证码发送、登录和注册。
- JWT Access Token 与可轮换 Refresh Token 认证。
- Refresh Token 哈希存储、单会话退出和多设备会话隔离。
- SQL、文件、新闻和车票 Agent 的 SSE 转发。
- ECharts 和数据分析 Agent 的 JSON 转发。
- `.docx` 文件上传并转发给 Python Agent。
- Agent HTTP、超时和协议错误映射。
- SSE 完成、超时、断开和上游取消处理。

Train Agent 接口当前由 Python 服务返回 HTTP 501，功能尚未实现，不属于当前 Chat/SSE 回归验收范围。

尚未实现：验证码限流、数据库连接 CRUD、用户资料、Agent 调用审计和 OpenAPI 文档。

## 运行架构

```text
Frontend
   │ HTTP / SSE
   ▼
Java Gateway :8080
   ├── MySQL :3307（容器内 3306）
   ├── Redis :6379
   └── Mock Agent / External Python Agent :8000
```

Java 和 Python 共用 `agent.user_info`。新建数据库时，`sql/init.sql` 会创建兼容的基础表；如果接入已有 Python 数据库，脚本使用 `CREATE TABLE IF NOT EXISTS`，不会删除现有表或数据。

## 前置要求

- Docker Desktop，或本机 MySQL 8 和 Redis 7。
- 如在宿主机运行 Java：JDK 17、Maven 3.9+。
- Docker 模式默认使用仓库内的 Mock Agent；连接真实外部 Agent 时通过 `AGENT_BASE_URL` 覆盖。

## 方式一：Docker 启动

```bash
docker compose up --build -d
docker compose ps
curl http://localhost:8080/api/health
```

预期健康响应：

```json
{"code":200,"message":"success","data":{"status":"UP","service":"swpu-agent-gateway","version":"auth-v2"}}
```

Compose 会启动：

- `swpu-mysql`
- `swpu-redis`
- `swpu-mock-agent`
- `swpu-gateway`

Java 容器默认通过 `http://mock-agent:8000` 访问 Mock Agent，不依赖局域网 IP、外部 API Key 或其他成员的 `.env`。

查看日志：

```bash
docker compose logs -f gateway
docker compose logs -f mysql
```

停止服务：

```bash
docker compose down
```

只有需要完全重建测试数据库时才执行以下命令，它会删除 MySQL 和 Redis 数据卷：

```bash
docker compose down -v
```

## 方式二：基础设施用 Docker，Java 在本机运行

```bash
docker compose up -d mysql redis
```

PowerShell：

```powershell
$env:JWT_SECRET_KEY=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(48))
$env:AGENT_BASE_URL="http://localhost:8000"
mvn spring-boot:run
```

Bash：

```bash
export JWT_SECRET_KEY="$(openssl rand -base64 48)"
export AGENT_BASE_URL="http://localhost:8000"
mvn spring-boot:run
```

## 环境变量

| 变量 | 本机默认值 | 用途 |
|---|---|---|
| `JWT_SECRET_KEY` | 无，必须提供 | JWT HS256 签名密钥，至少 32 字节 |
| `JWT_ACCESS_TOKEN_EXPIRATION` | `1800000` | Access Token 有效期，毫秒 |
| `JWT_REFRESH_TOKEN_EXPIRATION` | `604800000` | Refresh Token 有效期，毫秒 |
| `DB_URL` | `jdbc:mysql://localhost:3307/agent...` | Java 数据库连接 |
| `MYSQL_PORT` | `3307` | Compose 暴露到宿主机的 MySQL 端口 |
| `DB_USERNAME` | `zl` | MySQL 用户名 |
| `DB_PASSWORD` | `123456` | MySQL 密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | 空 | Redis 密码 |
| `AGENT_BASE_URL` | `http://localhost:8000` | Mock Agent 或外部 Agent 地址 |
| `AGENT_CONNECT_TIMEOUT_MS` | `10000` | Agent 连接超时，毫秒 |
| `AGENT_READ_TIMEOUT_MS` | `120000` | Agent 上游读取超时，毫秒 |
| `AGENT_SSE_CORE_POOL_SIZE` | `4` | SSE 核心线程数 |
| `AGENT_SSE_MAX_POOL_SIZE` | `16` | SSE 最大线程数 |
| `AGENT_SSE_QUEUE_CAPACITY` | `100` | SSE 等待队列容量 |
| `AGENT_SSE_STREAM_TIMEOUT_MS` | `120000` | 流式任务截止时间，毫秒 |
| `AGENT_SSE_JSON_TIMEOUT_MS` | `60000` | JSON Agent 任务截止时间，毫秒 |

Compose 内部会自动把数据库和 Redis 地址替换为容器服务名。默认密码和 JWT 密钥只用于本地开发，部署前必须通过环境变量覆盖。

## 已实现接口

### 健康检查

| 方法 | 路径 | 认证 |
|---|---|---|
| `GET` | `/api/health` | 否 |

### 认证

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/auth/send_code` | 发送登录验证码 |
| `POST` | `/api/auth/send_register_code` | 发送注册验证码 |
| `POST` | `/api/auth/login` | 验证邮箱验证码并返回 Token |
| `POST` | `/api/auth/register` | 注册到 `user_info` |
| `POST` | `/api/auth/refresh` | 轮换 Refresh Token 并返回新 Token 对 |
| `POST` | `/api/auth/logout` | 撤销当前 Refresh Token 会话 |

### Chat

以下接口需要 `Authorization: Bearer <accessToken>`：

| 方法 | 路径 | 说明 |
|---|---|---|
| `POST` | `/api/chat/send` | 返回 SSE Agent 响应 |
| `POST` | `/api/chat/upload` | 上传并转发 `.docx` 文件 |

Chat 请求：

```json
{"question":"查询本月销售额"}
```

SSE 事件包括 `text`、`chart`、`analyze`、`done` 和 `error`。

## Mock Agent

`mock-agent/` 是独立的最小 FastAPI 服务，不依赖大模型或 API Key。

| 接口 | 用途 |
|---|---|
| `GET /health` | 健康检查 |
| `POST /api/chat` | JSON 请求契约验证 |
| `POST /api/chat/stream` | SSE 分段输出验证 |

Mock Agent 同时适配 Java 当前使用的 `/agent/*` 和 `/upload` 路由。`mode` 支持 `normal`、`slow`、`http500`、`malformed` 和 `disconnect`；从 Gateway 验收时，可将 `question` 设置为对应模式。

## 数据库初始化

`sql/init.sql` 按外键依赖顺序创建：

1. `user_info`
2. `chat_sessions`
3. `chat_messages`
4. `db_connections`
5. `tool_invocations`

已删除未被 Java 使用的重复 `users` 表和消息队列占位表。当前认证数据以 `user_info` 为唯一用户来源。

MySQL 官方镜像只会在空数据卷首次启动时执行初始化脚本。修改 `init.sql` 不会自动修改已有数据卷。

## 测试

```bash
mvn test
```

当前自动化测试覆盖应用上下文、Agent HTTP/JSON 错误、SSE 生命周期、登录、刷新、退出、Token 校验、用户上下文和并发刷新。

## 当前身份模型说明

- Access Token：`sub=user_info.id`，并包含 `email`、`role` 和 `type=access`。
- Refresh Token：`sub=user_info.id`，并包含唯一 `jti` 和 `type=refresh`。
- Redis Key：`auth:refresh:{userId}:{jti}`，Value 只保存 Token 的 SHA-256 哈希。
- Java 内部使用数值用户 ID；调用 Python Agent 时继续传递邮箱。
- Refresh Token 每次刷新后轮换，旧 Token 无法再次使用。
