# Project Progress

Last updated: 2026-07-11

## Current Stage

Stage 5 completed: Mock Agent, SSE regression, and project documentation update.

## Completed

### Authentication Closed Loop (Stage 1-3)
- Access Token with numeric `sub=userId`, `email`, `role`, `type=access`.
- Refresh Token with numeric `sub=userId`, unique `jti`, `type=refresh`.
- Redis stores only Refresh Token SHA-256 hash, Key `auth:refresh:{userId}:{jti}`, TTL 7 days.
- Redis Lua atomic compare-delete-replace for single-consumption rotation.
- Real Redis concurrency: one success, one 401.
- Login, refresh, logout endpoints with real HTTP statuses and unified error body.
- `UserContext` + `UserContextHolder` with `finally` cleanup in `JwtAuthFilter`.
- Java uses numeric userId internally; Python Agent receives email.
- 30 automated Java tests pass.

### Gateway Runtime & SSE Safeguards (Stage 4)
- Version label `auth-v2` in health check and startup log.
- `GlobalExceptionHandler` returns unified `ApiErrorResponse` for auth, validation, agent, and runtime errors.
- `AsyncRequestNotUsableException` and `AsyncRequestTimeoutException` handled silently.
- SSE timeout configurable via `AGENT_SSE_STREAM_TIMEOUT_MS` / `AGENT_SSE_JSON_TIMEOUT_MS`.
- Proactive `TaskScheduler`-based timeout: sends `READ_TIMEOUT` SSE error, completes emitter, then cancels upstream.
- Container timeout extended +5s beyond active timeout to prevent Tomcat/Scheduler race.
- Agent connect/read timeout configurable via `AGENT_CONNECT_TIMEOUT_MS` / `AGENT_READ_TIMEOUT_MS`.

### Mock Agent & SSE Regression (Stage 5)
- Standalone `mock-agent/` with FastAPI, no LLM or API Key dependency.
- Standard routes: `GET /health`, `POST /api/chat`, `POST /api/chat/stream`.
- Java contract routes: all `/agent/*` paths and `/upload`.
- Five modes: `normal`, `slow`, `http500`, `malformed`, `disconnect` (via `mode` param or question text).
- Docker Compose adds `mock-agent` service; Gateway defaults to `AGENT_BASE_URL=http://mock-agent:8000`.
- No dependency on LAN IPs, external API keys, or other members' `.env`.

#### Verified Acceptance Results

| Scenario | Method | Result |
|----------|--------|--------|
| Normal JSON | Gateway POST → Mock `/api/chat` | HTTP 200, email passed correctly |
| Normal SSE | Gateway POST `question=hello` | 3 segments: `text`, `text`, `done` |
| HTTP 500 | Gateway POST `question=http500` | SSE `event:error` with `HTTP_ERROR` |
| Malformed | Gateway POST `question=malformed` | SSE `event:error` with `PROTOCOL_ERROR` |
| Disconnect | Gateway POST `question=disconnect` | SSE `event:error` with `UNAVAILABLE` |
| Timeout | Gateway POST `question=slow` (1s timeout) | SSE `event:error` with `READ_TIMEOUT` at 1.06s |
| Client disconnect | Client closes stream mid-request | Mock Agent `Gateway SSE closed`, no unhandled Gateway error |

## Verified Authentication Results

- Access Token contains numeric `sub`, `email`, `role`, and `type=access`.
- Refresh Token contains numeric `sub`, unique `jti`, and `type=refresh`.
- Redis stores only the Refresh Token SHA-256 hash with a seven-day TTL.
- Concurrent use of one Refresh Token produces one success and one HTTP 401.
- Logout removes only the current Refresh session.
- Missing, expired, tampered, revoked, and wrong-type tokens return HTTP 401.
- Malformed requests return HTTP 400.
- Internal service failures return HTTP 500 with the unified error body.

## Known Gaps

- Role-based authorization is not implemented (no real HTTP 403 scenario).
- Train Agent returns HTTP 501 (Python implementation not available).
- DB connection CRUD, user profile, verification-code rate limiting, and OpenAPI not implemented.
- Some legacy MyBatis entities log `@TableId` warnings.

## Git And Runtime

- Branch: `feature/auth-closed-loop`
- Commit 1: `5647964 feat: complete authentication closed loop` (auth MVP, 45 files)
- Commit 2: `1759ab2 feat: add gateway runtime and SSE safeguards` (version ID, unified errors, SSE config, 11 files)
- Current runtime: `auth-v2`, all containers healthy
- Stage 5 Mock Agent / README changes not yet committed

## Next Work

- Generate revised resume DOCX/PDF (remove Python Agent from tech stack, add Mock Agent).
- Commit Stage 5 changes (Mock Agent, README, SSE fixes).
- Optional: role-based authorization, DB CRUD, verification-code rate limiting.
