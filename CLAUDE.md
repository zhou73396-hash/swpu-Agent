# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
mvn clean compile              # compile only (no DB needed)
mvn spring-boot:run            # run on localhost:8080
```

Requires MySQL 8.x + Redis 6.x at runtime. Database init: `mysql -u root -p chatbi_db < sql/init.sql`.

Default credentials: MySQL `root/root`, Redis `localhost:6379` (no password). All secrets use `${ENV_VAR:default}` in `application-dev.yml`.

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
- `POST /send` — SSE streaming (`text/event-stream`), async Agent pipeline with events: `user_saved → thinking → tool_call → sql → tool_result → text → done`

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

6 tables in `chatbi_db`: `user_info`, `users`, `chat_sessions`, `chat_messages`, `db_connections`, `tool_invocations`. Full DDL in `sql/init.sql`.

## Conventions

- MyBatis uses annotations (`@Select`, `@Insert`), not XML mappers
- Lombok `@Data` on all entities/DTOs, `@RequiredArgsConstructor` on services
- All API responses wrap in `ApiResponse<T>` — never return raw entities
- Configuration in `application.yaml` activates `dev` profile by default
- Verification codes stored in Redis: `login_code:{email}` / `register_code:{email}`, TTL=300s

## What's Not Yet Built

- Real SMTP email delivery (SendEmailTool only logs to console)
- LangChain4j integration (Agent currently returns mock/simulated responses)
- Tests beyond the default context-load test
- Refresh token endpoint (`POST /api/auth/refresh`)
