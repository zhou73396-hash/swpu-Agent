# Standard Workflow

## 1. Environment Setup

### 1.1 Prerequisites

| Component | Version | Check Command |
|-----------|---------|---------------|
| JDK | 17+ | `java -version` |
| Maven | 3.8+ | `mvn -version` |
| MySQL | 8.0+ | `mysql --version` |
| Redis | 6.0+ | `redis-cli ping` |
| Python | 3.10+ | `python --version` |
| Git | 2.30+ | `git --version` |

### 1.2 Repository

```bash
git clone <repository-url>
cd swpu-agent
git checkout dev          # development branch
```

### 1.3 Configuration

Copy or edit `src/main/resources/application.yaml`:

```yaml
spring:
  datasource:
    url: jdbc:mysql://<host>:3306/agent?...   # MySQL address
    username: root
    password: root
  data:
    redis:
      host: <host>                             # Redis address
      port: 6379

agent:
  base-url: http://<host>:8000                 # Python agent address
```

---

## 2. Daily Development Workflow

### 2.1 Start Dependencies

```bash
# Option A: Docker
docker-compose up -d                           # Start MySQL + Redis

# Option B: Manual
# Start MySQL 8.0 and Redis 6.0 on your machine
```

### 2.2 Initialize Database

Run once after first clone or schema change:

```bash
# Ensure user_info uses InnoDB (required for foreign keys)
mysql -u root -proot agent -e "ALTER TABLE user_info ENGINE=InnoDB"

# Create Java-owned tables
mysql -u root -proot < sql/init.sql
```

### 2.3 Start Python Agent

```bash
cd agent-py
pip install -r requirements.txt                # First time only
python main.py                                 # → localhost:8000
```

Verify: `curl http://localhost:8000/docs` should return Swagger UI.

### 2.4 Start Java Wrapper

```bash
# In project root
mvn spring-boot:run                            # → localhost:8080
```

### 2.5 Verify All Services

| Service | URL | Expected |
|---------|-----|----------|
| Java Wrapper | `http://localhost:8080/api/health` | `{"code":200,"message":"success"}` |
| Python Agent | `http://localhost:8000/docs` | Swagger UI page |

---

## 3. Code Workflow

### 3.1 Branch Strategy

```
master        ← production releases
  └── dev     ← development integration
       └── feature/xxx   ← feature branches
       └── fix/xxx       ← bugfix branches
```

### 3.2 Daily Routine

```bash
# 1. Sync latest
git checkout dev
git pull origin dev

# 2. Create feature branch
git checkout -b feature/my-feature

# 3. Develop → compile frequently
mvn clean compile

# 4. Commit
git add .
git commit -m "feat: description"

# 5. Push
git push origin feature/my-feature

# 6. Merge back to dev when done
git checkout dev
git merge feature/my-feature
git push origin dev
```

### 3.3 Commit Message Convention

```
<type>: <description>

Types:
  feat     — new feature
  fix      — bug fix
  docs     — documentation only
  refactor — code restructuring
  test     — adding tests
  config   — configuration changes

Examples:
  feat: add chat keyword routing to Python agent
  fix: resolve JWT expiration edge case
  docs: update API reference with new endpoints
  config: update Redis host through REDIS_HOST
```

---

## 4. Build & Compile

### 4.1 Compile Only

```bash
mvn clean compile
```

Must pass with **56 source files compiled** and **BUILD SUCCESS**.

### 4.2 Package (JAR)

```bash
mvn clean package -DskipTests
# Output: target/swpu-agent-0.0.1-SNAPSHOT.jar
```

### 4.3 Run Packaged JAR

```bash
java -jar target/swpu-agent-0.0.1-SNAPSHOT.jar
```

---

## 5. Adding a New Feature

### 5.1 New Entity + Mapper + Service

Use the standard MyBatis Plus pattern:

```
Entity → Mapper → Service Interface → Service Impl
```

**Step 1: Create Entity**

```java
// entity/NewTable.java
@Data
@TableName("new_table")
public class NewTable {
    private Long id;
    private String name;
}
```

**Step 2: Create Mapper**

```java
// mapper/NewTableMapper.java
@Mapper
public interface NewTableMapper extends BaseMapper<NewTable> {
}
```

**Step 3: Create Service Interface**

```java
// service/NewTableService.java
public interface NewTableService extends IService<NewTable> {
}
```

**Step 4: Create Service Implementation**

```java
// service/impl/NewTableServiceImpl.java
@Service
public class NewTableServiceImpl extends ServiceImpl<NewTableMapper, NewTable>
        implements NewTableService {
}
```

**Step 5: Compile and Verify**

```bash
mvn clean compile
```

### 5.2 New Controller Endpoint

**Step 1: Create Request DTO**

```java
// dto/request/XxxRequest.java
@Data
public class XxxRequest {
    @NotBlank(message = "field cannot be empty")
    private String field;
}
```

**Step 2: Create Response DTO (if needed)**

```java
// dto/response/XxxResponse.java
@Data
@Builder
public class XxxResponse {
    private String data;
}
```

**Step 3: Add Service Method**

```java
// In service interface
public interface XxxService {
    Result<XxxResponse> doSomething(XxxRequest request);
}

// In service impl
@Service
@RequiredArgsConstructor
public class XxxServiceImpl implements XxxService {
    @Override
    public Result<XxxResponse> doSomething(XxxRequest request) {
        // business logic
        return Result.success(response);
    }
}
```

**Step 4: Add Controller Endpoint**

```java
// controller/XxxController.java
@RestController
@RequestMapping("/api/xxx")
@RequiredArgsConstructor
public class XxxController {
    private final XxxService xxxService;

    @PostMapping("/action")
    public Result<XxxResponse> action(@Valid @RequestBody XxxRequest request) {
        return xxxService.doSomething(request);
    }
}
```

### 5.3 JWT-Protected Endpoint

```java
@PostMapping("/secure-action")
public Result<XxxResponse> secureAction(@Valid @RequestBody XxxRequest request,
                                         HttpServletRequest httpRequest) {
    String userId = (String) httpRequest.getAttribute("userId");
    String role = (String) httpRequest.getAttribute("role");
    // userId and role injected by JwtAuthFilter
    return xxxService.doSecureAction(request, userId, role);
}
```

Ensure the path prefix is in `JwtAuthFilter.PROTECTED_PATHS`.

---

## 6. API Testing

### 6.1 Auth Flow Test

```bash
# Step 1: Send login verification code
curl -X POST http://localhost:8080/api/auth/send_code \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com"}'

# Step 2: Check Redis for the code
redis-cli GET "login:code:test@example.com"

# Step 3: Login with the code
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "test@example.com", "code": "483156"}'

# Response contains accessToken → use for authenticated requests
```

### 6.2 Chat Test

```bash
# Step 1: Login and save the token
TOKEN="eyJhbGciOiJIUzI1NiJ9..."

# Step 2: Send chat message (SSE streaming)
curl -N -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question": "Show all products"}'

# Step 3: Test keyword routing
curl -N -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question": "Generate bar chart of top 5 products"}'

# Step 4: Test analyze
curl -N -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{"question": "Analyze sales data by category"}'
```

### 6.3 Token Expiry Test

```bash
# Test with expired/invalid token
curl -X POST http://localhost:8080/api/chat/send \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer invalid_token" \
  -d '{"question": "test"}'

# Expected: HTTP 401 {"code":401,"message":"Invalid or expired token"}
```

---

## 7. Debugging

### 7.1 Redis

```bash
# Check stored codes
redis-cli KEYS "login:code:*"
redis-cli KEYS "register:code:*"

# Get a specific code
redis-cli GET "login:code:user@example.com"

# Check TTL
redis-cli TTL "login:code:user@example.com"

# Delete a code
redis-cli DEL "login:code:user@example.com"

# Monitor all Redis operations
redis-cli MONITOR
```

### 7.2 MySQL

```bash
# Check user_info table
mysql -u root -proot agent -e "SELECT id, user_name, email, role FROM user_info"

# Check table engine
mysql -u root -proot agent -e "SHOW TABLE STATUS WHERE Name='user_info'"
# Must show Engine=InnoDB

# Check Java tables exist
mysql -u root -proot agent -e "SHOW TABLES"
```

### 7.3 Python Agent

```bash
# Test SystemAgent directly
curl -X POST http://<host>:8000/agent/system/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "send login verification code 123456 to email test@example.com"}'

# Test SQL Agent directly
curl -N -X POST http://<host>:8000/agent/sql/chat \
  -H "Content-Type: application/json" \
  -d '{"question": "SELECT 1", "user_id": "test@example.com"}'

# Check Python logs
tail -f agent-py/app/logs/app.log
```

### 7.4 Java Wrapper Logging

MyBatis SQL logging is enabled by default:

```yaml
mybatis-plus:
  configuration:
    log-impl: org.apache.ibatis.logging.stdout.StdOutImpl
```

All SQL queries appear in the console. Agent client calls log at `INFO` level with prefix `AgentClient`.

---

## 8. Common Issues

### 8.1 Build Fails: "非法的类型开始" or syntax errors

```
→ Check UserServiceImpl.java — it has incomplete code
→ Check for missing imports in any modified file
→ Run: mvn clean compile and fix the first error
```

### 8.2 Redis Connection Refused

```
→ Check Redis is running: redis-cli ping (expect PONG)
→ Verify application.yaml redis.host matches
→ Check firewall rules
```

### 8.3 MySQL FK Constraint Fails

```
→ user_info must be InnoDB (not MyISAM)
→ Run: ALTER TABLE user_info ENGINE=InnoDB
→ Re-run: mysql -u root -proot < sql/init.sql
```

### 8.4 Python Agent Unavailable

```
→ Check agent.base-url in application.yaml
→ Verify Python agent is running: curl http://<host>:8000/docs
→ Check Python agent logs for errors
→ Java returns: {"code":500,"message":"Agent service unavailable: ..."}
```

### 8.5 JWT Token Invalid / 401

```
→ Token expired (30min access token)
→ Token not sent as "Bearer <token>"
→ Secret key mismatch between issuer and verifier
→ Protected path not matched by JwtAuthFilter
```

### 8.6 Verification Code Not Received

```
→ Check Redis: redis-cli GET "login:code:{email}"
→ Code TTL is 60 seconds — may have expired
→ Check Python SystemAgent logs for email send errors
→ Check SMTP configuration in Python .env
```

---

## 9. Deployment

### 9.1 Production Build

```bash
# 1. Update application.yaml for production
#    - Change datasource/redis hosts
#    - Change jwt.secret-key to a strong secret
#    - Change CORS origins to frontend domain

# 2. Build
mvn clean package -DskipTests

# 3. Deploy
scp target/swpu-agent-0.0.1-SNAPSHOT.jar user@server:/opt/swpu-agent/

# 4. Run
java -jar /opt/swpu-agent/swpu-agent-0.0.1-SNAPSHOT.jar
```

### 9.2 Production Checklist

| Item | Check |
|------|-------|
| JWT secret changed from dev default | ☐ |
| CORS origins restricted | ☐ |
| MySQL connection pool configured | ☐ |
| Redis password set | ☐ |
| Python agent accessible from Java host | ☐ |
| Logging configured (not stdout) | ☐ |
| Health endpoint monitored | ☐ |

---

## 10. Project Structure Reference

```
swpu-agent/
├── docs/
│   ├── tech-documentation.md     # Technical architecture & API reference
│   └── standard.md               # This file — standard workflow
├── sql/
│   └── init.sql                  # DDL for Java-owned tables
├── src/main/java/com/swpuagent/
│   ├── SwpuAgentApplication.java # Entry point
│   ├── agent/                    # Python agent HTTP client
│   ├── common/                   # Exception handling
│   ├── config/                   # Redis, CORS config
│   ├── controller/               # REST controllers
│   ├── dto/                      # Request/response DTOs
│   ├── entity/                   # Database entities
│   ├── mapper/                   # MyBatis Plus mappers
│   ├── security/                 # JWT auth
│   ├── service/                  # Business logic
│   ├── utils/                    # Utilities
│   └── vo/                       # View objects
├── src/main/resources/
│   └── application.yaml          # Application configuration
├── pom.xml                       # Maven dependencies
├── CLAUDE.md                     # Project guidance for Claude Code
└── docker-compose.yml            # MySQL + Redis containers
```
