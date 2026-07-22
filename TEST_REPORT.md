# swpu-agent 自动化测试报告

> 最近验证日期：2026-07-22
> 环境：Windows 11、Java 17.0.16、Spring Boot 3.5.14、Maven Surefire 3.5.5

## 验证结论

```text
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

项目当前包含 9 个测试类、30 个自动化测试用例。测试可在不启动真实 Python Agent、不调用大模型 API 的情况下运行。

## 测试分布

| 测试类 | 用例数 | 主要覆盖范围 |
|---|---:|---|
| `AgentClientTest` | 3 | JSON 响应、HTTP 错误和协议异常转换 |
| `AuthControllerTest` | 3 | 认证接口请求校验与响应状态 |
| `HealthControllerTest` | 1 | 健康检查接口 |
| `JwtAuthFilterTest` | 5 | Bearer Token、无效 Token、用户上下文与清理 |
| `JwtUtilTest` | 4 | Access/Refresh Token 生成、解析与类型校验 |
| `RefreshTokenStoreImplTest` | 1 | Refresh Token 仅以 SHA-256 摘要存储 |
| `AuthServiceImplTest` | 11 | 登录、注册、刷新、退出、异常路径与并发刷新 |
| `ChatServiceImplTest` | 1 | SSE 请求调度与生命周期行为 |
| `SwpuAgentApplicationTests` | 1 | Spring Boot 应用上下文加载 |
| **合计** | **30** | **0 失败、0 错误、0 跳过** |

## 重点场景

### Refresh Token 安全

- Redis 中只保存 Token 的 SHA-256 摘要，不保存明文。
- Refresh Token 包含唯一 `jti`，Redis Key 按用户和会话隔离。
- 旧 Token 通过 Lua 脚本完成校验、消费和新 Token 写入。
- 已消费、过期或签名被篡改的 Refresh Token 均被拒绝。
- 退出仅撤销客户端提交的当前会话。

### 并发刷新

`AuthServiceImplTest.concurrentRefreshShouldAllowOnlyOneSuccess` 使用两个并发调用者验证服务层竞争结果：同一个旧 Refresh Token 只允许一次刷新成功，另一次返回 Token 已被消费的认证错误。

该用例用于验证业务并发语义，不等同于吞吐量或性能压测。真实 Redis 原子性由 `RefreshTokenStoreImpl` 中的 Lua 脚本保证。

### Agent 与 SSE

- Agent HTTP 非成功状态转换为统一错误码。
- 上游返回非法 JSON/SSE 数据时转换为协议异常。
- SSE 生命周期覆盖完成、超时、客户端断开和上游取消。
- Mock Agent 可模拟正常、慢响应、HTTP 500、畸形数据和连接中断。

## 本地运行

Windows PowerShell：

```powershell
.\mvnw.cmd test
```

Linux、macOS 或 GitHub Actions：

```bash
./mvnw --batch-mode test
```

预期结果：

```text
Tests run: 30, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

## 测试边界

- 当前自动化测试主要使用 JUnit 5、Mockito 和 Spring Boot Test。
- 30 项测试不要求启动 MySQL、Redis 或真实 Python Agent。
- Docker Compose 端到端联调、真实 Redis Lua 并发验证和外部 Agent 兼容性应作为独立集成验证执行。
- 当前启动测试仍会报告部分外部数据实体缺少 MyBatis-Plus `@TableId` 的警告；这些表不由 `sql/init.sql` 创建，不能在缺少真实 DDL 的情况下猜测主键。
