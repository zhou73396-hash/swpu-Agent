# Project Progress

Last updated: 2026-07-11

## Current Stage

Stage 4 is in progress: Chat and SSE regression after the authentication changes.

## Completed

- AgentClient supports JSON, SSE, file upload, HTTP status validation, error classification, and upstream cancellation.
- Chat SSE uses a bounded executor and handles completion, rejection, disconnect, upstream errors, and cancellation.
- Access Token and rotating Refresh Token authentication is complete.
- Refresh sessions use SHA-256 hashes in Redis and atomic Lua rotation.
- Login, refresh, logout, token rejection, and real Redis concurrency were verified end to end.
- Gateway image was rebuilt and deployed as `auth-v2`.
- Gateway, MySQL, Redis, and Python Agent health checks pass.
- Authentication errors use real HTTP statuses and a unified error body.
- 30 automated Java tests pass.
- SSE stream and JSON timeout values are configurable.
- SSE timeout now emits `READ_TIMEOUT` and cancels the upstream connection.
- README states that Train Agent HTTP 501 is outside the current acceptance scope.

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

- Role-based authorization is not implemented, so a real HTTP 403 scenario is not available.
- Train Agent returns HTTP 501 because its Python implementation is not available.
- DB connection CRUD, user profile, verification-code rate limiting, and OpenAPI are not implemented.
- Some legacy MyBatis entities log warnings because they do not define `@TableId`.

## Git And Runtime

- Branch: `feature/auth-closed-loop`
- Recovery commit: `5647964 feat: complete authentication closed loop`
- Runtime version: `auth-v2`
- Gateway, MySQL, and Redis containers are healthy.
- Python Agent is currently stopped.
- The latest Stage 4 Gateway image is built but has not replaced the running container yet.
- Stage 2-4 follow-up changes are tracked on this branch.

## Next Work

- Start Python Agent and deploy the latest Gateway image.
- Finish normal, disconnect, timeout, and Python-error SSE regression.
- Record the final Stage 4 evidence here.
