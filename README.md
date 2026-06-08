# swpu-agent

> Spring Boot 3.5 + Java 17 智能 BI 对话后端，对接 Python LangChain AI Agent 实现自然语言数据查询与可视化。

## 架构

```
浏览器/前端
    │  SSE (Server-Sent Events)
    ▼
Java Backend (:8080) ──┐
    │  RestClient       │
    ▼                   │  共用 MySQL
Python Agent (:8000) ───┘  agent 数据库
    │
    └── LLM (qwen3-max)
```

- **Java 后端**：JWT 认证、会话管理、数据库连接管理、可视化生成、SSE 中继
- **Python Agent**：LangChain/LangGraph AI 智能体，负责 SQL 生成、图表生成、数据分析（独立部署，端口 8000）
- **共用数据库**：Java 和 Python 共用同一个 `agent` 数据库，Python 管理 `user_info` 表，Java 管理会话/消息等表

## 前置条件

| 依赖 | 版本 | 用途 |
|------|------|------|
| JDK | 17+ | Java 运行环境 |
| Maven | 3.8+ | 构建工具 |
| MySQL | 8.0+ | 数据存储（与 Python Agent 共用 `agent` 库） |
| Redis | 6.0+ | 验证码 / Token 缓存 |
| Python Agent | — | AI 对话引擎（独立服务，端口 8000） |

## 快速开始

### 1. 启动基础设施 + 初始化数据库

```bash
docker-compose up -d                              # MySQL + Redis
mysql -u root -proot < sql/init.sql               # 一键建库 + 建表 + 测试数据
```

### 2. 启动 Python Agent

```bash
cd agent-py
python main.py    # → localhost:8000
```

### 3. 配置环境变量（可选，默认值即可跑）

```bash
export DB_PASSWORD=your_mysql_password
export REDIS_PASSWORD=your_redis_password    # 如果 Redis 有密码
export JWT_SECRET_KEY=your-32-char-secret-key
export AGENT_PYTHON_URL=http://localhost:8000
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
├── sql/init.sql                 # 一键建库 + 全部表 + 测试数据
├── docker-compose.yml           # MySQL + Redis 一键启动
├── pom.xml
└── src/main/java/com/swpuagent/
    ├── SwpuAgentApplication.java
    ├── agent/
    │   └── AgentClient.java         # Python Agent HTTP 客户端（SSE 中继）
    ├── config/
    ├── controller/
    │   ├── AuthController.java      # 认证（纯 Java，0 LLM）
    │   ├── ChatController.java      # 对话（SSE）
    │   ├── DbConnectionController.java
    │   ├── VisualizationController.java
    │   └── UserController.java
    ├── service/
    │   ├── AgentService.java        # Agent 编排
    │   ├── AuthService.java
    │   ├── ChatService.java
    │   └── VerificationCodeService.java
    ├── security/                    # JWT 过滤器
    ├── mapper/                      # MyBatis
    ├── entity/
    ├── dto/
    └── common/                      # 全局异常处理
```

## SSE 事件类型

`POST /api/chat/send` 返回 SSE 流：

| event | 含义 |
|-------|------|
| `user_saved` | 用户消息已保存 |
| `thinking` | Agent 开始思考 |
| `text` | AI 回答片段（流式输出） |
| `chart` | ECharts 图表 JSON |
| `done` | 响应结束 |
| `error` | 错误信息 |

## 数据库说明

所有表都在 `agent` 库中，`init.sql` 一键创建：

| 表 | 用途 |
|----|------|
| `user_info` | 用户信息 + 角色权限（Python Agent 权限中间件依赖） |
| `users` | JWT 登录账号 |
| `chat_sessions` | 对话会话 |
| `chat_messages` | 对话消息 |
| `db_connections` | 外部数据库连接配置 |
| `tool_invocations` | Agent 工具调用审计日志 |
| `customer` | 客户数据（Python SQL 问答用） |
| `products` | 产品数据 |
| `orders` | 订单数据 |
| `customer_behavior` | 客户行为数据 |
| `sales` | 销售统计数据 |

## 注意事项

- **认证不走 LLM**，验证码由 Java 生成存储在 Redis，0 额外消耗
- 生产环境请修改 `JWT_SECRET_KEY` 为强随机字符串
- Python Agent 不可用时，对话功能报错，但认证/会话管理不受影响
- `init.sql` 包含测试数据，生产环境请清理
