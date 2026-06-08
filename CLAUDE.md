# CLAUDE.md

Guidance for Claude Code when working in this repository.

## Build & Run

```bash
mvn clean compile                            # compile only
mvn spring-boot:run                          # → localhost:8080
```

Runtime deps: MySQL 8.x + Redis 6.x. Quick setup:

```bash
docker-compose up -d                                                    # MySQL + Redis
mysql -u root -proot agent -e "ALTER TABLE user_info ENGINE=InnoDB"    # required for FK support
mysql -u root -proot < sql/init.sql                                    # create Java tables
```

Default credentials: MySQL `root/root`, Redis `localhost:6379` (no password). All secrets via `${ENV_VAR:default}` in `application-dev.yml`.

**Python Agent** (`agent-py/`, port 8000, Python 3.12) is required for Chat. Without it, Chat errors but Auth/Sessions/Viz still work.

## Architecture

Spring Boot 3.5.14 + Java 17 + MyBatis 3.0.5 + Redis + Maven + jjwt 0.12.6.

```
controller → service → mapper (MyBatis annotations)
     ↓
dto/request (Jakarta Bean Validation) + dto/response (ApiResponse<T>)
     ↓
common/exception (AppException → 6 subclasses)
     ↓
common/GlobalExceptionHandler (@RestControllerAdvice)
```

Response format:
```json
{"code": 200, "message": "success", "data": { ... }}
```

## Modules & Endpoints (19 total)

### Auth — `POST /api/auth/*` (public)
- `send_code` / `send_register_code` → 6-digit code to Redis (300s TTL), pure Java, 0 LLM
- `login` / `register` → verify code → JWT `accessToken` + opaque `refreshToken`
- login code: email MUST exist in `user_info`; register code: email MUST NOT exist

### Chat — `GET|POST|DELETE /api/chat/*` (JWT)
- `GET /sessions` — list; `POST /sessions` — create
- `GET /sessions/{id}/messages` — history; `DELETE /sessions/{id}` — soft-delete
- `POST /send` — SSE stream, events: `user_saved → thinking → text → (chart) → done`

### DB Connections — `/api/db/*` (JWT)
- Full CRUD at `/connections` + `POST /connections/{id}/test` (JDBC ping)
- `GET /connections/{id}/schema` — JDBC metadata
- Passwords: AES-128 (hardcoded key)

### Visualization — `POST /api/viz/generate` (JWT)
- `List<Map>` data + chart type → ECharts option JSON

### User — `GET|PUT /api/user/profile` (JWT)
- Read/update: id, userName, email, role

### Health — `GET /api/health` (public)

## JWT & Security

- HS256, secret from `jwt.secret-key` config
- Access token: 30min, contains `sub` (userId) + `role`
- Refresh token: 7-day, 128-char hex (not JWT)
- `JwtAuthFilter` intercepts `/api/chat/*`, `/api/db/*`, `/api/viz/*`, `/api/user/*`
- `Authorization: Bearer <token>` → sets `request.userId` + `request.role`

## Database

Java and Python share `agent` database.

| Table | Owner | Notes |
|-------|-------|-------|
| `user_info` | Python | Java adapts to actual schema: id, user_name, email, role, password |
| `users` | Java | JWT accounts |
| `chat_sessions` | Java | FK → user_info(id) |
| `chat_messages` | Java | FK → chat_sessions(id) |
| `db_connections` | Java | FK → user_info(id) |
| `tool_invocations` | Java | FK → chat_messages(id) |

**Critical**: `user_info` must be InnoDB (originally MyISAM → no FK support). Run `ALTER TABLE user_info ENGINE=InnoDB` before creating Java tables.

Java DDL in `sql/init.sql` — creates 5 Java-owned tables only, does NOT touch `user_info`.

## Python Agent Integration

- `AgentClient.chatStream(question, userId, onEvent)` → `GET /chat?question=...&user_id=...`
- Python `/chat` dispatches to LangChain agent by keyword: 图表 → echarts, 数据分析 → analyze, 热点/新闻 → news, default → sql
- Python SSE format: `data:{"content":{"text":"...","done":false},"done":false}` → Java relays as `text` / `chart` / `done` events
- Python permission middleware: `SELECT role FROM user_info WHERE email = ?` → expects `email` as user_id
- Java's `AgentService.lookupEmail()` resolves `userId → email` via `UserInfoMapper.findById()`
- Auth is pure Java + Redis, never calls Python → 0 LLM cost for login/register

## Conventions

- MyBatis annotations (`@Select`, `@Insert`), not XML
- Lombok `@Data` entities/DTOs, `@RequiredArgsConstructor` services
- All responses → `ApiResponse<T>`
- `application.yaml` activates `dev` profile
- Redis keys: `login_code:{email}` / `register_code:{email}`, TTL=300s

## What's Not Yet Built

- Real SMTP email delivery (logs to console only)
- Integration tests beyond context-load
- Refresh token endpoint
