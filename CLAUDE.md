# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
mvn clean compile              # compile only (no DB needed)
mvn spring-boot:run            # run on localhost:8080
```

Requires MySQL 8.x + Redis 6.x at runtime.

```bash
docker-compose up -d              # one-click MySQL + Redis
mysql -u root -proot < sql/init.sql  # init everything: agent DB + all tables + test data
```

Default credentials: MySQL `root/root`, Redis `localhost:6379` (no password). All secrets use `${ENV_VAR:default}` in `application-dev.yml`.

**Python Agent** (`agent-py/`, port 8000) is required for Chat — it runs the real LangChain/LangGraph AI agents. Without it, Chat errors but Auth/Sessions/DB/Viz still work.

## Architecture

Spring Boot 3.5.14 + Java 17 + MyBatis 3.0.5 + Redis + Maven + jjwt 0.12.6.

```
controller → service → mapper (MyBatis annotations, no XML)
     ↓
dto/request (Jakarta Bean Validation) + dto/response (ApiResponse<T>)
     ↓
common/exception (AppException → 6 HTTP-mapped subclasses)
     ↓
common/GlobalExceptionHandler (@RestControllerAdvice — catches 7 exception types)
```

**Response format** (every endpoint returns this):
```json
{"code": 200, "message": "success", "data": { ... }}
```

## Modules & Endpoints (19 total)

### Auth — `POST /api/auth/*` (public, no JWT)
- `send_code` / `send_register_code` — send 6-digit code to email (stored in Redis with 300s TTL)
- `login` / `register` — verify code → returns JWT `accessToken` + opaque `refreshToken`
- Rules: login code → email MUST exist in `user_info`; register code → email MUST NOT exist

### Chat — `GET|POST|DELETE /api/chat/*` (JWT required)
- `GET /sessions` — list user's sessions; `POST /sessions` — create session
- `GET /sessions/{id}/messages` — message history; `DELETE /sessions/{id}` — soft-delete
- `POST /send` — SSE streaming (`text/event-stream`), async Agent pipeline with events: `user_saved → thinking → text → (chart) → done`

### DB Connections — `/api/db/*` (JWT required)
- Full CRUD at `/connections` + `POST /connections/{id}/test` (real JDBC ping)
- `GET /connections/{id}/schema` — retrieves table/column metadata via JDBC metadata
- Passwords encrypted with AES-128 (hardcoded key `swpu-agent-2026!`)

### Visualization — `POST /api/viz/generate` (JWT required)
- Accepts `List<Map>` data + chart type → returns ECharts option JSON
- Auto-detects chart type: bar/line/pie/scatter based on column data types

### User — `GET|PUT /api/user/profile` (JWT required)
- Read/update current user's profile fields (age, country, salary, email)

### Health — `GET /api/health` (public)
- Returns `{"status":"UP","timestamp":"...","version":"1.0.0"}`

## JWT & Security

- Algorithm: HS256, secret from `jwt.secret-key` config (default 32-char string)
- Access token: 30min expiry, contains `sub` (userId) + `role` claim
- Refresh token: 7-day expiry, 128-char random hex (not JWT)
- `JwtAuthFilter` (servlet filter, not Spring Security) intercepts `/api/chat/*`, `/api/db/*`, `/api/viz/*`, `/api/user/*`
- Token passed as `Authorization: Bearer <token>`; valid token sets `request.userId` + `request.role` attributes
- Controllers extract userId via `(Long) request.getAttribute("userId")`

## Exception Handling

`GlobalExceptionHandler` maps these to proper HTTP status + `ApiResponse`:

| Exception | HTTP | Example message |
|-----------|------|-----------------|
| `AppException` subclasses | per subclass | "邮箱未注册", "会话不存在" |
| `MethodArgumentNotValidException` | 400 | field-level detail |
| `HttpMessageNotReadableException` | 400 | "Invalid request body" |
| `HttpRequestMethodNotSupportedException` | 405 | "Method not allowed" |
| `NoHandlerFoundException` | 404 | "Resource not found" |
| `Exception` (catch-all) | 500 | "Internal server error" |

Exception messages are Chinese (user-facing).

## Database

Java and Python share the `agent` database. Run `sql/init.sql` once to create everything.

| Table | Purpose |
|-------|---------|
| `user_info` | User info + role permissions (Python Agent depends on this) |
| `users` | JWT login accounts |
| `chat_sessions` | Chat sessions |
| `chat_messages` | Chat messages |
| `db_connections` | External DB connections |
| `tool_invocations` | Agent tool audit log |
| `customer`, `products`, `orders`, `customer_behavior`, `sales` | Business data (Python SQL Agent queries these) |

Full DDL + test data in `sql/init.sql`.

## Conventions

- MyBatis uses annotations (`@Select`, `@Insert`), not XML mappers
- Lombok `@Data` on all entities/DTOs, `@RequiredArgsConstructor` on services
- All API responses wrap in `ApiResponse<T>` — never return raw entities
- Configuration in `application.yaml` activates `dev` profile by default
- Verification codes stored in Redis: `login_code:{email}` / `register_code:{email}`, TTL=300s

## Python Agent Integration

Agent calls are delegated to the Python FastAPI service (`agent-py/`) via `AgentClient`:
- `AgentClient.chatStream()` calls `GET /chat?question=...&user_id=...` on the Python service
- The Python service runs real LangChain/LangGraph agents (qwen3-max via DashScope)
- Java relays the SSE stream directly to the frontend — `user_saved → thinking → text → done`
- Python expects `user_id` to be the user's **email** (its permission middleware queries `SELECT role FROM user_info WHERE email = ?`)
- Java's `AgentService.lookupEmail()` resolves `userId → email` before calling Python
- Auth (send_code / send_register_code) is pure Java with Redis — 0 LLM cost

## What's Not Yet Built

- Real SMTP email delivery (SendEmailTool only logs to console)
- Tests beyond the default context-load test
- Refresh token endpoint (`POST /api/auth/refresh`)
