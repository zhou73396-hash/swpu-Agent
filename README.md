# swpu-agent

> Spring Boot 3.5 + Java 17 智能 BI 对话后端，对接 Python LangChain AI Agent 实现自然语言数据查询与可视化。

## 架构

```
浏览器/前端
    │  SSE (Server-Sent Events)
    ▼
Java Backend (:8080) ─── MySQL (chatbi_db)
    │  RestClient
    ▼
Python Agent (:8000) ─── MySQL (agent) + LLM (qwen3-max)
```

- **Java 后端**：JWT 认证、会话管理、数据库连接管理、可视化生成、SSE 中继
- **Python Agent**：LangChain/LangGraph AI 智能体，负责 SQL 生成、图表生成、数据分析（独立项目，需另外部署）

## 前置条件

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 运行环境 |
| Maven | 3.8+ | 构建工具 |
| MySQL | 8.0+ | 业务数据存储 |
| Redis | 6.0+ | 验证码 / Token 缓存 |
| Python Agent | — | AI 对话引擎（独立服务，端口 8000） |

## 快速开始

### 1. 启动基础设施

```bash
# 在项目根目录，一键启动 MySQL + Redis
docker-compose up -d
```

### 2. 初始化数据库

```bash
# 创建 chatbi_db 库并导入表结构
mysql -u root -proot < sql/init.sql
```

### 3. 配置环境变量

```bash
# 必须配置（根据实际情况修改）
export DB_PASSWORD=your_mysql_password
export REDIS_PASSWORD=your_redis_password    # 如果 Redis 有密码
export JWT_SECRET_KEY=your-32-char-secret-key
export AGENT_PYTHON_URL=http://localhost:8000  # Python Agent 地址
```

### 4. 启动 Java 后端

```bash
cd swpu-agent
mvn spring-boot:run
```

### 5. 验证

```bash
curl http://localhost:8080/api/health
# {"code":200,"message":"success","data":{"status":"UP","version":"1.0.0"}}
```

## 配置说明

所有配置在 `src/main/resources/application-dev.yml`，敏感信息通过环境变量注入：

| 环境变量 | 默认值 | 说明 |
|----------|--------|------|
| `DB_PASSWORD` | `root` | MySQL 密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PORT` | `6379` | Redis 端口 |
| `REDIS_PASSWORD` | (空) | Redis 密码 |
| `JWT_SECRET_KEY` | 内置默认值 | JWT 签名密钥（生产环境请修改） |
| `AGENT_PYTHON_URL` | `http://localhost:8000` | Python Agent 服务地址 |

## API 端点（19 个）

### 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/health` | 健康检查 |
| `POST` | `/api/auth/send_code` | 发送登录验证码 |
| `POST` | `/api/auth/send_register_code` | 发送注册验证码 |
| `POST` | `/api/auth/login` | 登录（返回 JWT） |
| `POST` | `/api/auth/register` | 注册 |

### Chat（需要 JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/chat/sessions` | 会话列表 |
| `POST` | `/api/chat/sessions` | 创建会话 |
| `GET` | `/api/chat/sessions/{id}/messages` | 消息历史 |
| `DELETE` | `/api/chat/sessions/{id}` | 删除会话 |
| `POST` | `/api/chat/send` | **发送消息（SSE 流）** |

### 数据库连接（需要 JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/db/connections` | 连接列表 |
| `POST` | `/api/db/connections` | 新建连接 |
| `PUT` | `/api/db/connections/{id}` | 更新连接 |
| `DELETE` | `/api/db/connections/{id}` | 删除连接 |
| `POST` | `/api/db/connections/{id}/test` | 测试连接 |
| `GET` | `/api/db/connections/{id}/schema` | 获取数据库 Schema |

### 可视化 & 用户（需要 JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/viz/generate` | 生成 ECharts 图表 |
| `GET` | `/api/user/profile` | 获取用户信息 |
| `PUT` | `/api/user/profile` | 更新用户信息 |

## 项目结构

```
swpu-agent/
├── sql/init.sql                 # 数据库建表脚本
├── docker-compose.yml           # MySQL + Redis 一键启动
├── pom.xml                      # Maven 构建配置
└── src/main/java/com/swpuagent/
    ├── SwpuAgentApplication.java  # 启动类
    ├── agent/
    │   └── AgentClient.java       # Python Agent HTTP 客户端（SSE 中继）
    ├── config/                    # MyBatis / Redis / CORS 配置
    ├── controller/                # REST 控制器
    │   ├── AuthController.java    # 认证
    │   ├── ChatController.java    # 对话（SSE）
    │   ├── DbConnectionController.java
    │   ├── VisualizationController.java
    │   └── UserController.java
    ├── service/                   # 业务逻辑
    │   ├── AgentService.java      # Agent 编排（查邮箱 → 调 Python）
    │   ├── AuthService.java       # 纯 Java 认证（0 LLM 消耗）
    │   ├── ChatService.java
    │   └── VerificationCodeService.java
    ├── security/                  # JWT 过滤器
    ├── mapper/                    # MyBatis 数据访问
    ├── entity/                    # 实体类
    ├── dto/                       # 请求/响应 DTO
    └── common/                    # 全局异常处理
```

## SSE 事件类型

`POST /api/chat/send` 返回 SSE 流，事件类型如下：

| event | 含义 |
|-------|------|
| `user_saved` | 用户消息已保存 |
| `thinking` | Agent 开始思考 |
| `text` | AI 回答文本片段（流式） |
| `chart` | ECharts 图表 JSON |
| `done` | 响应结束 |
| `error` | 错误信息 |

## 注意事项

- **Python Agent 不可用时，对话功能无法使用**，但认证、会话管理等功能不受影响
- **认证不走 LLM**，验证码由 Java 生成存储在 Redis，0 额外消耗
- 生产环境请务必修改 `JWT_SECRET_KEY` 为强随机字符串
- Python Agent 和 Java 使用**不同的数据库**（`agent` vs `chatbi_db`），用户需在两个库都存在
