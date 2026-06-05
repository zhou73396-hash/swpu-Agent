# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Build & Run

```bash
mvn clean compile              # compile only (no DB needed)
mvn spring-boot:run            # run on localhost:8080
```

Requires MySQL 8.x + Redis 6.x at runtime. Database init: `mysql -u root -p chatbi_db < sql/init.sql`.

## Architecture

Spring Boot 3.5.14 + Java 17 + MyBatis 3.0.5 + Redis + Maven.

```
controller → service → mapper (MyBatis annotations, no XML)
     ↓
dto/request (Jakarta Bean Validation) + dto/response (ApiResponse<T>)
     ↓
common/exception (AppException → 6 HTTP-mapped subclasses)
     ↓
common/GlobalExceptionHandler (@RestControllerAdvice — catches all)
```

**Response format** (every endpoint returns this):
```json
{"code": 200, "message": "success", "data": { ... }}
```

**Exception flow**: Service throws e.g. `new ValidationException("邮箱未注册")` → GlobalExceptionHandler maps to HTTP 400 → returns `{"code":400,"message":"邮箱未注册"}`.

## What's Implemented

| Module | Files | Status |
|--------|-------|--------|
| Auth | AuthController, AuthService, VerificationCodeService | 4 endpoints working |
| Agent tools | MysqlTool (SELECT-only), SendEmailTool (placeholder) | Ready for LangChain4j integration |
| Config | CorsConfig, RedisConfig | Done |

**Auth endpoints** (all public, no JWT yet):
- `POST /api/auth/send_code` — login verification code
- `POST /api/auth/send_register_code` — registration verification code
- `POST /api/auth/login` — verify code → returns placeholder token
- `POST /api/auth/register` — create user in `user_info` table
- `GET /api/health` — health check

**Verification code flow**: codes stored in Redis (`login_code:{email}` / `register_code:{email}`, TTL=300s), deleted on successful use.

**Agent decision logic**: login code → email MUST exist in user_info; register code → email MUST NOT exist. Implemented in AuthService, tools in `agent/tool/`.

## Planned / Not Yet Built

- JWT (security/JwtUtil, JwtAuthFilter) — tokens currently placeholder strings
- Chat module (ChatController, ChatService, SSE streaming)
- DB connection module (external DB management, schema retrieval)
- Visualization (ECharts config generation)
- Real SMTP email delivery (SendEmailTool only logs to console)
- Tests (only the default context-load test exists)

## Conventions

- MyBatis uses annotations (`@Select`, `@Insert`), not XML mappers
- Lombok `@Data` on all entities/DTOs, `@RequiredArgsConstructor` on services
- All API responses wrap in `ApiResponse<T>` — never return raw entities
- Exception messages are Chinese (user-facing): "邮箱未注册", "验证码错误或已过期"
- `application-dev.yml` uses `${ENV_VAR:default}` for all secrets — works out of the box
- Configuration in `src/main/resources/application.yaml` activates `dev` profile by default
