# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Build & Run

```bash
mvn clean compile                            # compile only (56 source files)
mvn spring-boot:run                          # → localhost:8080
```

Runtime deps: MySQL 8.x + Redis 7.x. Quick setup:

```bash
docker-compose up -d                                                    # MySQL + Redis (auto-runs init.sql)
mysql -u root -proot agent -e "ALTER TABLE user_info ENGINE=InnoDB"    # required for FK support
```

Default credentials: MySQL `zl/123456` at `localhost:3307`, Redis at `localhost:6379` (no password). All config in `application.yaml` (no profile files).

**Python Agent** (`agent-py/`, port 8000, Python 3.12) is required for Chat and Auth email delivery. Without it, both Auth and Chat error.

## Architecture

Spring Boot 3.5.14 + Java 17 + MyBatis Plus 3.5.15 + Redis + Maven + jjwt 0.12.6 + Hutool 5.8.38.

```
controller → service → mapper (MyBatis Plus BaseMapper)
     ↓
dto/request (Jakarta Bean Validation) + dto/response (Result<T>)
     ↓
common/GlobalExceptionHandler (@RestControllerAdvice)
```

Response format (via `utils/Result<T>`):
```json
{"code": 200, "message": "success", "data": { ... }}
```

## Modules & Endpoints (6 implemented)

### Auth — `POST /api/auth/*` (public)
- `send_code` → Java generates 4-digit code to Redis (60s TTL), forwards to Python SystemAgent to email
- `send_register_code` → Python SystemAgent generates code + sends email
- `login` → verify code from Redis → JWT `accessToken` + opaque `refreshToken`
- `register` → verify code → insert directly into `user_info` via MyBatis Plus
- Email delivery delegated to Python SystemAgent, NOT Java mail queue

### Chat — `POST /api/chat/*` (JWT)
- `POST /send` → SSE stream via keyword routing (see routing table below)
- `POST /upload` → .docx file upload → forwards to Python `/upload`

### Not Yet Built
- DB Connections CRUD & schema (/api/db/*)
- Visualization (/api/viz/*)
- User Profile (/api/user/*)
- Health endpoint (/api/health)
- Refresh token endpoint
- Real SMTP email (Python handles email; no Java-side SMTP)
- Message queue / dead letter admin API

## JWT & Security

- HS256, secret from `jwt.secret-key` config
- Access token: 30min, `sub` = user email (used as userId throughout)
- Refresh token: 7-day, 128-char hex (not JWT)
- `JwtAuthFilter` intercepts `/api/chat/*`, `/api/db/*`, `/api/viz/*`, `/api/user/*`
- `Authorization: Bearer <token>` → sets `request.userId` (email) + `request.role`

## Database

Java and Python share `agent` database. Java-owned tables (6, in `sql/init.sql`):

| Table | Primary Key | Notes |
|-------|-------------|-------|
| `users` | id (auto) | JWT accounts: username, password_hash, email, role, refresh_token |
| `chat_sessions` | id (auto) | FK → user_info(id) ON DELETE CASCADE |
| `chat_messages` | id (auto) | FK → chat_sessions(id), role ENUM(USER/ASSISTANT/SYSTEM/TOOL), message_type ENUM(TEXT/SQL/CHART/THINKING/ERROR) |
| `db_connections` | id (auto) | FK → user_info(id), encrypted_password for external DBs |
| `queue_messages` | id (auto) | DDL only — no Java code uses this yet |
| `tool_invocations` | id (auto) | FK → chat_messages(id), audit log |
| `user_info` | id | **Python-owned** — Java reads via UserInfoMapper, Auth inserts directly |

**Critical**: `user_info` must be InnoDB (originally MyISAM → no FK support). Run `ALTER TABLE user_info ENGINE=InnoDB` before creating Java tables.

MyBatis Plus entities (7): `UserInfo`, `Customer`, `CustomerBehavior`, `Orders`, `Products`, `Sales`, `SalesOrders` — all use `BaseMapper<Entity>` with auto snake_case→camelCase mapping.

## Python Agent Integration

`AgentClient` (Hutool HTTP) → Python FastAPI at `agent.base-url` (default `http://localhost:8000`).

| Java Method | Python Endpoint | Type |
|-------------|----------------|------|
| `systemChat(msg, email)` | POST `/agent/system/chat` | JSON — auth email + login/register |
| `sqlChat(q, uid, cb)` | POST `/agent/sql/chat` | SSE streaming (default route) |
| `echartsGenerate(q, uid)` | POST `/agent/echarts/generate` | JSON — ECharts config |
| `analyze(q, uid)` | POST `/agent/analyze` | JSON — table + analysis + chart |
| `fileChat(q, uid, cb)` | POST `/agent/file/chat` | SSE streaming |
| `newsChat(q, uid, cb)` | POST `/agent/news/chat` | SSE streaming |
| `trainChat(q, uid, cb)` | POST `/agent/train/chat` | SSE streaming |
| `uploadFile(file)` | POST `/upload` | multipart/form-data |

Python SSE format: `data:{"content":{"text":"...","done":false},"done":false}` — Java strips `data:` prefix, relays as `text`/`chart`/`analyze`/`done`/`error` SSE events.

### Chat Keyword Routing (in `ChatServiceImpl.sendMessage`)

| Keywords | Agent | Response |
|----------|-------|----------|
| 图表 / chart / 图 | echarts | `chart` SSE event (JSON) |
| 数据分析 / 分析数据 / analyze | analyze | `analyze` SSE event (JSON) |
| 上传文件成功 / file | file | SSE streaming |
| 新闻 / 热点 / news | news | SSE streaming |
| 火车 / 高铁 / 车票 / train | train | SSE streaming |
| (default) | sql | SSE streaming |

### Auth Flow (actual)

1. **Send login code**: Java → `RandomUtil.randomNumbers(4)` → Redis `login:code:{email}` (60s TTL) → Python SystemAgent sends email
2. **Send register code**: Java → Python SystemAgent (no code generated on Java side for register) → Python generates/sends code → Redis `register:code:{email}`
3. **Login**: verify Redis code → issue JWT (sub=email)
4. **Register**: verify Redis code → check email not in user_info → `userInfoService.save(user)` directly

## Redis Key Schema

| Purpose | Key Pattern | Value | TTL |
|---------|-------------|-------|-----|
| Login Code | `login:code:{email}` | 4-digit string | 60 seconds |
| Register Code | `register:code:{email}` | code string | 60 seconds |

## Conventions

- MyBatis Plus annotations (`@TableName`, `@TableId`), not XML mappers
- Lombok `@Data` entities/DTOs, `@RequiredArgsConstructor` services
- All responses → `Result<T>` (from `utils/Result`)
- No Spring profile files — single `application.yaml`
- SSE via Spring `SseEmitter` (120s timeout streaming, 60s for JSON agents)
- `@EnableScheduling` on main class (placeholder, no scheduled tasks yet)

# Java 后端项目协作规范

## 我的身份

我是准备寻找 Java 后端实习的学生。

当前目标：
- 理解现有项目代码
- 完善项目功能
- 提升 Java 后端开发能力
- 准备用于实习面试

## 你的工作方式

你作为我的 Java 后端开发助手。

不要只生成代码，需要帮助我理解代码。

每次开发任务：

第一步：
分析当前代码结构。

第二步：
说明实现方案。

第三步：
列出涉及文件。

第四步：
等待我确认后再进行大规模修改。

不要：
- 直接重构整个项目
- 一次生成大量无法理解的代码
- 跳过设计分析

## 技术栈

当前项目：

- Java 17
- Spring Boot 3
- MyBatis-Plus
- MySQL
- Redis
- JWT
- SSE
- Python Agent

## 项目架构

遵循：

Controller
↓
Service
↓
Mapper
↓
Database

并保持：

Controller负责请求处理

Service负责业务逻辑

Mapper负责数据库访问

## 代码解释要求

每次生成代码时，需要解释：

1. 这个类的作用
2. 为什么这样设计
3. 每个方法负责什么
4. 调用流程是什么
5. 涉及哪些 Spring Boot 知识点

## 修改代码原则

修改前：

先告诉我：

- 修改原因
- 修改方案
- 修改文件列表
- 可能影响

## 当前项目目标

这个项目是：

Java 后端网关 + Python Agent系统。

目标：

完善项目工程能力。

重点提升：

- 用户认证
- JWT
- Redis
- MyBatis-Plus
- 异常处理
- 文件上传
- Agent调用
