# swpu-agent

Spring Boot 3.5 + Java 17 智能 BI 对话后端，对接 Python LangChain AI Agent，实现自然语言数据查询与可视化。

## 架构

```
浏览器 / 前端
    │  SSE (Server-Sent Events)
    ▼
Java Backend (:8080)
    │  RestClient          JWT · 会话管理 · SSE 中继
    ▼
Python Agent (:8000)
    │  LangChain           qwen3-max LLM
    ▼
MySQL (agent) + Redis      共用单库
```

Java 和 Python 共用 `agent` 数据库。`user_info` 表由 Python 管理，Java 只读写不建表，适配其实际结构。

## 快速开始

```bash
# 1. 基础设施
docker-compose up -d                                # MySQL 8.0 + Redis 7

# 2. 初始化数据库
# 注意：user_info 如果是 MyISAM 引擎，需先转 InnoDB（MyISAM 不支持外键）
mysql -u root -proot agent -e "ALTER TABLE user_info ENGINE=InnoDB"
mysql -u root -proot < sql/init.sql                 # 建 Java 5 张表

# 3. 启动 Python Agent（另见 agent-py/README.md）
cd ../agent-py && python main.py                    # → :8000

# 4. 启动 Java
cd ../swpu-agent && mvn spring-boot:run             # → :8080

# 5. 验证
curl http://localhost:8080/api/health
# → {"code":200,"data":{"status":"UP","version":"1.0.0"}}
```

## 环境变量

| 变量 | 默认值 | 说明 |
|------|--------|------|
| `DB_PASSWORD` | `root` | MySQL 密码 |
| `REDIS_HOST` | `localhost` | Redis 地址 |
| `REDIS_PASSWORD` | (空) | Redis 密码 |
| `JWT_SECRET_KEY` | 内置默认值 | JWT 签名密钥 |
| `AGENT_PYTHON_URL` | `http://localhost:8000` | Python Agent 地址 |

## API 端点

### 公开接口

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/health` | 健康检查 |
| `POST` | `/api/auth/send_code` | 发送登录验证码（纯 Java，0 LLM） |
| `POST` | `/api/auth/send_register_code` | 发送注册验证码 |
| `POST` | `/api/auth/login` | 登录 → `{accessToken, refreshToken}` |
| `POST` | `/api/auth/register` | 注册 → `{accessToken, refreshToken}` |

### Chat（需 JWT）

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/chat/sessions` | 会话列表 |
| `POST` | `/api/chat/sessions` | 创建会话 |
| `GET` | `/api/chat/sessions/{id}/messages` | 消息历史 |
| `DELETE` | `/api/chat/sessions/{id}` | 删除会话 |
| `POST` | `/api/chat/send` | **发消息（SSE 流式返回）** |

### 数据库连接管理

| 方法 | 路径 | 说明 |
|------|------|------|
| `GET` | `/api/db/connections` | 连接列表 |
| `POST` | `/api/db/connections` | 新建连接 |
| `PUT` | `/api/db/connections/{id}` | 更新连接 |
| `DELETE` | `/api/db/connections/{id}` | 删除连接 |
| `POST` | `/api/db/connections/{id}/test` | 测试连接 |
| `GET` | `/api/db/connections/{id}/schema` | 查看库表结构 |

### 其他

| 方法 | 路径 | 说明 |
|------|------|------|
| `POST` | `/api/viz/generate` | 生成 ECharts 图表 |
| `GET` | `/api/user/profile` | 用户信息 |
| `PUT` | `/api/user/profile` | 更新用户信息 |

## SSE 事件流

`POST /api/chat/send` 返回 Server-Sent Events：

```
event:user_saved  → {"message_id":1}       用户消息已保存
event:thinking    → 正在分析您的问题…        Agent 开始思考
event:text        → 您好                     AI 回答片段（多次流式推送）
event:chart       → {"option":{...}}         ECharts 图表（图表生成时）
event:done        →                          流结束
event:error       → 错误信息                 异常时
```

## 数据库

### 表结构

```
agent（Java + Python 共用）
├── user_info            用户 · 角色权限（Python 管理，Java 适配）
├── users                JWT 登录账号
├── chat_sessions        对话会话
├── chat_messages        对话消息
├── db_connections       外部数据库连接
├── tool_invocations     Agent 工具调用日志
├── customer / products / orders / customer_behavior / sales  业务数据
```

### 关键注意

- `user_info` 必须为 **InnoDB** 引擎（默认 MyISAM 不支持外键，Java 的 `chat_sessions` 等表引用 `user_info(id)`）
- Java 的 `UserInfoMapper` 已适配实际列：`id, user_name, email, role, password`
- Java 不建 `user_info` 表，不修改其结构
- `init.sql` 只建 Java 的 5 张表，不含 `user_info`

## 项目结构

```
swpu-agent/
├── sql/init.sql                     Java 5 表建表脚本
├── docker-compose.yml               MySQL + Redis
├── pom.xml                          Maven
└── src/main/java/com/swpuagent/
    ├── SwpuAgentApplication.java
    ├── agent/
    │   └── AgentClient.java             调用 Python /chat（SSE 中继）
    ├── controller/
    │   ├── AuthController.java          认证（纯 Java，0 LLM）
    │   ├── ChatController.java          对话 SSE
    │   ├── DbConnectionController.java
    │   ├── VisualizationController.java
    │   └── UserController.java
    ├── service/
    │   ├── AgentService.java            编排：查邮箱 → 调 Python
    │   ├── AuthService.java             登录/注册逻辑
    │   ├── ChatService.java
    │   └── VerificationCodeService.java
    ├── security/                        JWT 过滤器
    ├── mapper/                          MyBatis
    ├── entity/
    └── common/                          异常处理
```

## 注意

- **Auth 不走 LLM**：验证码 Java 生成存 Redis，零 AI 消耗
- Python Agent 不可用时 Chat 报错，认证和会话管理照常工作
- 生产环境请修改 `JWT_SECRET_KEY`，清理测试数据
- Python Agent 需用 Python 3.12 环境运行（langgraph 兼容性）
