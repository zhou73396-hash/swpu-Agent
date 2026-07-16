# swpu-agent 完整测试报告

> 测试日期：2026-06-07 | 项目：swpu-agent | 47 Java 文件 | 19 API 端点

---

## 一、测试概览

| 测试类型 | 范围 | 结果 |
|----------|------|------|
| Maven 单元测试 | Spring 上下文加载 | ✅ 1/1 通过 |
| API 端点验证 | 19 端点 + 12 边界探测 | ✅ 31/31 通过 |
| **合计** | | **✅ 全部通过** |

**测试环境：**

| 项目 | 值 |
|------|-----|
| 操作系统 | Windows 11 |
| Java | 17.0.16 |
| Maven | 3.9.11 |
| Spring Boot | 3.5.14 |
| MySQL | 8.0.42 |
| Redis | 6.x (localhost:6379) |

---

## 二、Maven 自动化测试

### 2.1 测试范围

- 测试文件：`src/test/java/com/swpuagent/SwpuAgentApplicationTests.java`
- 测试方法：`contextLoads`
- 覆盖内容：Spring Boot 应用上下文加载（不覆盖 API 行为、数据库读写、Redis、JWT、SSE 等）

### 2.2 执行与结果

```bash
# Maven Wrapper 尝试（失败）
.\mvnw.cmd test
# → Cannot index into a null array. Cannot start maven from wrapper.

# 本机 Maven（成功）
mvn test
```

```
Tests run: 1, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

### 2.3 问题：Maven Wrapper 在 PowerShell 下不可用

**现象：** `.\mvnw.cmd test` 报 `Cannot index into a null array`。

**根因分析：** `mvnw.cmd` 脚本中 `%MAVEN_PROJECTBASEDIR%` 变量解析失败。这是 Maven Wrapper 在 Windows PowerShell 下的已知兼容性问题。

**建议修复：**
```powershell
# 方案 A：用 cmd 执行
cmd /c "mvnw.cmd test"

# 方案 B：检查 JAVA_HOME 是否设置，Maven Wrapper 依赖它定位 JDK
echo $env:JAVA_HOME
```

---

## 三、API 端点手动验证

> 应用运行在 `localhost:8080`，通过 curl 发送请求并验证响应。

### 3.1 公开端点（5/5 通过）

| # | 方法 | 路径 | HTTP | 说明 |
|---|------|------|------|------|
| 1 | GET | `/api/health` | 200 | `{"status":"UP","version":"1.0.0"}` |
| 2 | POST | `/api/auth/send_code` | 200 | 发送登录验证码到 Redis |
| 3 | POST | `/api/auth/send_register_code` | 200 | 发送注册验证码到 Redis |
| 4 | POST | `/api/auth/login` | 200 | 返回 JWT accessToken + refreshToken |
| 5 | POST | `/api/auth/register` | 200 | 新建用户 + 返回 JWT |

### 3.2 Chat 模块（5/5 通过）

| # | 方法 | 路径 | HTTP | 说明 |
|---|------|------|------|------|
| 6 | GET | `/api/chat/sessions` | 200 | 会话列表 |
| 7 | POST | `/api/chat/sessions` | 200 | 创建会话 |
| 8 | GET | `/api/chat/sessions/{id}/messages` | 200 | 消息历史 |
| 9 | DELETE | `/api/chat/sessions/{id}` | 200 | 软删除 |
| 10 | POST | `/api/chat/send` | 200 | SSE 流式 Agent 管道 |

**SSE 事件流验证：** `user_saved → thinking → tool_call → sql → tool_result → text → done` 全部正确推送，`text/event-stream` Content-Type 正确。

### 3.3 DB 连接模块（6/6 通过）

| # | 方法 | 路径 | HTTP | 说明 |
|---|------|------|------|------|
| 11 | GET | `/api/db/connections` | 200 | 列出数据源 |
| 12 | POST | `/api/db/connections` | 200 | 添加数据源（密码 AES 加密） |
| 13 | PUT | `/api/db/connections/{id}` | 200 | 更新数据源 |
| 14 | DELETE | `/api/db/connections/{id}` | 200 | 软删除 |
| 15 | POST | `/api/db/connections/{id}/test` | 200 | 连通性测试 |
| 16 | GET | `/api/db/connections/{id}/schema` | 200 | Schema 检索 |

**连通性测试结果：** 真实 JDBC 连接 MySQL 8.0.42，延迟 13ms，返回 `{"status":"SUCCESS","dbVersion":"8.0.42","latencyMs":13}`。

**Schema 检索结果：** 正确返回 `chatbi_db` 全部 6 张表（user_info, users, chat_sessions, chat_messages, db_connections, tool_invocations），含完整字段名、类型、是否可空、主键信息。

### 3.4 可视化 + 用户（3/3 通过）

| # | 方法 | 路径 | HTTP | 说明 |
|---|------|------|------|------|
| 17 | POST | `/api/viz/generate` | 200 | ECharts 图表配置（bar/line/pie/auto） |
| 18 | GET | `/api/user/profile` | 200 | 获取用户资料 |
| 19 | PUT | `/api/user/profile` | 200 | 更新用户资料 |

**图表类型验证：**
- `chartType=bar` → 正确生成柱状图配置（category xAxis + value yAxis + series）
- `chartType=auto` (日期+数值) → 自动检测为 line
- `chartType=auto` (文本+数值 ≤10) → 自动检测为 pie

---

## 四、边界/异常路径探测（12/12 通过）

| # | 场景 | 预期 HTTP | 实际 | 响应消息 |
|---|------|-----------|------|----------|
| E1 | 无 Authorization header | 401 | ✅ | `Missing or invalid Authorization header` |
| E2 | 无效 token | 401 | ✅ | `Invalid or expired token` |
| E3 | 缺少必填字段 email | 400 | ✅ | `email: 邮箱不能为空` |
| E4 | 非法邮箱格式 | 400 | ✅ | `email: 邮箱格式不正确` |
| E5 | 缺少必填字段 code | 400 | ✅ | `code: 验证码不能为空` |
| E6 | 错误 HTTP 方法 | 405 | ✅ | `Method not allowed` |
| E7 | 不存在的路由 | 404 | ✅ | `Resource not found` |
| E8 | 非法 JSON body | 400 | ✅ | `Invalid request body` |
| E9 | 未注册邮箱发登录码 | 400 | ✅ | `邮箱未注册` |
| E10 | 已注册邮箱发注册码 | 400 | ✅ | `邮箱已注册，请直接登录` |
| E11 | 错误验证码登录 | 400 | ✅ | `验证码错误或已过期` |
| E12 | 受保护路由错误方法 | 405 | ✅ | `Method not allowed` |

**异常处理覆盖的 HTTP 状态码：** 200, 400, 401, 404, 405, 500 — 全部由 `GlobalExceptionHandler` 统一处理，响应格式一致（`ApiResponse` 格式）。

---

## 五、代码修复记录

测试过程中发现并修复了 3 个问题（已在 commit `0b9ef83`）：

| 问题 | 修复前 | 修复后 | 修改文件 |
|------|--------|--------|----------|
| 错误 HTTP 方法返回 500 | 405 未处理 | 添加 `HttpRequestMethodNotSupportedException` → 405 | GlobalExceptionHandler |
| 非法 JSON 返回 500 | 400 未处理 | 添加 `HttpMessageNotReadableException` → 400 | GlobalExceptionHandler |
| 不存在路径返回 401 | JWT Filter `/api/*` 拦截 | 精确路径 + `NoHandlerFoundException` → 404 | FilterConfig + GlobalExceptionHandler |

---

## 六、测试覆盖缺口

### 当前已有

- [x] `contextLoads` — 应用上下文启动
- [x] 19 个端点手动验证 — 所有 API 行为正确
- [x] 12 个边界探测 — 异常处理路径正确

### 待补充

- [ ] **Service 层单元测试** — AuthService、ChatService、DatabaseConnectionService 等
- [ ] **验证码生命周期测试** — Redis 写入→比对→删除→过期
- [ ] **JWT 过期/篡改测试** — 自动化验证 token 校验逻辑
- [ ] **并发场景测试** — 同一邮箱并发注册、会话并发操作
- [ ] **DB 连接测试** — 错误密码、超时、不存在的 host
- [ ] **Maven Wrapper 修复** — PowerShell 下正常运行

---

## 七、结论

**项目测试结论：✅ PASS**

- Maven 构建 + 上下文加载：通过
- 19 个 API 端点：全部通过
- 12 个边界/异常探测：全部通过
- 异常处理体系：7 种异常类型全部覆盖，HTTP 状态码正确
- 已修复问题：3 个（405/400/404 异常处理）

项目在 Java 17 + MySQL 8.0 + Redis 6.x 环境下功能完整，API 行为符合规格说明书。建议后续补充 Service 层单元测试和并发场景测试。
