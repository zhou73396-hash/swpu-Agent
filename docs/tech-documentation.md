# Java Wrapper Technical Documentation

## 1. Project Overview

The **swpu-agent** Java wrapper is a Spring Boot 3.5.14 application that wraps the Python Agent service (FastAPI + LangChain/LangGraph, port 8000) for frontend consumption. It provides JWT-based authentication, Redis-backed verification code management, and SSE streaming relay.

### 1.1 Architecture

```
┌──────────────────────────────────────────────────────────┐
│                  Frontend (localhost:8081)                │
└──────────────────────┬───────────────────────────────────┘
                       │ HTTP / SSE
┌──────────────────────▼───────────────────────────────────┐
│              Java Wrapper (localhost:8080)                │
│  ┌────────────────────────────────────────────────────┐  │
│  │              JwtAuthFilter                          │  │
│  │   /api/chat/* /api/db/* /api/viz/* /api/user/*     │  │
│  └──────────────────────┬─────────────────────────────┘  │
│                         │                                │
│  ┌──────────────────────▼─────────────────────────────┐  │
│  │              Controllers                            │  │
│  │  ┌─────────────────┐  ┌────────────────────────┐   │  │
│  │  │  AuthController  │  │   ChatController       │   │  │
│  │  │  /api/auth/*     │  │   /api/chat/send       │   │  │
│  │  │                  │  │   /api/chat/upload      │   │  │
│  │  └────────┬─────────┘  └───────────┬────────────┘   │  │
│  └───────────┼──────────────────────────┼──────────────┘  │
│              │                          │                │
│  ┌───────────▼──────────┐  ┌───────────▼──────────────┐ │
│  │     AuthService      │  │     ChatService          │ │
│  │  ┌────────────────┐  │  │  ┌────────────────────┐  │ │
│  │  │  RedisService   │  │  │  │   AgentClient      │  │ │
│  │  │  (code storage) │  │  │  │   (HTTP to Python) │  │ │
│  │  └────────────────┘  │  │  └────────────────────┘  │ │
│  │  ┌────────────────┐  │  │                          │ │
│  │  │    JwtUtil      │  │  │                          │ │
│  │  │  (token gen)    │  │  │                          │ │
│  │  └────────────────┘  │  │                          │ │
│  └──────────────────────┘  └──────────────────────────┘ │
│                         │                                │
│  ┌──────────────────────▼──────────────────────────────┐ │
│  │              External Services                       │ │
│  │  MySQL 8.0  │  Redis 7.x  │  Python Agent :8000     │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 1.2 Design Rationale

| Decision | Chosen | Alternative | Rationale |
|----------|--------|-------------|-----------|
| Code generation | Java-side 4-digit (login); Python-side (register) | Python-side only | Java generates login codes for Redis TTL control; Python handles register codes |
| Code storage | Redis (TTL 60s) | Database | Auto-expiry, no manual cleanup |
| Auth token | JWT HS256 | Session-based | Stateless, compatible with microservice architecture |
| Chat relay | SSE pass-through | Full proxy | Preserves Python agent streaming behavior |
| HTTP client | Hutool HttpUtil + RestTemplate | Spring RestTemplate only | Hutool for simple JSON calls, RestTemplate for multipart upload |
| Email delivery | Python SystemAgent | Java SMTP | Reuses Python's SMTP config; no Java-side SMTP needed |
| User registration | Java MyBatis Plus direct insert | Python agent | Simpler; Java has full DB access |

---

## 2. Technology Stack

| Component | Choice | Version |
|-----------|--------|---------|
| Framework | Spring Boot | 3.5.14 |
| Language | Java | 17 |
| ORM | MyBatis Plus | 3.5.15 |
| Database | MySQL | 8.0 |
| Cache | Redis | 7.x (Alpine) |
| JWT | jjwt (io.jsonwebtoken) | 0.12.6 |
| HTTP Client | Hutool | 5.8.38 |
| Email | via Python SystemAgent | — |
| Validation | Jakarta Bean Validation | — |

---

## 3. Module Breakdown

```
src/main/java/com/swpuagent/
├── SwpuAgentApplication.java    # Entry point + @MapperScan + @EnableScheduling
├── agent/
│   └── AgentClient.java         # HTTP client to Python agent service (8 methods)
├── common/
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
├── config/
│   ├── CorsConfig.java          # CORS configuration
│   └── RedisConfig.java         # RedisTemplate beans
├── controller/
│   ├── AuthController.java      # /api/auth/* (4 endpoints)
│   └── ChatController.java      # /api/chat/* (2 endpoints)
├── dto/
│   ├── request/
│   │   ├── SendCodeRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   └── ChatSendRequest.java
│   └── response/
│       └── LoginResponse.java
├── entity/
│   ├── Customer.java            # → table: customer
│   ├── CustomerBehavior.java    # → table: customer_behavior
│   ├── Orders.java              # → table: orders
│   ├── Products.java            # → table: products
│   ├── Sales.java               # → table: sales
│   ├── SalesOrders.java         # → table: sales_orders
│   └── UserInfo.java            # → table: user_info (Python-owned)
├── mapper/
│   ├── CustomerMapper.java
│   ├── CustomerBehaviorMapper.java
│   ├── OrdersMapper.java
│   ├── ProductsMapper.java
│   ├── SalesMapper.java
│   ├── SalesOrdersMapper.java
│   └── UserInfoMapper.java
├── security/
│   ├── JwtUtil.java             # JWT generation & validation
│   └── JwtAuthFilter.java       # OncePerRequestFilter
├── service/
│   ├── AuthService.java
│   ├── ChatService.java
│   ├── RedisService.java
│   ├── UserService.java
│   ├── UserInfoService.java
│   ├── CustomerService.java
│   ├── CustomerBehaviorService.java
│   ├── OrdersService.java
│   ├── ProductsService.java
│   ├── SalesService.java
│   ├── SalesOrdersService.java
│   └── impl/
│       ├── AuthServiceImpl.java
│       ├── ChatServiceImpl.java
│       ├── RedisServiceImpl.java
│       ├── UserServiceImpl.java
│       ├── UserInfoServiceImpl.java
│       ├── CustomerServiceImpl.java
│       ├── CustomerBehaviorServiceImpl.java
│       ├── OrdersServiceImpl.java
│       ├── ProductsServiceImpl.java
│       ├── SalesServiceImpl.java
│       └── SalesOrdersServiceImpl.java
├── utils/
│   ├── Result.java              # Unified response wrapper
│   └── ResultCode.java          # Status code enum
└── vo/
    ├── ChatRequestVo.java
    ├── SendLoginCodeRequest.java
    ├── UserLoginDto.java
    └── SendEmailVO.java
```

---

## 4. API Reference

### 4.1 Base Information

| Item | Description |
|------|-------------|
| Base URL | `http://localhost:8080` |
| Protocol | HTTP / SSE |
| Data Format | JSON |
| Encoding | UTF-8 |

### 4.2 Response Format

All endpoints return the unified `Result<T>` format:

```json
{
  "code": 200,
  "message": "success",
  "data": { ... }
}
```

| Field | Type | Description |
|-------|------|-------------|
| `code` | int | 200 success, 400 validation error, 500 server error |
| `message` | string | Human-readable message |
| `data` | T | Response payload (nullable) |

---

### 4.3 Auth Endpoints (Public — 4 endpoints)

#### 4.3.1 Send Login Code

| Item | Description |
|------|-------------|
| Method | `POST` |
| Path | `/api/auth/send_code` |
| Auth | None |

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response (200):**
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**Internal Flow:**
1. Java generates 4-digit numeric code via `RandomUtil.randomNumbers(4)`
2. Stores in Redis: key `login:code:{email}`, TTL 60s
3. Forwards to Python `POST /agent/system/chat` with message `"send login verification code {code} to email {email}"`
4. SystemAgent checks email exists in DB → sends email via QQ SMTP

---

#### 4.3.2 Send Register Code

| Item | Description |
|------|-------------|
| Method | `POST` |
| Path | `/api/auth/send_register_code` |
| Auth | None |

**Request:**
```json
{
  "email": "newuser@example.com"
}
```

**Response (200):**
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**Internal Flow:**
1. Java forwards to Python `POST /agent/system/chat` with message `"send register verification code to email {email}"`
2. SystemAgent checks email not registered → generates code → stores in Redis `register:code:{email}` → sends email
3. Note: for register, code generation and Redis storage are handled by Python, not Java

---

#### 4.3.3 Login

| Item | Description |
|------|-------------|
| Method | `POST` |
| Path | `/api/auth/login` |
| Auth | None |

**Request:**
```json
{
  "email": "user@example.com",
  "code": "1234"
}
```

**Response (200):**
```json
{
  "code": 200,
  "message": "success",
  "data": {
    "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
    "refreshToken": "a1b2c3d4e5f6...",
    "tokenType": "Bearer",
    "expiresIn": 1800000
  }
}
```

**Response (500 - wrong code):**
```json
{
  "code": 500,
  "message": "Invalid verification code",
  "data": null
}
```

**Internal Flow:**
1. Retrieve stored code from Redis `login:code:{email}`
2. Compare with user-provided code
3. If match → delete Redis code → issue JWT tokens (access + refresh)
4. If mismatch → return error
5. **No Python call** — login is pure Java + Redis

---

#### 4.3.4 Register

| Item | Description |
|------|-------------|
| Method | `POST` |
| Path | `/api/auth/register` |
| Auth | None |

**Request:**
```json
{
  "email": "newuser@example.com",
  "code": "654321",
  "userName": "New User"
}
```

**Response (200):**
```json
{
  "code": 200,
  "message": "success",
  "data": null
}
```

**Internal Flow:**
1. Retrieve stored code from Redis `register:code:{email}`
2. Compare with user-provided code
3. Check email not already in `user_info` via MyBatis Plus `LambdaQueryWrapper`
4. If valid → insert user directly into `user_info` via `userInfoService.save(user)` → delete Redis code
5. If mismatch or duplicate → return error
6. **No Python call for user creation** — register inserts directly into shared DB

---

### 4.4 Chat Endpoints (JWT Protected — 2 endpoints)

#### 4.4.1 Send Message

| Item | Description |
|------|-------------|
| Method | `POST` |
| Path | `/api/chat/send` |
| Auth | Bearer JWT |
| Response Type | `text/event-stream` (SSE) |

**Request:**
```json
{
  "question": "Show top 5 products by sales"
}
```

**Request Headers:**
```
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
```

**SSE Response Events (for streaming agents: SQL, File, News, Train):**

| Event | Data | Description |
|-------|------|-------------|
| `text` | `{"content":{"text":"...","done":false}}` | Streaming text chunk |
| `done` | `{"content":"","done":true}` | Stream complete |
| `error` | `{"message":"error description"}` | Error occurred |

**Response Events (for non-streaming agents: ECharts, Analyze):**

| Event | Data | Description |
|-------|------|-------------|
| `chart` | `{"data":"{...echarts JSON...}","code":200,"msg":"..."}` | Complete ECharts config |
| `analyze` | `{"table":{...},"result":"...","json":"..."}` | Complete analysis result |

**Internal Flow:**
1. `JwtAuthFilter` validates JWT from `Authorization` header
2. Extracts `userId` (email) from token `sub` claim
3. Java `ChatServiceImpl` performs keyword matching on the question
4. Routes to the appropriate Python agent endpoint (see routing table below)
5. Python SSE → Java relays as `text`/`chart`/`analyze`/`done`/`error` events via `SseEmitter`

---

#### 4.4.2 Upload File

| Item | Description |
|------|-------------|
| Method | `POST` |
| Path | `/api/chat/upload` |
| Auth | Bearer JWT |
| Content-Type | `multipart/form-data` |

**Request:**
```
file: document.docx (multipart form field, .docx only)
```

**Response (200):**
```json
{
  "code": 200,
  "msg": "File uploaded successfully",
  "data": "..."
}
```

**Internal Flow:**
1. Validate file is non-empty and has `.docx` extension
2. Forward to Python `POST /upload` via RestTemplate (multipart)
3. Return Python response to caller

---

## 5. JWT Security

### 5.1 Token Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| Algorithm | HS256 | HMAC with SHA-256 |
| Secret Key | `swpu-agent-jwt-secret-key-2026-i-always-like-xyhc` | Configurable via `jwt.secret-key` |
| Access Token TTL | 30 minutes | Configurable via `jwt.access-token-expiration` |
| Refresh Token TTL | 7 days | Configurable via `jwt.refresh-token-expiration` |
| Refresh Token Format | 128-char hex | Non-JWT opaque token via `SecureRandom` |

### 5.2 Token Claims

| Claim | Description |
|-------|-------------|
| `sub` | User email (used as userId throughout the app) |
| `role` | User role for permission checks |
| `iat` | Issued at timestamp |
| `exp` | Expiration timestamp |

### 5.3 Protected Paths

The `JwtAuthFilter` intercepts requests to these prefixes:

| Path Prefix | Controller Exists? |
|-------------|-------------------|
| `/api/chat/*` | ✅ ChatController |
| `/api/db/*` | ❌ Not yet built |
| `/api/viz/*` | ❌ Not yet built |
| `/api/user/*` | ❌ Not yet built |

### 5.4 Request Attributes

After successful JWT validation, the filter injects these request attributes:

| Attribute | Type | Source |
|-----------|------|--------|
| `userId` | String | JWT `sub` claim (email) |
| `role` | String | JWT `role` claim |

### 5.5 Data Access Layer

All entities use the MyBatis Plus standard pattern:

```
Entity → Mapper (extends BaseMapper<Entity>) → Service (extends IService<Entity>) → ServiceImpl (extends ServiceImpl<Mapper, Entity>)
```

#### Business Entities (Python-owned tables, Java reads via MyBatis Plus)

| Entity | Mapper | Service Interface | Service Impl | Table |
|--------|--------|-------------------|-------------|-------|
| `Customer` | `CustomerMapper` | `CustomerService` | `CustomerServiceImpl` | `customer` |
| `CustomerBehavior` | `CustomerBehaviorMapper` | `CustomerBehaviorService` | `CustomerBehaviorServiceImpl` | `customer_behavior` |
| `Orders` | `OrdersMapper` | `OrdersService` | `OrdersServiceImpl` | `orders` |
| `Products` | `ProductsMapper` | `ProductsService` | `ProductsServiceImpl` | `products` |
| `Sales` | `SalesMapper` | `SalesService` | `SalesServiceImpl` | `sales` |
| `SalesOrders` | `SalesOrdersMapper` | `SalesOrdersService` | `SalesOrdersServiceImpl` | `sales_orders` |
| `UserInfo` | `UserInfoMapper` | `UserInfoService` | `UserInfoServiceImpl` | `user_info` |

#### Java-Owned Tables (DDL in `sql/init.sql`)

| Table | Primary Key | Notes |
|-------|-------------|-------|
| `users` | id (auto) | JWT accounts — no MyBatis entity, not yet used by Java code |
| `chat_sessions` | id (auto) | No MyBatis entity yet — not yet used |
| `chat_messages` | id (auto) | No MyBatis entity yet — not yet used |
| `db_connections` | id (auto) | No MyBatis entity yet — not yet used |
| `queue_messages` | id (auto) | DDL only — no Java code uses this |
| `tool_invocations` | id (auto) | DDL only — no Java code uses this |

Each `BaseMapper<Entity>` provides built-in methods: `insert`, `deleteById`, `updateById`, `selectById`, `selectList`, `selectPage`.

Each `IService<Entity>` / `ServiceImpl` adds: `save`, `saveBatch`, `removeById`, `updateById`, `getById`, `list`, `page`, `count`, plus lambda query wrappers.

---

## 6. Redis Key Schema

| Purpose | Key Pattern | Value | TTL |
|---------|-------------|-------|-----|
| Login Code | `login:code:{email}` | 4-digit string (e.g. `"4831"`) | 60 seconds |
| Register Code | `register:code:{email}` | code string | 60 seconds |

- `{email}` is the user's email address
- Keys auto-expire after TTL via `SETEX`
- Login codes generated by Java; register codes generated by Python
- Codes are deleted immediately after successful verification

---

## 7. Python Agent Integration

### 7.1 Agent Client Configuration

| Setting | Default | Config Key |
|---------|---------|------------|
| Base URL | `http://192.168.158.56:8000` | `agent.base-url` |

### 7.2 Python Endpoints Called by Java

| Java Method | Python Endpoint | Method | Response |
|-------------|----------------|--------|----------|
| `AgentClient.systemChat(msg, email)` | `/agent/system/chat` | POST | JSON — send code / email |
| `AgentClient.sqlChat(q, uid, cb)` | `/agent/sql/chat` | POST | SSE streaming (default route) |
| `AgentClient.echartsGenerate(q, uid)` | `/agent/echarts/generate` | POST | JSON (ECharts config) |
| `AgentClient.analyze(q, uid)` | `/agent/analyze` | POST | JSON (table + analysis + chart) |
| `AgentClient.fileChat(q, uid, cb)` | `/agent/file/chat` | POST | SSE streaming |
| `AgentClient.newsChat(q, uid, cb)` | `/agent/news/chat` | POST | SSE streaming |
| `AgentClient.trainChat(q, uid, cb)` | `/agent/train/chat` | POST | SSE streaming |
| `AgentClient.uploadFile(file)` | `/upload` | POST | multipart/form-data |

### 7.3 Agent Request/Response Formats

**SystemAgent** (`POST /agent/system/chat`):

Request:
```json
{
  "message": "send login verification code 4831 to email user@example.com",
  "user_id": "user@example.com"
}
```

Response (success):
```json
{ "data": "4831", "code": "200", "msg": "Sent successfully" }
```

Response (failure — email not registered):
```json
{ "data": "0", "code": "500", "msg": "Email not registered" }
```

**Chat Agents** (`POST /agent/{sql,file,news,train}/chat`):

Request:
```json
{ "question": "Show top 5 products by sales", "user_id": "user@example.com" }
```

Response: SSE stream with `data:` prefixed JSON lines. Java strips the `data:` prefix, parses JSON, and relays as named SSE events.

**ECharts Agent** (`POST /agent/echarts/generate`):

Request:
```json
{ "question": "Generate bar chart of sales by category", "user_id": "user@example.com" }
```

Response:
```json
{ "data": "{...ECharts JSON...}", "code": 200, "msg": "Generated successfully" }
```

**Analyze Agent** (`POST /agent/analyze`):

Request:
```json
{ "question": "Analyze sales by category in 2024", "user_id": "user@example.com" }
```

Response:
```json
{
  "table": { "column_name": ["category", "total_sales"], "data": [{"category": "Electronics", "total_sales": "1200000"}] },
  "result": "I. Detailed Analysis\n...\nII. Conclusion: ...",
  "json": "{...ECharts JSON...}"
}
```

### 7.4 Auth Flow — Python vs Java Boundaries

| Operation | Java Responsibility | Python Responsibility |
|-----------|--------------------|-----------------------|
| Send login code | Generate 4-digit code → Redis → forward message to SystemAgent | Validate email exists → send email |
| Send register code | Forward message to SystemAgent | Generate code → Redis → validate email not registered → send email |
| Login | Verify Redis code → issue JWT | **Not called** |
| Register | Verify Redis code → check duplicate → insert into user_info via MyBatis Plus | **Not called for user creation** |

### 7.5 Chat Routing (by Java Wrapper)

The Java `ChatServiceImpl.sendMessage()` performs keyword matching and dispatches to the correct Python agent:

```
POST /api/chat/send { "question": "xxx" }
    ├── contains "图表" / "chart" / "图"           → POST /agent/echarts/generate (JSON)
    ├── contains "数据分析" / "分析数据" / "analyze"  → POST /agent/analyze (JSON)
    ├── contains "上传文件成功" / "file"              → POST /agent/file/chat (SSE)
    ├── contains "新闻" / "热点" / "news"            → POST /agent/news/chat (SSE)
    ├── contains "火车" / "高铁" / "车票" / "train"   → POST /agent/train/chat (SSE)
    └── otherwise                                   → POST /agent/sql/chat (SSE, default)
```

---

## 8. Database Schema

### 8.1 Java-Owned Tables (in `sql/init.sql`)

These tables are created by Java's init script. DDL only — most have no corresponding MyBatis Plus entities yet.

| # | Table | Has Entity? | Notes |
|---|-------|-------------|-------|
| 1 | `users` | ❌ | JWT accounts: username, password_hash, email, role, refresh_token |
| 2 | `chat_sessions` | ❌ | FK → user_info(id) ON DELETE CASCADE |
| 3 | `chat_messages` | ❌ | FK → chat_sessions(id); role ENUM(USER/ASSISTANT/SYSTEM/TOOL); message_type ENUM(TEXT/SQL/CHART/THINKING/ERROR) |
| 4 | `db_connections` | ❌ | FK → user_info(id); encrypted_password for external DBs |
| 5 | `queue_messages` | ❌ | Message queue tracking — DDL only, no Java code uses this |
| 6 | `tool_invocations` | ❌ | FK → chat_messages(id); agent tool call audit log |

### 8.2 Python-Owned Tables (Java reads via MyBatis Plus)

| Table | Entity | Notes |
|-------|--------|-------|
| `user_info` | `UserInfo.java` | id, user_name, email, role. Java reads + inserts directly (register) |
| `customer` | `Customer.java` | user_id, username, registration_date, country, age, gender, total_spent, order_count |
| `customer_behavior` | `CustomerBehavior.java` | User behavior tracking |
| `orders` | `Orders.java` | order_id, order records |
| `products` | `Products.java` | product_id, product catalog |
| `sales` | `Sales.java` | Monthly sales statistics |
| `sales_orders` | `SalesOrders.java` | id (auto), simplified sales orders |

### 8.3 MyBatis Plus Configuration

```yaml
mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true    # snake_case → camelCase
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

---

## 9. Configuration Reference

### 9.1 application.yaml (single file, no profiles)

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://192.168.158.56:3306/agent?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: zl
    password: 123456
  data:
    redis:
      host: 192.168.158.56
      port: 6379
  servlet:
    multipart:
      max-file-size: 20MB
      max-request-size: 20MB

jwt:
  secret-key: swpu-agent-jwt-secret-key-2026-i-always-like-xyhc
  access-token-expiration: 1800000     # 30 min
  refresh-token-expiration: 604800000  # 7 days

agent:
  base-url: http://192.168.158.56:8000

mybatis-plus:
  mapper-locations: classpath*:mapper/**/*.xml
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
  global-config:
    db-config:
      id-type: auto
```

---

## 10. Build & Run

### 10.1 Prerequisites

| Component | Version | Purpose |
|-----------|---------|---------|
| JDK | 17+ | Java runtime |
| Maven | 3.8+ | Build tool |
| MySQL | 8.0 | Data storage |
| Redis | 7.x | Code cache |
| Python Agent | — | Business logic backend (port 8000) |

### 10.2 Quick Start

```bash
# 1. Start MySQL + Redis (auto-creates agent DB and Java tables)
docker-compose up -d

# 2. Ensure InnoDB for user_info (required for FK support)
mysql -u root -proot agent -e "ALTER TABLE user_info ENGINE=InnoDB"

# 3. Start Python agent (separate terminal)
cd agent-py && python main.py

# 4. Start Java wrapper
mvn spring-boot:run
# → localhost:8080
```

### 10.3 Verify

```bash
# Health check (not yet implemented — use auth test instead)
curl -X POST http://localhost:8080/api/auth/send_code \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# Login (use code from Redis)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "code": "4831"}'

# Chat (replace TOKEN with accessToken from login response)
curl -N -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"question": "Show all products"}'
```

---

## 11. Error Handling

### 11.1 HTTP Status Codes

| Code | Meaning | Scenario |
|------|---------|----------|
| 200 | Success | All successful responses |
| 400 | Validation Error | Missing/invalid request parameters |
| 401 | Unauthorized | Missing or expired JWT |
| 500 | Server Error | Internal exception |

### 11.2 Business Error Codes (in response body)

| code | Scenario |
|------|----------|
| 200 | Operation successful |
| 400 | Parameter validation failed |
| 500 | Code expired / invalid / Python agent error |

### 11.3 Global Exception Handler

The `GlobalExceptionHandler` (`@RestControllerAdvice`) catches:

| Exception | HTTP Status | Behavior |
|-----------|-------------|----------|
| `MethodArgumentNotValidException` | 400 | Returns field validation errors |
| `RuntimeException` | 500 | Logs stack trace, returns message |
| `Exception` (generic) | 500 | Logs stack trace, returns "Internal server error" |

---

## 12. Security Considerations

| Concern | Mitigation |
|---------|------------|
| Code brute force | 4-digit codes, 60s TTL in Redis, auto-expiry |
| Token theft | JWT access token 30min TTL; refresh token rotation |
| Replay attacks | Codes deleted after first successful use |
| Python agent unavailable | Java catches exceptions, returns 500 with message |
| CORS | Allow all origins (dev); restrict in production |
| SQL injection | Parameterized queries via MyBatis Plus; Python agent has 3-layer SQL defense |
| Path traversal | Not applicable — no file server operations in Java (only .docx upload to Python) |

---

## 13. File Index

| Category | Files | Count |
|----------|-------|-------|
| Config | `CorsConfig`, `RedisConfig`, `application.yaml` | 3 |
| Security | `JwtUtil`, `JwtAuthFilter` | 2 |
| Agent Client | `AgentClient` | 1 |
| Controllers | `AuthController`, `ChatController` | 2 |
| Services (interfaces) | `AuthService`, `ChatService`, `RedisService`, `UserService`, `UserInfoService`, `CustomerService`, `CustomerBehaviorService`, `OrdersService`, `ProductsService`, `SalesService`, `SalesOrdersService` | 11 |
| Services (impls) | `AuthServiceImpl`, `ChatServiceImpl`, `RedisServiceImpl`, `UserServiceImpl`, `UserInfoServiceImpl`, `CustomerServiceImpl`, `CustomerBehaviorServiceImpl`, `OrdersServiceImpl`, `ProductsServiceImpl`, `SalesServiceImpl`, `SalesOrdersServiceImpl` | 11 |
| Entities | `Customer`, `CustomerBehavior`, `Orders`, `Products`, `Sales`, `SalesOrders`, `UserInfo` | 7 |
| Mappers | `CustomerMapper`, `CustomerBehaviorMapper`, `OrdersMapper`, `ProductsMapper`, `SalesMapper`, `SalesOrdersMapper`, `UserInfoMapper` | 7 |
| DTOs | `SendCodeRequest`, `LoginRequest`, `RegisterRequest`, `ChatSendRequest`, `SendEmailVO`, `UserLoginDto`, `LoginResponse` | 7 |
| VOs | `ChatRequestVo`, `SendLoginCodeRequest` | 2 |
| Utils | `Result`, `ResultCode` | 2 |
| Exception | `GlobalExceptionHandler` | 1 |
| **Total** | | **56** |

---

## 14. What's Not Yet Built

| Feature | Status |
|---------|--------|
| DB Connections CRUD (`/api/db/*`) | ❌ DDL exists; no controller/service |
| Visualization (`/api/viz/*`) | ❌ No controller (Python agent ready) |
| User Profile (`/api/user/*`) | ❌ No controller (UserInfo mapper ready) |
| Health endpoint (`/api/health`) | ❌ No controller |
| Refresh token endpoint | ❌ Token generation exists; no exchange endpoint |
| Chat session persistence | ❌ chat_sessions/chat_messages DDL exists; no Java code uses them |
| Message queue system | ❌ queue_messages DDL exists; no consumer/producer code |
| Tool invocation audit | ❌ tool_invocations DDL exists; no Java code writes to it |
| Real SMTP email (Java-side) | ❌ Email handled by Python SystemAgent |
| Integration tests | ❌ Only `@SpringBootTest` context-load test |
