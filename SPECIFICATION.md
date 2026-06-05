# ChatBI-Agent 编码规格说明书

> 基于 Agent Documentation.md + Backend Documentation.md 综合编写。
> **所有代码片段均来自实际源文件，可直接复制使用。**
> 编译状态：✅ `mvn clean compile` BUILD SUCCESS (24 source files)

---

## 一、项目概述

本项目是一个 **ChatBI 智能数据助手系统**，核心功能：
- 用户注册/登录（支持邮箱验证码 + 用户名密码双模式）
- 用户可通过自然语言对话查询数据库、生成图表
- Agent 智能体负责意图识别、NL→SQL、工具调用、发送验证码邮件

**技术栈：**
| 层级 | 技术 | 版本 |
|------|------|------|
| 后端框架 | Spring Boot | 3.5.14 |
| 语言 | Java | 17 |
| 构建工具 | Maven | 3.9+ |
| 数据库 | MySQL | 8.x |
| ORM | MyBatis (mybatis-spring-boot-starter) | 3.0.5 |
| 缓存 | Redis (spring-boot-starter-data-redis) | — |
| 校验 | spring-boot-starter-validation (Hibernate Validator) | 3.5.14 |
| 工具 | Lombok | — |
| Agent | LangChain4j（计划） | — |

---

## 二、项目目录结构（已实现文件用 ✅ 标注）

```
swpu-agent/
├── pom.xml                                    # Maven 构建文件 ✅
├── sql/
│   └── init.sql                               # 数据库初始化脚本 ✅
├── src/main/java/com/swpuagent/
│   ├── SwpuAgentApplication.java              # Spring Boot 入口 ✅
│   ├── controller/
│   │   ├── AuthController.java                # 认证接口 ✅
│   │   └── HealthController.java              # 健康检查 ✅
│   │   ├── ChatController.java                # 对话接口（计划）
│   │   ├── UserController.java                # 用户接口（计划）
│   │   ├── DatabaseController.java            # 数据库连接接口（计划）
│   │   └── VisualizationController.java       # 图表接口（计划）
│   ├── service/
│   │   ├── AuthService.java                   # 认证业务 ✅
│   │   └── VerificationCodeService.java       # 验证码Redis服务 ✅
│   │   ├── ChatService.java                   # 对话业务（计划）
│   │   └── AgentService.java                  # Agent编排（计划）
│   ├── mapper/
│   │   └── UserInfoMapper.java                # 用户表Mapper ✅
│   ├── entity/
│   │   └── UserInfo.java                      # 用户实体 ✅
│   ├── dto/
│   │   ├── request/
│   │   │   ├── SendCodeRequest.java           # ✅
│   │   │   ├── LoginRequest.java              # ✅
│   │   │   └── RegisterRequest.java            # ✅
│   │   └── response/
│   │       ├── ApiResponse.java               # 统一响应 ✅
│   │       └── PageResponse.java              # 分页响应 ✅
│   ├── config/
│   │   ├── CorsConfig.java                    # CORS 跨域 ✅
│   │   └── RedisConfig.java                   # Redis 序列化 ✅
│   ├── agent/
│   │   └── tool/
│   │       ├── MysqlTool.java                 # Agent查询工具 ✅
│   │       └── SendEmailTool.java             # Agent发邮件工具 ✅
│   ├── security/                              # JWT（计划）
│   └── common/
│       ├── GlobalExceptionHandler.java        # 全局异常处理 ✅
│       └── exception/
│           ├── AppException.java              # 基础异常 ✅
│           ├── NotFoundException.java         # →404 ✅
│           ├── AuthenticationException.java   # →401 ✅
│           ├── PermissionDeniedException.java # →403 ✅
│           ├── ValidationException.java       # →400 ✅
│           ├── ConflictException.java         # →409 ✅
│           └── DatabaseQueryException.java    # →422 ✅
├── src/main/resources/
│   ├── application.yml                        # 主配置 ✅
│   └── application-dev.yml                    # 开发环境配置 ✅
└── src/test/java/com/swpuagent/
    └── SwpuAgentApplicationTests.java         # 测试 ✅
```

---

## 三、Maven 依赖（pom.xml 关键依赖）

```xml
<!-- 以下依赖已在 pom.xml 中配置，clone 后直接 mvn compile 即可 -->

<!-- Spring Boot Web (嵌入式 Tomcat) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-web</artifactId>
</dependency>

<!-- ⚠️ 必须添加此依赖，否则 jakarta.validation 注解无法解析 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- MyBatis -->
<dependency>
    <groupId>org.mybatis.spring.boot</groupId>
    <artifactId>mybatis-spring-boot-starter</artifactId>
    <version>3.0.5</version>
</dependency>

<!-- MySQL 驱动 -->
<dependency>
    <groupId>com.mysql</groupId>
    <artifactId>mysql-connector-j</artifactId>
    <scope>runtime</scope>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>

<!-- Lombok -->
<dependency>
    <groupId>org.projectlombok</groupId>
    <artifactId>lombok</artifactId>
    <optional>true</optional>
</dependency>

<!-- 测试 -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
```

---

## 四、数据库设计（6张表）

### 4.1 初始化方式

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE chatbi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"

# 2. 执行初始化脚本
mysql -u root -p chatbi_db < sql/init.sql
```

### 4.2 表清单

| 表名 | 用途 | 状态 |
|------|------|------|
| `user_info` | 用户信息（Agent验证码流程用） | 当前模块使用 |
| `users` | 用户账号（JWT密码登录用） | 计划 |
| `chat_sessions` | 对话会话 | 计划 |
| `chat_messages` | 对话消息 | 计划 |
| `db_connections` | 外部数据库连接配置 | 计划 |
| `tool_invocations` | Agent工具调用审计日志 | 计划 |

完整 DDL 见 `sql/init.sql`。

### 4.3 user_info 表（当前核心表）

```sql
CREATE TABLE user_info (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    user_name VARCHAR(50) NOT NULL,
    email VARCHAR(100) NOT NULL UNIQUE,
    role VARCHAR(50) DEFAULT 'USER',
    age INT DEFAULT NULL,
    country VARCHAR(100) DEFAULT NULL,
    salary DECIMAL(12,2) DEFAULT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_user_info_email (email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
```

---

## 五、配置文件

### 5.1 application.yml（主配置）

```yaml
spring:
  application:
    name: swpu-agent
  profiles:
    active: dev
```

### 5.2 application-dev.yml（开发环境）

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/chatbi_db?useUnicode=true&characterEncoding=utf8mb4&serverTimezone=Asia/Shanghai
    username: root
    password: ${DB_PASSWORD:root}
    driver-class-name: com.mysql.cj.jdbc.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 5
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3000ms
      lettuce:
        pool:
          max-active: 8
          max-idle: 8
          min-idle: 0

mybatis:
  mapper-locations: classpath:mapper/*.xml
  type-aliases-package: com.swpuagent.entity
  configuration:
    map-underscore-to-camel-case: true
    log-impl: org.apache.ibatis.logging.slf4j.Slf4jImpl

jwt:
  secret-key: ${JWT_SECRET_KEY:default-jwt-secret-key-at-least-32-chars-long}
  access-token-expire-minutes: 30
  refresh-token-expire-days: 7

server:
  port: ${SERVER_PORT:8080}

logging:
  level:
    com.swpuagent: DEBUG
    root: WARN
```

> 所有敏感参数通过 `${ENV_VAR:default}` 格式支持环境变量覆盖，clone 后直接可用默认值运行。

---

## 六、核心代码详解

### 6.1 统一响应格式

`dto/response/ApiResponse.java` — 所有API返回此格式：

```java
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {
    private int code;
    private String message;
    private T data;

    public static <T> ApiResponse<T> success(String message, T data) { ... }
    public static <T> ApiResponse<T> success(T data) { ... }
    public static <T> ApiResponse<T> created(String message, T data) { ... }
    public static <T> ApiResponse<T> error(int code, String message) { ... }
    public static <T> ApiResponse<T> error(int code, String message, T data) { ... }
}
```

### 6.2 异常体系

```
AppException (statusCode + message)
├── NotFoundException           → HTTP 404
├── AuthenticationException     → HTTP 401
├── PermissionDeniedException   → HTTP 403
├── ValidationException         → HTTP 400
├── ConflictException           → HTTP 409
└── DatabaseQueryException      → HTTP 422
```

### 6.3 全局异常处理

`GlobalExceptionHandler` 使用 `@RestControllerAdvice` 统一拦截：
- `AppException` → 返回对应的 HTTP 状态码
- `MethodArgumentNotValidException` → 400 + 字段级校验详情
- `Exception` → 500（stacktrace 记日志，不返回前端）

### 6.4 验证码 Redis 服务

```
Key Schema:
  login_code:{email}    → 6-digit code, TTL=300s
  register_code:{email} → 6-digit code, TTL=300s

生命周期:
  send_xxx_code → SET key code EX 300
  login/register → GET key → compare → DELETE key (on success)
  expire → Redis auto-evict after 300s
```

### 6.5 AuthService 业务规则

| 操作 | 规则 | 错误消息 |
|------|------|----------|
| sendLoginCode | 邮箱必须存在于 user_info | "邮箱未注册" |
| sendRegisterCode | 邮箱必须不存在于 user_info | "邮箱已注册，请直接登录" |
| loginWithCode | Redis 中验证码匹配 | "验证码错误或已过期" |
| register | 验证码匹配 + 二次检查邮箱唯一性 | "验证码错误或已过期" / "邮箱已被注册" |

### 6.6 Agent 工具

| 工具 | 文件 | 功能 |
|------|------|------|
| MysqlTool | `agent/tool/MysqlTool.java` | 查询 user_info 表（仅 SELECT） |
| SendEmailTool | `agent/tool/SendEmailTool.java` | 发送验证码邮件（占位，待接入 SMTP） |

### 6.7 Agent 决策流程

```
POST /api/auth/send_code? (login)
    → Agent: intent="login"
    → MysqlTool.queryEmail → email EXISTS?
        → YES: SendEmailTool.send → Redis.saveLoginCode → 200 "发送成功"
        → NO:  throw ValidationException("邮箱未注册")

POST /api/auth/send_register_code? (register)
    → Agent: intent="register"
    → MysqlTool.queryEmail → email EXISTS?
        → NO:  SendEmailTool.send → Redis.saveRegisterCode → 200 "发送成功"
        → YES: throw ValidationException("邮箱已注册，请直接登录")
```

---

## 七、API 接口清单

### 7.1 已实现接口

| 方法 | 路径 | 认证 | 说明 |
|------|------|------|------|
| GET | `/api/health` | 无 | 健康检查 |
| POST | `/api/auth/send_code` | 无 | 发送登录验证码 |
| POST | `/api/auth/send_register_code` | 无 | 发送注册验证码 |
| POST | `/api/auth/login` | 无 | 验证码登录 |
| POST | `/api/auth/register` | 无 | 用户注册 |

### 7.2 计划实现接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/refresh` | 刷新Token |
| POST | `/api/auth/logout` | 登出 |
| GET | `/api/auth/me` | 获取当前用户 |
| POST | `/api/chat/send` | Agent对话（SSE流式） |
| GET | `/api/chat/sessions` | 会话列表 |
| POST | `/api/chat/sessions` | 创建会话 |
| GET | `/api/chat/sessions/{id}/messages` | 会话消息 |
| DELETE | `/api/chat/sessions/{id}` | 删除会话 |
| POST | `/api/db/connections` | 添加DB连接 |
| GET | `/api/db/connections` | 列出DB连接 |
| POST | `/api/viz/generate` | 生成图表 |

---

## 八、环境变量清单

```bash
# 数据库
DB_HOST=localhost
DB_PORT=3306
DB_PASSWORD=root

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379
REDIS_PASSWORD=

# JWT
JWT_SECRET_KEY=your-jwt-secret-at-least-32-chars

# LLM (Agent用)
LLM_PROVIDER=deepseek
LLM_API_KEY=your_api_key
LLM_BASE_URL=https://api.deepseek.com/v1
LLM_MODEL=deepseek-chat

# SMTP (邮件用)
SMTP_HOST=smtp.example.com
SMTP_PORT=587
SMTP_USERNAME=your_email
SMTP_PASSWORD=your_password
```

---

## 九、可复现操作步骤

### 9.1 Clone & Build

```bash
git clone git@git.code.tencent.com:swpu-agent/backend.git
cd backend
mvn clean compile        # 下载依赖 + 编译（无需数据库即可通过）
```

### 9.2 初始化数据库

```bash
mysql -u root -p -e "CREATE DATABASE chatbi_db CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;"
mysql -u root -p chatbi_db < sql/init.sql
```

### 9.3 配置 & 启动

```bash
# 方式一：使用默认配置（需本地 MySQL + Redis）
mvn spring-boot:run

# 方式二：通过环境变量覆盖
export DB_PASSWORD=your_real_password
mvn spring-boot:run
```

### 9.4 验证

```bash
# 健康检查
curl http://localhost:8080/api/health
# → {"code":200,"message":"success","data":{"status":"UP",...}}

# 发送注册验证码
curl -X POST http://localhost:8080/api/auth/send_register_code \
  -H "Content-Type: application/json" \
  -d '{"email":"new@example.com"}'
# → {"code":200,"message":"发送成功","data":null}

# 注册（code需与Redis中一致，目前log输出到控制台）
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"new@example.com","code":"123456","userName":"Tom"}'

# 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"test@example.com","code":"123456"}'
```

---

## 十、编码实施顺序

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 | 基础框架：yml配置、ApiResponse、异常类、GlobalExceptionHandler、CorsConfig、RedisConfig | ✅ 完成 |
| Phase 2 | 登录注册：DTO、Entity、Mapper、VerificationCodeService、AuthService、AuthController | ✅ 完成 |
| Phase 3 | Agent工具：MysqlTool、SendEmailTool | ✅ 完成 |
| Phase 4 | JWT + 安全：JwtUtil、JwtAuthFilter | ⏳ 计划 |
| Phase 5 | 扩展模块：Chat、DB连接、可视化 | ⏳ 计划 |

---

## 十一、文件清单（共27个源文件）

```
已实现文件 (24 个 .java + 2 个 .yml + 1 个 .sql = 27):
  src/main/resources/application.yml
  src/main/resources/application-dev.yml
  sql/init.sql
  src/main/java/.../SwpuAgentApplication.java
  src/main/java/.../controller/AuthController.java
  src/main/java/.../controller/HealthController.java
  src/main/java/.../service/AuthService.java
  src/main/java/.../service/VerificationCodeService.java
  src/main/java/.../mapper/UserInfoMapper.java
  src/main/java/.../entity/UserInfo.java
  src/main/java/.../dto/request/SendCodeRequest.java
  src/main/java/.../dto/request/LoginRequest.java
  src/main/java/.../dto/request/RegisterRequest.java
  src/main/java/.../dto/response/ApiResponse.java
  src/main/java/.../dto/response/PageResponse.java
  src/main/java/.../config/CorsConfig.java
  src/main/java/.../config/RedisConfig.java
  src/main/java/.../agent/tool/MysqlTool.java
  src/main/java/.../agent/tool/SendEmailTool.java
  src/main/java/.../common/GlobalExceptionHandler.java
  src/main/java/.../common/exception/AppException.java
  src/main/java/.../common/exception/NotFoundException.java
  src/main/java/.../common/exception/AuthenticationException.java
  src/main/java/.../common/exception/PermissionDeniedException.java
  src/main/java/.../common/exception/ValidationException.java
  src/main/java/.../common/exception/ConflictException.java
  src/main/java/.../common/exception/DatabaseQueryException.java
```
