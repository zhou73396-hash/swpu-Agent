# ChatBI-Agent 项目说明书

> 基于实际代码库生成 | 19 个 API 端点 | 47 个 Java 源文件 | 6 张数据表
> 测试日期: 2026-06-06 | 全部 19 端点 + 12 边界探测通过 ✅

---

## 一、项目概述

**ChatBI 智能数据助手系统** — 基于 Spring Boot 的后端服务，支持：
- 邮箱验证码注册/登录，JWT 令牌认证
- 自然语言对话查询数据库（Agent 管道 + SSE 流式响应）
- 外部数据库连接管理（CRUD + 连通性测试 + Schema 检索）
- ECharts 图表配置自动生成（柱状图/折线图/饼图/散点图）
- 用户个人资料管理

### 技术栈

| 层级 | 技术 | 版本 |
|------|------|------|
| 框架 | Spring Boot | 3.5.14 |
| 语言 | Java | 17 |
| 构建 | Maven | 3.9+ |
| 数据库 | MySQL | 8.x |
| ORM | MyBatis (注解模式) | 3.0.5 |
| 缓存 | Redis (Lettuce) | — |
| JWT | jjwt | 0.12.6 |
| 校验 | Hibernate Validator | — |
| 工具 | Lombok | — |

---

## 二、快速开始

```bash
# 1. 初始化数据库
mysql -u root -p -e "CREATE DATABASE chatbi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p chatbi_db < sql/init.sql

# 2. 编译（无需数据库）
mvn clean compile

# 3. 启动（需要 MySQL + Redis）
mvn spring-boot:run

# 4. 验证
curl http://localhost:8080/api/health
# → {"code":200,"message":"success","data":{"status":"UP","version":"1.0.0"}}
```

默认凭证：MySQL `root:root`，Redis `localhost:6379`（无密码）。所有密钥通过 `${ENV_VAR:default}` 支持环境变量覆盖。

---

## 三、项目结构

```
swpu-agent/
├── pom.xml
├── sql/init.sql                          # 6 张表完整 DDL
├── src/main/resources/
│   ├── application.yml                   # spring.profiles.active: dev
│   └── application-dev.yml               # 数据源/Redis/JWT/日志配置
├── src/main/java/com/swpuagent/
│   ├── SwpuAgentApplication.java         # 入口
│   ├── controller/                       # 6 个 Controller
│   │   ├── HealthController.java         # GET /api/health
│   │   ├── AuthController.java           # 登录/注册/验证码 (4 端点)
│   │   ├── ChatController.java           # 会话/消息/SSE 流 (5 端点)
│   │   ├── DatabaseController.java       # DB 连接 CRUD/测试/Schema (6 端点)
│   │   ├── VisualizationController.java  # 图表生成 (1 端点)
│   │   └── UserController.java           # 个人资料 (2 端点)
│   ├── service/                          # 7 个 Service
│   │   ├── AuthService.java
│   │   ├── VerificationCodeService.java
│   │   ├── ChatService.java
│   │   ├── AgentService.java
│   │   ├── DatabaseConnectionService.java
│   │   ├── VisualizationService.java
│   │   └── UserService.java
│   ├── mapper/                           # 4 个 Mapper (纯注解)
│   │   ├── UserInfoMapper.java
│   │   ├── ChatSessionMapper.java
│   │   ├── ChatMessageMapper.java
│   │   └── DbConnectionMapper.java
│   ├── entity/                           # 4 个 Entity
│   │   ├── UserInfo.java
│   │   ├── ChatSession.java
│   │   ├── ChatMessage.java
│   │   └── DbConnection.java
│   ├── dto/
│   │   ├── request/                      # 8 个请求 DTO
│   │   └── response/                     # ApiResponse + LoginResponse + PageResponse
│   ├── security/
│   │   ├── JwtUtil.java                  # HS256 JWT 生成/校验
│   │   └── JwtAuthFilter.java           # Servlet Filter 鉴权
│   ├── config/
│   │   ├── CorsConfig.java
│   │   ├── RedisConfig.java
│   │   └── FilterConfig.java            # Filter 注册(精确路径)
│   ├── agent/tool/
│   │   ├── MysqlTool.java               # SELECT-only 查询工具
│   │   └── SendEmailTool.java           # 邮件发送(占位)
│   └── common/
│       ├── GlobalExceptionHandler.java   # 7 种异常 → HTTP 状态码
│       └── exception/                    # 6 个 AppException 子类
```

---

## 四、数据库设计

6 张表，完整 DDL 见 `sql/init.sql`：

| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `user_info` | 用户信息（验证码流程） | id, user_name, email(UNIQUE), role, age, country, salary |
| `users` | 用户账号（JWT 密码登录用，预留） | id, username, password_hash, email, refresh_token |
| `chat_sessions` | 会话管理 | id, user_id(FK), title, status(ACTIVE/ARCHIVED/DELETED) |
| `chat_messages` | 消息记录 | id, session_id(FK), role(USER/ASSISTANT/SYSTEM/TOOL), content, message_type |
| `db_connections` | 外部数据库连接 | id, user_id(FK), db_type, host, port, encrypted_password(AES-128) |
| `tool_invocations` | Agent 工具调用审计 | id, message_id(FK), tool_name, input_params, output_result, status |

---

## 五、API 接口清单（19 个端点）

### 5.1 公开端点（无需认证）

| # | 方法 | 路径 | 说明 | HTTP |
|---|------|------|------|------|
| 1 | GET | `/api/health` | 健康检查，返回 status/version/timestamp | 200 |
| 2 | POST | `/api/auth/send_code` | 发送登录验证码（邮箱需已注册） | 200 |
| 3 | POST | `/api/auth/send_register_code` | 发送注册验证码（邮箱需未注册） | 200 |
| 4 | POST | `/api/auth/login` | 验证码登录 → JWT accessToken + refreshToken | 200 |
| 5 | POST | `/api/auth/register` | 用户注册 → JWT accessToken + refreshToken | 200 |

**请求示例：**
```bash
# 发送登录验证码
curl -X POST http://localhost:8080/api/auth/send_code \
  -H "Content-Type: application/json" \
  -d '{"email":"jwtuser@test.com"}'

# 登录（验证码从 Redis 获取：redis-cli get "login_code:jwtuser@test.com"）
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"jwtuser@test.com","code":"123456"}'
# → {"code":200,"message":"登陆成功","data":{"accessToken":"eyJ...","userId":1,...}}
```

### 5.2 Chat 模块（JWT 认证）

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 6 | GET | `/api/chat/sessions` | 获取用户会话列表 |
| 7 | POST | `/api/chat/sessions` | 创建新会话 |
| 8 | GET | `/api/chat/sessions/{id}/messages` | 获取会话消息历史 |
| 9 | DELETE | `/api/chat/sessions/{id}` | 软删除会话 |
| 10 | POST | `/api/chat/send` | **SSE 流式** 发送消息给 Agent |

**SSE 事件流：** `user_saved → thinking → tool_call → sql → tool_result → text → done`

### 5.3 DB 连接模块（JWT 认证）

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 11 | GET | `/api/db/connections` | 列出用户的数据源 |
| 12 | POST | `/api/db/connections` | 添加数据源（密码 AES 加密） |
| 13 | PUT | `/api/db/connections/{id}` | 更新数据源 |
| 14 | DELETE | `/api/db/connections/{id}` | 删除数据源（软删除） |
| 15 | POST | `/api/db/connections/{id}/test` | 连通性测试（真实 JDBC 连接） |
| 16 | GET | `/api/db/connections/{id}/schema` | 检索表结构和字段元数据 |

### 5.4 可视化 + 用户（JWT 认证）

| # | 方法 | 路径 | 说明 |
|---|------|------|------|
| 17 | POST | `/api/viz/generate` | 生成 ECharts 图表配置（bar/line/pie/scatter/auto） |
| 18 | GET | `/api/user/profile` | 获取当前用户资料 |
| 19 | PUT | `/api/user/profile` | 更新用户资料（age/country/salary/email） |

---

## 六、JWT 认证机制

- **算法**: HS256，密钥配置 `jwt.secret-key`（默认 32 字符）
- **Access Token**: 30 分钟过期，payload 包含 `sub`(userId) + `role`
- **Refresh Token**: 7 天过期，128 字符随机十六进制（非 JWT）
- **Filter 机制**: `JwtAuthFilter` (Servlet Filter) 仅拦截 `/api/chat/*`、`/api/db/*`、`/api/viz/*`、`/api/user/*`
- **使用方式**: 请求头 `Authorization: Bearer <token>`，校验通过后设置 `request.userId` + `request.role` 属性
- 公开路径 (`/api/auth/*`, `/api/health`) 直接放行

---

## 七、异常处理体系

```
AppException (statusCode + message)
├── ValidationException         → HTTP 400  "邮箱未注册"
├── AuthenticationException     → HTTP 401
├── PermissionDeniedException   → HTTP 403
├── NotFoundException           → HTTP 404  "会话不存在"
├── ConflictException           → HTTP 409
└── DatabaseQueryException      → HTTP 422

GlobalExceptionHandler (@RestControllerAdvice):
├── AppException 子类           → 对应 HTTP 状态码
├── MethodArgumentNotValidException → 400 + 字段级详情
├── HttpMessageNotReadableException → 400  "Invalid request body"
├── HttpRequestMethodNotSupportedException → 405  "Method not allowed"
├── NoHandlerFoundException         → 404  "Resource not found"
└── Exception (兜底)                → 500  "Internal server error"
```

所有用户可见错误消息使用中文。

---

## 八、统一响应格式

```json
// 成功
{"code": 200, "message": "success", "data": { ... }}

// 业务异常
{"code": 400, "message": "邮箱未注册"}

// 校验失败
{"code": 400, "message": "Validation error", "data": "email: 邮箱不能为空"}

// 认证失败
{"code": 401, "message": "Missing or invalid Authorization header"}

// 服务端错误
{"code": 500, "message": "Internal server error"}
```

---

## 九、验证码流程

```
Redis Key 规范:
  login_code:{email}     → 6位数字, TTL=300s
  register_code:{email}  → 6位数字, TTL=300s

生命周期:
  发送验证码 → SET key code EX 300
  登录/注册  → GET key → 比对 → DELETE key (成功后)
  超时      → Redis 自动淘汰
```

**业务规则:**

| 操作 | 前置条件 | 错误消息 |
|------|----------|----------|
| sendLoginCode | email 必须存在于 user_info | "邮箱未注册" |
| sendRegisterCode | email 必须不存在于 user_info | "邮箱已注册，请直接登录" |
| loginWithCode | Redis 验证码匹配 | "验证码错误或已过期" |
| register | 验证码匹配 + 二次检查唯一性 | "验证码错误或已过期" / "邮箱已被注册" |

---

## 十、DB 连接密码加密

- 算法：AES-128，密钥 `swpu-agent-2026!`（16 字节）
- `DatabaseConnectionService` 写入前加密，读取时解密
- 响应中 `encryptedPassword` 字段不返回原密码（敏感信息遮蔽）

---

## 十一、可视化自动检测规则

`POST /api/viz/generate` 在 `chartType=auto` 时的自动判断：

| 数据特征 | 检测结果 |
|----------|----------|
| 1 日期列 + 1 数值列 | line (折线图) |
| 1 文本列 + 1 数值列 (≤10 条) | pie (饼图) |
| 1 文本列 + 1 数值列 (>10 条) | bar (柱状图) |
| 1 文本列 + 多数值列 | bar (分组柱状图) |
| 2 数值列 | scatter (散点图) |
| 其他 | table (表格) |

---

## 十二、编码约定

- MyBatis 纯注解模式（`@Select`, `@Insert`），不使用 XML mapper
- Lombok `@Data` 用于所有 Entity/DTO，`@RequiredArgsConstructor` 用于 Service
- 所有接口返回 `ApiResponse<T>` 包装，**绝不**返回裸实体
- `application-dev.yml` 激活 dev profile，所有密钥通过 `${ENV_VAR:default}` 注入
- 异常消息中文化（用户面向）

---

## 十三、实施阶段

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 | 基础框架：配置、ApiResponse、异常体系、CORS、Redis | ✅ |
| Phase 2 | 认证模块：DTO、Entity、Mapper、验证码、AuthService | ✅ |
| Phase 3 | Agent 工具：MysqlTool、SendEmailTool | ✅ |
| Phase 4 | JWT 安全：JwtUtil(HS256)、JwtAuthFilter、FilterConfig | ✅ |
| Phase 5 | 扩展模块：Chat(SSE)、DB 连接、可视化、用户资料 | ✅ |
| Fix | GlobalExceptionHandler 补全 3 种异常 + FilterConfig 精确路径 | ✅ |

---

## 十四、测试报告 (2026-06-06)

### 端点测试：19/19 通过 ✅

| 模块 | 通过 | 失败 |
|------|------|------|
| Auth (公开) | 5 | 0 |
| Chat (JWT) | 5 | 0 |
| DB Connections (JWT) | 6 | 0 |
| Visualization (JWT) | 1 | 0 |
| User Profile (JWT) | 2 | 0 |
| **合计** | **19** | **0** |

### 边界探测：12/12 通过 ✅

| 场景 | 预期 | 实际 |
|------|------|------|
| 无 Authorization header | 401 | ✅ 401 |
| 无效 token | 401 | ✅ 401 |
| 缺少必填字段 | 400 + 字段详情 | ✅ |
| 非法邮箱格式 | 400 | ✅ |
| 错误 HTTP 方法 | 405 | ✅ |
| 不存在的路由 | 404 | ✅ |
| 非法 JSON body | 400 | ✅ |
| 未注册邮箱 | 400 "邮箱未注册" | ✅ |
| 重复注册 | 400 "邮箱已注册" | ✅ |
| 错误验证码 | 400 "验证码错误或已过期" | ✅ |
| 缺少验证码字段 | 400 + 字段详情 | ✅ |
| 受保护路由错误方法 | 405 | ✅ |

---

## 十五、待完成

- [ ] SMTP 真实邮件发送（SendEmailTool 仅 console 输出验证码）
- [ ] LangChain4j Agent 接入（当前返回模拟数据）
- [ ] 单元测试和集成测试（仅有一个默认 context-load 测试）
- [ ] Refresh Token 端点（`POST /api/auth/refresh`）
- [ ] 登出端点（`POST /api/auth/logout`）
- [ ] Git push（SSH 密钥密码问题阻止 push）

---

## 十六、环境变量

```bash
# 数据库
DB_PASSWORD=root              # 默认: root

# Redis
REDIS_HOST=localhost           # 默认: localhost
REDIS_PORT=6379                # 默认: 6379
REDIS_PASSWORD=                # 默认: (空)

# JWT
JWT_SECRET_KEY=...             # 默认: default-jwt-secret-key-at-least-32-chars-long

# 服务端口
SERVER_PORT=8080               # 默认: 8080
```
