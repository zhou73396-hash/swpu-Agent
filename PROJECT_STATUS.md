# PROJECT_STATUS - 2026-07-11

## 能启动的模块

| 模块 | 状态 | 说明 |
|------|------|------|
| MySQL 8.0 | 正常 | 容器 swpu-mysql，端口 3307，root 密码 root |
| Redis 7 | 正常 | 容器 swpu-redis，端口 6379 |
| Mock Agent | 正常 | 容器 swpu-mock-agent，端口 8000，5 种模式可用 |
| Java 网关 | 正常 | 容器 swpu-gateway，端口 8080，版本 auth-v2 |

## 已验证链路 (15:00-17:00)

| 测试场景 | 方法 | 结果 |
|----------|------|------|
| 发送验证码 | POST /api/auth/send_code | 200，Redis 写入 code |
| 登录获取 Token | POST /api/auth/login | 200，Access + Refresh Token |
| SSE 正常流 | POST /api/chat/send question=hello | 3 段: text, text, done |
| SSE HTTP 500 | POST /api/chat/send question=http500 | event:error HTTP_ERROR |
| SSE Malformed | POST /api/chat/send question=malformed | event:error PROTOCOL_ERROR |
| SSE Disconnect | POST /api/chat/send question=disconnect | event:error UNAVAILABLE |
| SSE Timeout | 项目文档已记录 | READ_TIMEOUT at 1.06s（需设短超时） |

## 阻塞项

无阻塞项。所有环境就绪，核心链路可演示。

## 已知缺口 (不阻塞 M1 演示)

- 验证码发送依赖 Mock Agent 返回 200（正常）
- 验证码为 4 位数字，Redis TTL 默认
- 不做 RBAC / DB CRUD / 验证码限流 / OpenAPI
- 不做新大功能

## 下一步

1. 提交 Stage 5 改动（Mock Agent + README）
2. 准备简历与项目描述对齐
3. 开始投递（按计划 7/14 起）

## 演练命令 (2 分钟演示用)

```bash
# 1. 启动所有服务
docker compose up -d

# 2. 检查健康
docker compose ps
# 应有 4 个 healthy：mysql, redis, mock-agent, gateway

# 3. 发送验证码
curl -X POST http://localhost:8080/api/auth/send_code \
  -H 'Content-Type: application/json' \
  -d '{"email":"mock-e2e@example.com"}'
# → {"code":200,"message":"success"}

# 4. 获取验证码
docker exec swpu-redis redis-cli GET "login:code:mock-e2e@example.com"
# → 4 位数字

# 5. 登录
curl -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"mock-e2e@example.com","code":"5379"}'
# → {"code":200,"data":{"accessToken":"...","refreshToken":"..."}}

# 6. SSE 流式调用
curl -X POST http://localhost:8080/api/chat/send \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer eyJ...' \
  -d '{"question":"hello"}'
# → event:text ... event:done

# 7. 异常场景
# http500 / malformed / disconnect
curl ... -d '{"question":"http500"}'
# → event:error {"code":"HTTP_ERROR",...}
```

## 安全确认

- .env 已 gitignore，不存在于仓库
- JWT_SECRET_KEY 使用环境变量，docker-compose 有本地开发默认值
- 无真实 API Key、LAN IP 泄露
- 测试用例使用独立 test key

## 回顾产出

| 时间 | 产出 |
|------|------|
| 上午 | Stage 5: Mock Agent + SSE 回归 + PROJECT_PROGRESS.md |
| 15:00-17:00 | 15 题项目验收问答 + 验收文档 |
| 17:00-18:30 | 全链路复测：登录→SSE 正常/500/malformed/disconnect |
| 18:30-19:00 | PROJECT_STATUS.md + 安全审计 |
