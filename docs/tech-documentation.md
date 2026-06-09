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
│  │  MySQL 8.0  │  Redis 6.0  │  Python Agent :8000     │ │
│  └─────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```

### 1.2 Design Rationale

| Decision | Chosen | Alternative | Rationale |
|----------|--------|-------------|-----------|
| Code generation | Java-side (6-digit) | Python-side | Decouples code generation from email; enables Redis TTL guarantee |
| Code storage | Redis (TTL 60s) | Database | Auto-expiry, no manual cleanup |
| Auth token | JWT HS256 | Session-based | Stateless, compatible with microservice architecture |
| Chat relay | SSE pass-through | Full proxy | Preserves Python agent streaming behavior |
| HTTP client | Hutool HttpUtil | Spring RestTemplate | Lightweight, already a project dependency |

---

## 2. Technology Stack

| Component | Choice | Version |
|-----------|--------|---------|
| Framework | Spring Boot | 3.5.14 |
| Language | Java | 17 |
| ORM | MyBatis Plus | 3.5.15 |
| Database | MySQL | 8.0 |
| Cache | Redis | 6.0+ |
| JWT | jjwt (io.jsonwebtoken) | 0.12.6 |
| HTTP Client | Hutool | 5.8.38 |
| Email | Spring Boot Mail | — |
| Validation | Jakarta Bean Validation | — |

---

## 3. Module Breakdown

```
src/main/java/com/swpuagent/
├── SwpuAgentApplication.java    # Entry point + @MapperScan
├── agent/
│   └── AgentClient.java         # HTTP client to Python agent service
├── common/
│   └── GlobalExceptionHandler.java  # @RestControllerAdvice
├── config/
│   ├── CorsConfig.java          # CORS configuration
│   └── RedisConfig.java         # RedisTemplate beans
├── controller/
│   ├── AuthController.java      # /api/auth/* endpoints
│   └── ChatController.java      # /api/chat/* endpoints
├── dto/
│   ├── request/
│   │   ├── SendCodeRequest.java
│   │   ├── LoginRequest.java
│   │   ├── RegisterRequest.java
│   │   └── ChatSendRequest.java
│   └── response/
│       └── LoginResponse.java
├── entity/
│   ├── Customer.java
│   ├── CustomerBehavior.java
│   ├── Orders.java
│   ├── Products.java
│   ├── Sales.java
│   ├── SalesOrders.java
│   └── UserInfo.java
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
    └── SendLoginCodeRequest.java
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

### 4.3 Auth Endpoints (Public)

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
1. Java generates 6-digit numeric code via `RandomUtil.randomNumbers(6)`
2. Stores in Redis: key `login:code:{email}`, value `"123456"`, TTL 60s
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
1. Java generates 6-digit numeric code
2. Stores in Redis: key `register:code:{email}`, value `"654321"`, TTL 60s
3. Forwards to Python `POST /agent/system/chat` with message `"send register verification code {code} to email {email}"`
4. SystemAgent checks email not registered → sends email via QQ SMTP

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
  "code": "123456"
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
3. If match → forward to Python `POST /agent/system/chat` with message `"login user {email} with verification code {code}"`
4. SystemAgent validates user → returns 200
5. Delete Redis code → issue JWT tokens
6. If mismatch or SystemAgent error → return error

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
3. If match → forward to Python `POST /agent/system/chat` with message `"register new user {name} with email {email} and verification code {code}"`
4. SystemAgent creates user → returns 200
5. Delete Redis code → return success

---

### 4.4 Chat Endpoints (JWT Protected)

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
| `chart` | `{"content":{"chart":"{...echarts JSON...}","done":false}}` | Chart data |
| `done` | `{"content":"","done":true}` | Stream complete |
| `error` | `{"message":"error description"}` | Error occurred |

**Response Events (for non-streaming agents: ECharts, Analyze):**

| Event | Data | Description |
|-------|------|-------------|
| `chart` | `{"data":"{echarts JSON}","code":200,"msg":"..."}` | Complete ECharts config |
| `analyze` | `{"table":{...},"result":"...","json":"..."}` | Complete analysis result |

**Internal Flow:**
1. `JwtAuthFilter` validates JWT from `Authorization` header
2. Extracts `userId` from token claims
3. Java `ChatServiceImpl` performs keyword matching on the question
4. Routes to the appropriate Python agent endpoint:
   - ECharts/Analyze → JSON response → wrapped as SSE event → frontend
   - SQL/File/News/Train → SSE streaming → relayed to frontend

---

## 5. JWT Security

### 5.1 Token Configuration

| Parameter | Value | Description |
|-----------|-------|-------------|
| Algorithm | HS256 | HMAC with SHA-256 |
| Secret Key | `swpu-agent-jwt-secret-key-2026-i-always-like-xyhc` | Configurable via `jwt.secret-key` |
| Access Token TTL | 30 minutes | Configurable via `jwt.access-token-expiration` |
| Refresh Token TTL | 7 days | Configurable via `jwt.refresh-token-expiration` |
| Refresh Token Format | 128-char hex | Non-JWT opaque token |

### 5.2 Token Claims

| Claim | Description |
|-------|-------------|
| `sub` | User email (used as userId) |
| `role` | User role for permission checks |
| `iat` | Issued at timestamp |
| `exp` | Expiration timestamp |

### 5.3 Protected Paths

The `JwtAuthFilter` intercepts requests to these prefixes:

| Path Prefix | Description |
|-------------|-------------|
| `/api/chat/*` | Chat endpoints |
| `/api/db/*` | Database connection management |
| `/api/viz/*` | Visualization generation |
| `/api/user/*` | User profile |

### 5.4 Request Attributes

After successful JWT validation, the filter injects these request attributes:

| Attribute | Type | Source |
|-----------|------|--------|
| `userId` | String | JWT `sub` claim |
| `role` | String | JWT `role` claim |

### 5.5 Data Access Layer

All 7 tables have full MyBatis Plus CRUD support using the standard pattern:

```
Entity → Mapper (extends BaseMapper<Entity>) → Service (extends IService<Entity>) → ServiceImpl (extends ServiceImpl<Mapper, Entity>)
```

| Entity | Mapper | Service Interface | Service Impl | Table |
|--------|--------|-------------------|-------------|-------|
| `Customer` | `CustomerMapper` | `CustomerService` | `CustomerServiceImpl` | `customer` |
| `CustomerBehavior` | `CustomerBehaviorMapper` | `CustomerBehaviorService` | `CustomerBehaviorServiceImpl` | `customer_behavior` |
| `Orders` | `OrdersMapper` | `OrdersService` | `OrdersServiceImpl` | `orders` |
| `Products` | `ProductsMapper` | `ProductsService` | `ProductsServiceImpl` | `products` |
| `Sales` | `SalesMapper` | `SalesService` | `SalesServiceImpl` | `sales` |
| `SalesOrders` | `SalesOrdersMapper` | `SalesOrdersService` | `SalesOrdersServiceImpl` | `sales_orders` |
| `UserInfo` | `UserInfoMapper` | `UserInfoService` | `UserInfoServiceImpl` | `user_info` |

Each `BaseMapper<Entity>` provides built-in methods: `insert`, `deleteById`, `updateById`, `selectById`, `selectList`, `selectPage`.

Each `IService<Entity>` / `ServiceImpl` adds: `save`, `saveBatch`, `removeById`, `updateById`, `getById`, `list`, `page`, `count`, plus lambda query wrappers.

---

## 6. Redis Key Schema

| Purpose | Key Pattern | Value | TTL |
|---------|-------------|-------|-----|
| Login Code | `login:code:{email}` | 6-digit string (e.g. `"483156"`) | 60 seconds |
| Register Code | `register:code:{email}` | 6-digit string | 60 seconds |

- `{email}` is the user's email address
- Keys auto-expire after TTL via `SETEX`
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
| `AgentClient.systemChat(message)` | `/agent/system/chat` | POST | JSON — send code / login / register |
| `AgentClient.sqlChat(question, userId, ...)` | `/agent/sql/chat` | POST | SSE streaming |
| `AgentClient.echartsGenerate(question, userId)` | `/agent/echarts/generate` | POST | JSON (ECharts config) |
| `AgentClient.analyze(question, userId)` | `/agent/analyze` | POST | JSON (table + analysis + chart) |
| `AgentClient.fileChat(question, userId, ...)` | `/agent/file/chat` | POST | SSE streaming |
| `AgentClient.newsChat(question, userId, ...)` | `/agent/news/chat` | POST | SSE streaming |
| `AgentClient.trainChat(question, userId, ...)` | `/agent/train/chat` | POST | SSE streaming |

### 7.3 Agent Request/Response Formats

**SystemAgent** (`POST /agent/system/chat`):

Request:
```json
{ "message": "send login verification code 483156 to email user@example.com" }
```

Response (success):
```json
{ "data": "483156", "code": "200", "msg": "Sent successfully" }
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

Response: SSE stream with `data:` prefixed JSON lines.

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

### 7.4 Auth Flow (via SystemAgent)

Java communicates with Python SystemAgent for all auth operations:

| Operation | SystemAgent Message | Description |
|-----------|-------------------|-------------|
| Send login code | `"send login verification code {code} to email {email}"` | SystemAgent checks email exists → sends email |
| Send register code | `"send register verification code {code} to email {email}"` | SystemAgent checks email not registered → sends email |
| Login | `"login user {email} with verification code {code}"` | SystemAgent validates user credentials |
| Register | `"register new user {name} with email {email} and verification code {code}"` | SystemAgent creates user record |

### 7.5 Chat Routing (by Java Wrapper)

The Java `ChatServiceImpl` performs keyword matching and dispatches to the correct Python agent:

```
POST /api/chat/send { "question": "xxx" }
    ├── contains "图表" / "chart" / "图"           → POST /agent/echarts/generate (JSON)
    ├── contains "数据分析" / "analyze"              → POST /agent/analyze (JSON)
    ├── contains "上传文件成功" / "file"              → POST /agent/file/chat (SSE)
    ├── contains "新闻" / "热点" / "news"            → POST /agent/news/chat (SSE)
    ├── contains "火车" / "高铁" / "车票" / "train"   → POST /agent/train/chat (SSE)
    └── otherwise                                   → POST /agent/sql/chat (SSE, default)
```

---

## 8. Database Schema

### 8.1 Java-Owned Tables

The Java wrapper manages these tables via MyBatis Plus:

| Table | Entity | Primary Key | Description |
|-------|--------|-------------|-------------|
| `customer` | `Customer.java` | `user_id` | Customer profile data |
| `customer_behavior` | `CustomerBehavior.java` | — | User behavior tracking |
| `orders` | `Orders.java` | `order_id` | Order records |
| `products` | `Products.java` | `product_id` | Product catalog |
| `sales` | `Sales.java` | — | Monthly sales statistics |
| `sales_orders` | `SalesOrders.java` | `id` (AUTO_INCREMENT) | Simplified sales orders |
| `user_info` | `UserInfo.java` | `id` | Python agent user table |

### 8.2 MyBatis Plus Configuration

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

### 9.1 application.yaml

```yaml
server:
  port: 8080

spring:
  datasource:
    driver-class-name: com.mysql.cj.jdbc.Driver
    url: jdbc:mysql://192.168.158.56:3306/agent?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Shanghai
    username: root
    password: root
  data:
    redis:
      host: 192.168.158.56
      port: 6379

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
| Redis | 6.0+ | Code cache |
| Python Agent | — | Business logic backend (port 8000) |

### 10.2 Quick Start

```bash
# 1. Start MySQL + Redis
docker-compose up -d

# 2. Initialize database
mysql -u root -proot agent -e "ALTER TABLE user_info ENGINE=InnoDB"
mysql -u root -proot < sql/init.sql

# 3. Start Python agent (separate terminal)
cd agent-py && python main.py

# 4. Start Java wrapper
mvn spring-boot:run
# → localhost:8080
```

### 10.3 Verify

```bash
# Health check
curl http://localhost:8080/api/health

# Send login code
curl -X POST http://localhost:8080/api/auth/send_code \
  -H "Content-Type: application/json" \
  -d '{"email": "2972526358@qq.com"}'

# Login (use code from email)
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "2972526358@qq.com", "code": "123456"}'

# Chat (replace TOKEN with accessToken from login response)
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer TOKEN" \
  -d '{"question": "View all products"}'
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
| Code brute force | 6-digit codes, 60s TTL in Redis, auto-expiry |
| Token theft | JWT access token 30min TTL; refresh token rotation |
| Replay attacks | Codes deleted after first successful use |
| Python agent unavailable | Java catches exceptions, returns 500 with message |
| CORS | Allow all origins (dev); restrict in production |
| SQL injection | Parameterized queries via MyBatis Plus; Python agent has 3-layer SQL defense |
| Path traversal | Not applicable — no file operations in Java layer |

---

## 13. File Index

| Category | Files | Count |
|----------|-------|-------|
| Config | `RedisConfig`, `CorsConfig`, `application.yaml` | 3 |
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
