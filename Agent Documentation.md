# Agent Documentation

# Project Architecture and Overall Design



## 1\. Project Overview



xnsy\_agent is a multi\-agent AI backend system built with **FastAPI \+ LangChain/LangGraph**, providing a ChatBI \(Conversational Business Intelligence\) experience\. Users ask questions in natural language, and the system automatically routes them to specialized agents for database queries, chart generation, data analysis, news retrieval, train ticket queries, file analysis, and more\.



### 1\.1 Project Goals



- Provide natural language\-driven data query and analysis capabilities

- Implement role\-based fine\-grained data permission control

- Support multi\-domain intelligent Q\&A \(SQL, charts, news, train tickets, etc\.\)

- Provide streaming \(SSE\) responses for an improved user experience

    

## 2\. Technology Stack



|Component|Choice|Description|
|---|---|---|
|Web Framework|FastAPI|Async high\-performance, native SSE support|
|Server|Uvicorn|ASGI server|
|AI Framework|LangChain \+ LangGraph|Custom Agent construction and state management|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope\)|Accessed via compatible OpenAI API|
|Database|MySQL 8\.0|Business data storage|
|Cache|Redis|Temporary verification code storage|
|Email|SMTP\_SSL \(QQ Mail\)|Send verification code emails|
|Logging|RotatingFileHandler|Log rotation|



### 2\.1 Technology Selection Rationale



|Decision|Recommended|Alternatives|Trade\-off|
|---|---|---|---|
|AI Framework|LangChain \+ LangGraph|Native OpenAI API, Semantic Kernel|LangChain provides complete Agent abstractions, tool injection, and middleware; LangGraph supports stateful graph execution and streaming|
|LLM|Qwen3\-Max|DeepSeek, GPT\-4o|Alibaba Cloud DashScope offers low\-latency access in China; compatible OpenAI API format for easy switching|
|Web Framework|FastAPI|Flask, Django|FastAPI natively supports async SSE streaming, outperforms Flask|



## 3\. System Architecture



```Plain Text
┌─────────────────────────────────────────────────────────┐
│                  Frontend (localhost:8081)                │
└──────────────────────┬──────────────────────────────────┘
                       │ HTTP / SSE
┌──────────────────────▼──────────────────────────────────┐
│                  FastAPI Application                      │
│  ┌────────────────────────────────────────────────────┐  │
│  │                   Middleware                        │  │
│  │              CORS (localhost:8081)                  │  │
│  └──────────────────────┬─────────────────────────────┘  │
│                         │                                │
│  ┌──────────────────────▼─────────────────────────────┐  │
│  │                   Routers                           │  │
│  │  ┌─────────────────┐    ┌──────────────────────┐   │  │
│  │  │  System Router   │    │    Chat Router       │   │  │
│  │  │  /send_code      │    │  /chat  (SSE)        │   │  │
│  │  │  /login          │    │  /upload             │   │  │
│  │  │  /register       │    └──────────┬───────────┘   │  │
│  │  └─────────────────┘               │                │  │
│  └─────────────────────────────────────┼──────────────┘  │
│                                        │                │
│  ┌─────────────────────────────────────▼──────────────┐  │
│  │                 Agent Layer                         │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │  │
│  │  │ System   │ │ SQL      │ │ ECharts          │   │  │
│  │  │ Agent    │ │ Agent    │ │ Agent            │   │  │
│  │  └──────────┘ └──────────┘ └──────────────────┘   │  │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────────────┐   │  │
│  │  │ Analyze  │ │ File     │ │ News             │   │  │
│  │  │ Agent    │ │ Analyze  │ │ Agent            │   │  │
│  │  │          │ │ Agent    │ │                  │   │  │
│  │  └──────────┘ └──────────┘ └──────────────────┘   │  │
│  │  ┌──────────┐                                      │  │
│  │  │ Train    │                                      │  │
│  │  │ Agent    │                                      │  │
│  │  └──────────┘                                      │  │
│  └─────────────────────────────────────────────────────┘  │
│                         │                                │
│  ┌──────────────────────▼──────────────────────────────┐ │
│  │                   Tool Layer                         │ │
│  │  mysql_tool │ send_email │ news_tool │ train_tool   │ │
│  │  docx_read_tool │ docx_write_tool                   │ │
│  └──────────────────────────────────────────────────────┘ │
│                         │                                │
│  ┌──────────────────────▼──────────────────────────────┐ │
│  │              External Services                       │ │
│  │  MySQL │ Redis │ QQ SMTP │ 12306 │ RSS Feeds        │ │
│  └──────────────────────────────────────────────────────┘ │
└──────────────────────────────────────────────────────────┘
```



### 3\.1 Architecture Design Rationale



**Layered Design**: The system is divided into Router → Agent → Tool → External layers, each with a single responsibility\. Router handles request dispatching, Agent handles intent understanding and task orchestration, Tool handles atomic operations, and the External layer abstracts dependencies\. This layering facilitates independent testing, maintenance, and extensibility\.



## 4\. Module Breakdown



|Module|Directory|Responsibility|
|---|---|---|
|AI Agent Layer|`app/ai/agent/`|7 Agents, each encapsulating independent business logic|
|AI Model Layer|`app/ai/model/`|LLM singleton management|
|AI Schema|`app/ai/schema/`|Structured output definitions|
|AI Tool Layer|`app/ai/tool/`|6 custom Tools|
|API Router Layer|`app/api/`|HTTP endpoint definitions|
|Utilities|`app/utils/`|Logging, permissions, Redis, etc\.|



## 5\. Route Dispatch Mechanism



```Plain Text
/chat?question=xxx&user_id=yyy
    ├── question contains "图表" (chart)           → echarts_agent (non-streaming JSON)
    ├── question contains "数据分析" (analysis)    → analyze_agent (non-streaming JSON)
    ├── question contains "上传文件成功" (file)    → file_analyze_agent (streaming)
    ├── question contains keywords for news        → news_agent (streaming)
    ├── question contains keywords for trains      → train_agent (streaming)
    └── otherwise                                  → sql_question_agent (streaming)
```



**Design Rationale**: Uses keyword matching with priority ordering\. echarts and analyze agents return non\-streaming JSON for direct chart rendering; other agents use SSE streaming to enhance UX in long\-response scenarios\.



## 6\. Data Flow



### 6\.1 Streaming Request Data Flow



```Plain Text
User inputs question → Frontend sends GET /chat
    → Chat Router keyword matching
    → Agent.answer() method
    → before_agent middleware (permission check)
    → LLM intent recognition + tool call
    → Tool execution (database query / RSS / 12306)
    → LLM formats results
    → SSE streaming response → Frontend progressive rendering
```



### 6\.2 Non\-Streaming Request Data Flow \(ECharts / Analyze\)



```Plain Text
User inputs question → Frontend sends GET /chat
    → Chat Router keyword matching
    → Agent.answer() method (synchronous)
    → LLM intent recognition + tool call
    → Tool execution
    → LLM generates structured response (Pydantic Schema)
    → Backend returns complete JSON → Frontend renders at once
```



## 7\. Permission Control System



The system implements a three\-layer permission defense architecture\. All SQL\-related agents pass through the following three layers:



|Layer|Component|Responsibility|
|---|---|---|
|1st|`before_agent_middleware`|User authentication \+ role validation, rejects unauthorized roles|
|2nd|`sql_permission_prompt`|Dynamically generates system prompt exposing only permitted table schemas|
|3rd|`mysql_tool` \(tool layer\)|SQL parsing \+ table name extraction \+ cross\-table access protection|



**Risk Analysis**:



|Risk|Impact|Mitigation|
|---|---|---|
|SQL injection|Database security|Parameterized queries for permission lookup; tool\-layer SQL validation|
|Unauthorized data access|Data confidentiality|Three\-layer validation ensures users can only query authorized tables|
|LLM hallucination generating SQL|Query accuracy|Tool\-layer re\-validates table names and SQL types|



## 8\. Deployment Structure



```Plain Text
xnsy_agent/
├── main.py               # Application entry point
├── .env                  # Environment configuration
├── app/
│   ├── ai/               # AI layer
│   ├── api/              # API layer
│   ├── utils/            # Utilities
│   └── static/           # Static files
│       ├── upload/       # Uploaded files
│       └── download/     # Downloaded files
└── docs/                 # Technical documentation
```



## 9\. Design Decision Log



|Decision|Chosen|Alternative|Rationale|
|---|---|---|---|
|Agent registration|`app.state` injection|DI framework|Simple and direct, natively supported by FastAPI, fixed number of agents|
|Permission data source|Query database each time|Redis cache|Role changes take effect immediately, avoids cache consistency issues|
|Non\-streaming Agents|Return JSON directly|Unified SSE|ECharts/Analyze need complete data for one\-shot chart rendering; streaming adds no value|
|Code storage|Redis|Database|Redis natively supports TTL expiration, no manual cleanup needed|

# System Agent \(Login/Registration Code\) Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|Response Format|Pydantic `EmailResponse` structured output|
|Execution Mode|Synchronous `agent.invoke()`|



## 2\. Agent Function Description



- Determine verification code type \(login / registration\) based on user input

- Query `user_info` table to verify email registration status

- Generate 4\-digit numeric verification code

- Send HTML\-formatted verification code email via SMTP

- Return structured `EmailResponse` \(code, status, message\)

    

## 3\. Agent Workflow



```Plain Text
User requests verification code
    ↓
Frontend POST /send_code or /send_register_code
    ↓
SystemRouter calls SystemAgent.answer("send login/register code to xxx")
    ↓
SystemAgent receives message, LLM parses intent
    ↓
[Login code scenario]
    ├── Call mysql_tool to check if email exists
    │   ├── Not found → return code=500, data=0, msg="Email not registered"
    │   └── Found → generate 4-digit code → call send_email → return code=200
    │
[Registration code scenario]
    ├── Call mysql_tool to check if email is registered
    │   ├── Registered → return code=500, data=0, msg="Email already registered"
    │   └── Not registered → generate 4-digit code → call send_email → return code=200
    ↓
Router receives Agent response
    ├── code=200 → store code in Redis (TTL=60s)
    └── code=500 → return error directly
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`mysql_tool`|Execute SELECT query to verify email|`sql: str` \(SELECT statement\)|JSON string \(result or error\)|
|`send_email`|Send HTML\-formatted verification code email|`to: str`, `subject: str`, `content: str`|`"success"` or `"failed to send: {error}"`|



## 5\. Agent Input and Output Format



### 5\.1 Input Format



```Plain Text
GET /send_code     Body: {"email": "user@example.com"}
GET /send_register_code  Body: {"email": "user@example.com"}
```



Agent internal prompt format:



```Plain Text
Send login verification code to email user@example.com
```



### 5\.2 Output Format



Agent outputs structured `EmailResponse`:



```JSON
{
  "data": "1234",
  "code": "200",
  "msg": "Sent successfully"
}
```



|Field|Type|Description|
|---|---|---|
|`data`|string|4\-digit code, `"0"` on failure|
|`code`|string|Status code, `"200"` for success, `"500"` for failure|
|`msg`|string|Prompt message|



### 5\.3 API Return Value



```JSON
{
  "code": 200,
  "msg": "Sent successfully"
}
```



## 6\. Agent Security Restrictions



|Restriction|Description|
|---|---|
|SQL type limit|SELECT only, INSERT/UPDATE/DELETE/DROP prohibited|
|Query template|Fixed: `SELECT id,email FROM user_info WHERE email='...' LIMIT 1`|
|Code TTL|Redis TTL 60 seconds|
|Code length|4\-digit numeric|
|Email content|Contains verification code only, no other user information|
|Failure protection|Router does not write code to Redis when Agent returns code≠200|



## 7\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|Redis unavailable|Code storage failure|`system_router.py:24` hardcoded Redis connection `127.0.0.1:6379`; recommended to make configurable with connection pool|
|Email send failure|User cannot receive code|send\_email returns failure info, Agent returns code=500, Router does not store code|
|SQL injection|Database security|Parameterized queries in `permission_role.py`; fixed SQL template in Agent|
|Brute force code cracking|Account security|TTL 60s reduces exposure window; retry limit recommended \(to be confirmed\)|

# SQL Question Agent Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|Middleware|`before_agent_middleware` \+ `sql_permission_prompt` \(dynamic prompt\)|
|State Management|LangGraph StateGraph \+ InMemorySaver Checkpoint|
|Execution Mode|Async streaming `agent.astream(stream_mode="messages")`|



## 2\. Agent Function Description



- Convert natural language questions into SQL queries

- Dynamically restrict queryable tables and columns based on user role

- Execute read\-only database queries and return JSON results

- Multi\-layer security interception \(Middleware → Prompt → Tool\)

- Stream LLM responses via SSE

    

## 3\. Agent Workflow



```Plain Text
User inputs question → GET /chat?question=xxx&user_id=yyy
    ↓
Chat Router dispatches to SqlQuestionAgent
    ↓
Agent.answer() → create_question() → astream(stream_mode="messages")
    ↓
before_agent_middleware executes:
    ├── 1. Extract user_id
    ├── 2. Query user_info table for user role
    ├── 3. Load role permission config
    ├── 4. No permission → raise "Insufficient permissions"
    └── 5. Valid role → inject permissions into Graph State
    ↓
model_node executes:
    ├── sql_permission_prompt dynamically generates system prompt
    │   └── Only includes table schemas allowed for the role
    ├── LLM parses user intent, decides whether to call tool
    ├── [If query needed] Generate SQL → call mysql_tool
    │       ├── Syntax validation (SELECT only)
    │       ├── Dangerous keyword blocking
    │       ├── Table permission check
    │       └── Execute query (max 1000 rows)
    └── LLM formats results into natural language response
    ↓
SSE streaming response → Frontend progressive rendering
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`mysql_tool`|Execute MySQL SELECT query|`sql: str`|JSON string \(result array or error\)|



## 5\. Agent Input and Output Format



### 5\.1 Request Format



|Parameter|Type|Required|Description|
|---|---|---|---|
|`question`|string|Yes|User's natural language question|
|`user_id`|string|Yes|User email for identity and permission lookup|



### 5\.2 Response Format \(SSE Stream\)



Normal response:

```Plain Text
data:{"content": {"text": "Query result: ", "done": false}}
data:{"content": {"text": "[{\"product_name\": \"iPhone 15 Pro\", \"price\": 7999}]", "done": false}}
data:{"content": "", "done": true}
```



Permission denied response:

```Plain Text
data:{"content": "Insufficient permissions, current role: Regular Employee", "done": true, "error": true}
```



## 6\. Three\-Layer Permission Defense



|Layer|Component|Location|Responsibility|
|---|---|---|---|
|1st|`before_agent_middleware`|`permission_middle.py:48`|Authentication \+ role validation; denies unauthorized roles|
|2nd|`sql_permission_prompt`|`permission_middle.py:111`|Dynamically generates system prompt with only permitted table schemas|
|3rd|`mysql_tool` \(table validation\)|`mysql_tool.py:64`|SQL parsing \+ table name extraction \+ cross\-table access protection|



### 6\.1 Role Permission Matrix



|Role|Allowed Tables|Forbidden Columns|Max Rows|
|---|---|---|---|
|General Manager|All \(`*`\)|None|10000|
|Department Manager|sales, customer, products, orders, customer\_behavior|None|5000|
|Sales Manager|sales, customer, products|None|1000|
|Finance|sales, orders|None|5000|
|Sales|sales, customer, products|None|1000|
|Operations|customer\_behavior, products, customer|customer\.salary|1000|
|Regular Employee|None|—|0|



### 6\.2 Table Definitions



|Table|Columns|Description|
|---|---|---|
|`user_info`|id, user\_name, email, role, age, country, salary|User information|
|`customer`|user\_id, username, registration\_date, country, age, gender, total\_spent, order\_count|Customer table|
|`products`|product\_id, product\_name, category, price, stock, sales\_volume, average\_rating|Product table|
|`orders`|order\_id, user\_id, order\_date, product\_id, quantity, total\_amount, payment\_method, order\_status|Order table|
|`customer_behavior`|id, user\_id, product\_id, action, action\_date, device|Customer behavior table|
|`sales`|id, year, total\_sales, total\_orders, total\_quantity\_sold, category, average\_order\_value|Sales statistics table|



## 7\. Agent Security Restrictions



### 7\.1 SQL Validation Rules



|Rule|Description|
|---|---|
|SELECT only|Parse first SQL token, reject non\-SELECT|
|Dangerous keyword blocking|INTO OUTFILE, LOAD\_FILE\(\), BENCHMARK\(\), SLEEP\(\), EXEC, XP\_CMDSHELL|
|Connection timeout|10s|
|Query timeout|30s|
|Max rows|1000 rows per query|
|Table validation|Extract all table names from SQL, compare with role permissions|



### 7\.2 Error Messages



|Error Scenario|Returned Message|
|---|---|
|User not found|`User does not exist, please login again`|
|Insufficient permissions|`Insufficient permissions, current role: {role}`|
|Illegal SQL|`Security validation failed: only SELECT queries allowed...`|
|Unauthorized table|`Insufficient permissions: cannot query table {table_name}`|
|Dangerous keyword|`SQL contains dangerous operation: {pattern}`|
|Database exception|`Database error: {error_detail}`|



## 8\. Design Decisions



|Decision|Chosen|Alternative|Rationale|
|---|---|---|---|
|Permission data source|Query database each time|Redis cache|Real\-time role changes, avoids cache consistency issues|
|Dynamic prompt|`@dynamic_prompt` middleware|Hardcoded prompt|Dynamically injects table structures per role; LLM cannot generate unauthorized SQL|
|State injection|`InjectedState` annotation|Global variables|LangGraph recommended approach, type\-safe, no side effects|
|Streaming output|`stream_mode="messages"`|Non\-streaming|Better UX for long query scenarios|



## 9\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|LLM generates malicious SQL|Database security|Tool\-layer re\-validation; SELECT only \+ dangerous keyword blocking \+ table whitelist|
|Prompt injection|Agent behavior anomaly|Dynamic prompt only injects table schemas, not user input; user question via message|
|Data leakage|Data confidentiality|Three\-layer permission check; forbidden columns removed at Prompt level|
|Timeout|User experience|10s connect \+ 30s query timeout; streaming response prevents frontend hanging|

# ECharts Agent Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|Response Format|Pydantic `EchartsResponse` structured output|
|State Management|LangGraph StateGraph \+ InMemorySaver Checkpoint|
|Execution Mode|Synchronous `agent.invoke()`|



**Design Rationale**: ECharts Agent uses synchronous non\-streaming execution because the frontend needs the complete ECharts JSON configuration for one\-shot chart rendering; streaming adds no value for chart rendering\.



## 2\. Agent Function Description



- Generate database query SQL based on user questions

- Execute SELECT queries to retrieve data

- Convert query results into ECharts\-compatible JSON configuration

- Return structured `EchartsResponse` \(JSON data, status code, message\)

- Support common chart types: bar, line, pie, scatter \(dependent on LLM capability\)

    

## 3\. Agent Workflow



```Plain Text
User inputs question → "Query top 5 products by sales and generate bar chart"
    ↓
Chat Router matches "chart" keyword → routes to echarts_agent
    ↓
echarts_agent.answer(question, user_id) synchronous execution
    ↓
LLM parses intent: need to query database and generate chart
    ↓
Call mysql_tool to execute SQL query
    ├── SELECT product_name, sales_volume FROM products ORDER BY sales_volume DESC LIMIT 5
    └── Return JSON result
    ↓
LLM generates ECharts JSON configuration from query results
    ↓
Return structured EchartsResponse
    ↓
Router directly returns JSON to frontend
    ↓
Frontend renders chart using ECharts library
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`mysql_tool`|Execute MySQL SELECT query for chart data|`sql: str`|JSON string \(result array or error\)|



## 5\. Agent Input and Output Format



### 5\.1 Request Format



```Plain Text
GET /chat?question=Query top 5 products by sales and generate bar chart&user_id=xxx@example.com
```



|Parameter|Type|Required|Description|
|---|---|---|---|
|`question`|string|Yes|Natural language question \(must contain "chart" keyword\)|
|`user_id`|string|Yes|User identifier|



### 5\.2 Output Format



Agent outputs structured `EchartsResponse`:



```JSON
{
  "data": "{\"title\":{\"text\":\"Top 5 Products by Sales\"},\"xAxis\":{\"type\":\"category\",\"data\":[\"Product A\",\"Product B\",\"Product C\"]},\"yAxis\":{\"type\":\"value\"},\"series\":[{\"type\":\"bar\",\"data\":[120,98,86]}]}",
  "code": 200,
  "msg": "Generated successfully"
}
```



|Field|Type|Description|
|---|---|---|
|`data`|string|ECharts JSON configuration string \(needs `JSON.parse` on frontend\)|
|`code`|int|Status code, 200 for success, 500 for failure|
|`msg`|string|Prompt message|



### 5\.3 HTTP Response



```JSON
{
  "data": "{...ECharts JSON...}",
  "code": 200,
  "msg": "Generated successfully"
}
```



## 6\. Agent Security Restrictions



|Restriction|Description|
|---|---|
|SQL type|SELECT only|
|Result limit|Only query top 5 records \(fixed in Prompt\)|
|TOP queries|Must use ORDER BY \+ LIMIT|
|Multi\-table queries|Must use JOIN|



## 7\. Design Decisions



|Decision|Chosen|Alternative|Rationale|
|---|---|---|---|
|Execution mode|Synchronous non\-streaming|Async streaming|Frontend needs complete JSON for one\-shot chart rendering|
|Output format|ECharts JSON string|Frontend assembly|Agent directly generates ECharts\-compatible config, reducing frontend logic|
|Result limit|5 records|Unlimited|Charts should highlight key data; excessive data impacts rendering performance|



## 8\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|ECharts JSON format error|Frontend rendering failure|Return code=500 with error info; frontend validation recommended \(to be confirmed\)|
|Chart type vs data mismatch|Poor visualization|LLM automatically selects chart type based on data characteristics; manual optimization needed \(to be confirmed\)|
|Excessive data volume|Response latency|Prompt limits queries to 5 records|

# Analyze Agent Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|Response Format|Pydantic `AnalyzeResponse` structured output \(table \+ analysis text \+ chart JSON\)|
|State Management|LangGraph StateGraph \+ InMemorySaver Checkpoint|
|Execution Mode|Synchronous `agent.invoke()`|



**Design Rationale**: Analyze Agent uses synchronous non\-streaming execution because the frontend needs the complete analysis result \(table \+ analysis text \+ chart\) for one\-shot rendering\.



## 2\. Agent Function Description



- Generate SQL queries based on user questions to retrieve data

- Organize query results into table format

- Perform multi\-dimensional analysis on data, generating structured analysis reports

- Automatically generate ECharts chart configuration based on data characteristics

- Return structured `AnalyzeResponse` containing table data, analysis text, and chart JSON

    

## 3\. Agent Workflow



```Plain Text
User inputs question → "Data analysis: sales by category in 2024"
    ↓
Chat Router matches "data analysis" keyword → routes to analyze_agent
    ↓
analyze_agent.answer(question, user_id) synchronous execution
    ↓
[Step 1] LLM generates SQL → calls mysql_tool to query database
    ├── SELECT category, total_sales FROM sales WHERE year=2024
    └── Retrieve raw data
    ↓
[Step 2] LLM analyzes data, generates analysis report
    ├── I. Detailed Analysis
    ├── 1. Category sales: ...
    ├── 2. Growth trend: ...
    └── II. Conclusion: ...
    ↓
[Step 3] LLM generates ECharts JSON configuration from data
    ↓
Assemble AnalyzeResponse (table + result + json) → return to frontend
    ↓
Frontend renders table + analysis text + chart
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`mysql_tool`|Execute MySQL SELECT query for analysis data|`sql: str`|JSON string \(result array or error\)|



## 5\. Agent Input and Output Format



### 5\.1 Request Format



```Plain Text
GET /chat?question=Data analysis: sales by category in 2024&user_id=xxx@example.com
```



|Parameter|Type|Required|Description|
|---|---|---|---|
|`question`|string|Yes|Natural language question \(must contain "data analysis" keyword\)|
|`user_id`|string|Yes|User identifier|



### 5\.2 Output Format



Agent outputs structured `AnalyzeResponse`:



```JSON
{
  "table": {
    "column_name": ["category", "total_sales"],
    "data": [
      {"category": "Electronics", "total_sales": "1200000"},
      {"category": "Clothing", "total_sales": "800000"}
    ]
  },
  "result": "I. Detailed Analysis\n1. Category sales:\n   Electronics had the highest sales at 1,200,000...\nII. Conclusion:\n   Overall sales performance in 2024 is positive...",
  "json": "{\"title\":{\"text\":\"2024 Sales by Category\"},\"xAxis\":{\"type\":\"category\",\"data\":[\"Electronics\",\"Clothing\"]},\"series\":[{\"type\":\"bar\",\"data\":[1200000,800000]}]}"
}
```



|Field|Type|Description|
|---|---|---|
|`table`|TableResponse|Table data \(column\_name \+ data\)|
|`result`|string|Structured analysis report text|
|`json`|string|ECharts JSON configuration string|



### TableResponse Structure



|Field|Type|Description|
|---|---|---|
|`column_name`|list|Column header list|
|`data`|list\[dict\[str,str\]\]|Data rows as field\-value mappings|



## 6\. Agent Security Restrictions



|Restriction|Description|
|---|---|
|SQL type|SELECT only|
|Result limit|Only query top 5 records|
|TOP queries|Must use ORDER BY \+ LIMIT|
|Multi\-table queries|Must use JOIN|



## 7\. Design Decisions



|Decision|Chosen|Alternative|Rationale|
|---|---|---|---|
|Three\-step workflow|Single Agent call completes query\+analysis\+chart|Multiple Agent calls|Single call reduces latency; LLM generates more consistent analysis and charts with complete data|
|Execution mode|Synchronous|Async streaming|Frontend needs complete response for rendering; streaming adds no value|
|Analysis format|Fixed template \(I\. Detailed / II\. Conclusion\)|Free format|Ensures output consistency, enables unified frontend rendering|



## 8\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|Inaccurate LLM analysis|Analysis deviates from facts|Prompt requires data\-based analysis; manual review recommended \(to be confirmed\)|
|ECharts JSON format error|Chart rendering failure|Return code=500; frontend falls back to table display|
|SQL query failure|Analysis flow interrupted|Agent returns error, code=500|

# File Analyze Agent Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|Execution Mode|Async streaming `agent.astream(stream_mode="messages")`|
|Prompt Template|LangChain `PromptTemplate` dynamic file path injection|



## 2\. Agent Function Description



- Read table data from uploaded docx files

- Automatically detect missing values \(fill with "None"\) and duplicate data \(remove\)

- Write cleaned data to a new docx file

- Return analysis report and download link

- Stream processing progress via SSE

    

## 3\. Agent Workflow



```Plain Text
User uploads file → POST /upload → file saved to static/upload/
    ↓
User inputs question → "File uploaded successfully:filename.docx"
    ↓
Chat Router matches "uploaded successfully" → routes to file_analyze_agent
    ↓
file_analyze_agent.answer() parses filename
    ├── Extract filename after ":"
    ├── Build full path: static/upload/filename.docx
    └── Check file existence
    ↓
[Step 1] Call docx_read_tool to read docx table data
    ├── Iterate all tables in document
    └── Read each cell text, organize as 2D array
    ↓
[Step 2] LLM analyzes data quality
    ├── Detect missing values → fill "None"
    ├── Detect duplicate rows → remove
    └── Generate cleaning description
    ↓
[Step 3] Call docx_write_tool to write new document
    ├── Generate timestamp-named docx file
    ├── Save to static/download/
    └── Return download link http://localhost:8000/static/download/{timestamp}.docx
    ↓
SSE streaming returns analysis results → Frontend renders
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`docx_read_tool`|Read all table data from docx file|`path: str` \(file path\)|2D array string \(rows × columns text\)|
|`docx_write_tool`|Write analysis results to new docx file|`content: str` \(content to write\)|Download link string|



## 5\. Agent Input and Output Format



### 5\.1 Upload Interface



```Plain Text
POST /upload
Content-Type: multipart/form-data
Body: file=@file.docx
```



Response:

```JSON
{
  "code": 200,
  "file_name": "file.docx",
  "msg": "Upload successful"
}
```



### 5\.2 Analysis Request



```Plain Text
GET /chat?question=File uploaded successfully:file.docx&user_id=xxx
```



|Parameter|Type|Required|Description|
|---|---|---|---|
|`question`|string|Yes|Must contain "File uploaded successfully:" \+ filename|
|`user_id`|string|Yes|User identifier|



### 5\.3 Response Format \(SSE Stream\)



```Plain Text
data:{"content": {"text": "Starting file analysis...", "done": false}}
data:{"content": {"text": "Read 10 rows, found 2 missing values, 1 duplicate row", "done": false}}
data:{"content": {"text": "Download: http://localhost:8000/static/download/20260608120000.docx", "done": false}}
data:{"content": "", "done": true}
```



## 6\. Agent Security Restrictions



|Restriction|Description|
|---|---|
|File format|`.docx` only|
|File size|No explicit limit \(to be confirmed\)|
|Path security|Uses `Path(file_name).name` to prevent path traversal|
|File existence|Checks before reading|
|Download path|Fixed to `http://localhost:8000/static/download/`|



## 7\. Design Decisions



|Decision|Chosen|Alternative|Rationale|
|---|---|---|---|
|File path passing|Via question string with `:` separator|Separate API parameter|Reuses Chat routing mechanism, no new endpoint needed|
|Filename sanitization|`Path(file_name).name`|Direct concatenation|Prevents path traversal \(e\.g\., `../../etc/passwd`\)|
|Output filename|Timestamp|Original filename|Prevents filename conflicts|



## 8\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|Path traversal attack|Server file security|Uses `Path(file_name).name` to extract pure filename|
|Large file processing timeout|User experience|No explicit timeout or size limit \(to be confirmed\)|
|Unsupported docx format|Read failure|Tool layer catches exceptions and returns error|
|Hardcoded download URL|Deployment environment changes|URL hardcoded to `localhost:8000`; needs configuration for production \(to be confirmed\)|

# News Agent \(Hot News Retrieval\) Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|Middleware|`news_before_agent_middleware` \(news permission validation\)|
|State Management|LangGraph StateGraph \+ InMemorySaver Checkpoint|
|RSS Parsing|`feedparser` \+ `requests` \(8s timeout per source\)|
|Execution Mode|Async streaming `agent.astream(stream_mode="messages")` \+ stream filtering|



## 2\. Agent Function Description



- Aggregate AI\-related news from 10 RSS sources \(Chinese \+ English\)

- Support filtering by AI sub\-domain: Large Models/LLM, AI Applications, AI Research

- Support custom keyword search

- Role\-based access control \(`can_access_news` permission flag\)

- Stream output filtering: skip LLM thinking and tool call internals, return final Markdown only

- Graceful degradation: unreachable RSS sources logged as warnings and skipped

    

## 3\. Agent Workflow



```Plain Text
User inputs question → "What's new in AI today?"
    ↓
Chat Router matches keywords → news_agent
    ↓
Agent.answer() → create_question() → astream(stream_mode="messages")
    ↓
news_before_agent_middleware executes:
    ├── 1. Extract user_id
    ├── 2. Query user_info table for role
    ├── 3. Check can_access_news permission
    └── 4. No access → raise "Insufficient permissions"
    ↓
LLM parses intent → decides category/keyword parameters
    ├── "Latest LLM news" → news_tool(category="大模型/LLM")
    ├── "Search for GPT news" → news_tool(keywords="GPT")
    └── "What's new in AI" → news_tool()
    ↓
news_tool executes:
    ├── Iterate all enabled RSS sources (parallel with 8s timeout)
    ├── feedparser parses XML → extract title/summary/link/date
    ├── Sort by date, deduplicate by URL
    ├── Filter by category/keywords
    └── Return JSON array (default 20 items)
    ↓
LLM formats result as Markdown text
    ↓
Stream output filter → skip thinking tokens and tool calls, output final answer only
    ↓
SSE response → Frontend renders
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`news_tool`|Aggregate AI news retrieval|`category: str` \(optional\), `keywords: str` \(optional\), `limit: int` \(default 20\)|JSON string array: `[{title, summary, url, source, language, published}]`|



## 5\. Agent Input and Output Format



### 5\.1 Request Format



```Plain Text
GET /chat?question=What's new in large language models&user_id=xxx@example.com
```



|Parameter|Type|Required|Description|
|---|---|---|---|
|`question`|string|Yes|Natural language question \(must contain news\-related keywords\)|
|`user_id`|string|Yes|User identifier|



### 5\.2 Response Format \(SSE Stream\)



```Plain Text
data:{"content": {"text": "Found 20 AI-related news from QbitAI, TechCrunch AI, 36Kr and more:", "done": false}}
data:{"content": {"text": "\n\n🌐 **[OpenAI Releases GPT-5](https://...)** | TechCrunch · 2026-06-05\n> OpenAI announces GPT-5 with breakthrough reasoning capabilities...", "done": false}}
data:{"content": "", "done": true}
```



Error response:

```Plain Text
data:{"content": "Insufficient permissions, current role: Regular Employee", "done": true, "error": true}
```



### 5\.3 Markdown Format Specification



```Plain Text
Found N related articles from sources X, Y, Z

📰 **[Chinese Title](link)** | Source · Date
> Summary (first 80 chars)

---

🌐 **[English Title](link)** | Source · Date
> Summary (first 80 chars)
```



- Chinese articles: 📰 prefix

- English articles: 🌐 prefix

- Sources separated by: `---`

    

## 6\. RSS Source Configuration



|Source|Language|Status|
|---|---|---|
|机器之心|zh|Enabled|
|36氪|zh|Enabled|
|InfoQ|zh|Enabled|
|量子位|zh|Enabled|
|TechCrunch AI|en|Enabled|
|MIT Tech Review AI|en|Enabled|
|ArXiv cs\.AI|en|Enabled|
|Google AI Blog|en|Enabled \(unreachable from China\)|
|Hugging Face Blog|en|Enabled \(unreachable from China\)|
|OpenAI Blog|en|Enabled|



**Design Rationale**: Each source can be individually controlled via `enabled: bool`; unreachable sources are logged as warnings without affecting other sources\.



## 7\. Category Keywords



|Category|Example Keywords|
|---|---|
|Large Models/LLM|GPT, LLM, Claude, Llama, transformer, multimodal, Sora, Gemini, DeepSeek, Qwen|
|AI Applications|Agent, Copilot, RAG, fine\-tuning, LangChain, Cursor, Function Calling, API|
|AI Research|ArXiv, paper, benchmark, SOTA, dataset, RLHF, DPO, inference, alignment|



## 8\. Stream Output Filtering



**Problem**: LLM streaming has three phases—thinking tokens, tool calls, final answer\. The first two should not be shown to users\.



**Solution**:



```Plain Text
seen_tool = False
buffer = ""
async for chunk, metadata in response:
    if hasattr(chunk, "tool_call_id"):
        seen_tool = True          # Skip tool call
        continue
    if seen_tool:
        if chunk.content:
            yield chunk.content    # Output final answer only
    else:
        buffer += (chunk.content or "")  # Cache thinking, discard
if not seen_tool and buffer:
    yield buffer                   # Flush buffer when no tool was called
```



## 9\. Permission Control



|Role|can\_access\_news|
|---|---|
|General Manager|True|
|Department Manager|True|
|Sales Manager|True|
|Finance|True|
|Sales|True|
|Operations|True|
|Regular Employee|False|



## 10\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|RSS source unreachable|Partial data loss|Log warning, skip, remaining sources return normally|
|RSS format change|Parse failure|`feedparser` tolerant parsing; log exceptions|
|Network timeout|Overall response latency|8s timeout per source; parallel batch requests|
|Content duplication|Poor UX|URL deduplication \+ date sorting|
|China\-inaccessible sources|English sources unavailable|Sources can be individually disabled; Google AI Blog and HF Blog confirmed unreachable|

# Train Agent \(Train Ticket Query\) Technical Documentation



## 1\. Agent Technology Selection



|Component|Choice|
|---|---|
|Agent Framework|LangChain `create_agent` \(based on LangGraph StateGraph\)|
|LLM|Qwen3\-Max \(Alibaba Cloud DashScope, compatible OpenAI API\)|
|State Management|LangGraph StateGraph \+ InMemorySaver Checkpoint|
|Data Source|China Railway 12306 public API|
|Station Data|12306 `station_name.js` \(\~3000 stations\)|
|Execution Mode|Async streaming `agent.astream(stream_mode="messages")` \+ stream filtering|



## 2\. Agent Function Description



- Extract departure station, arrival station, and date from user questions

- Support fuzzy station name matching

- Support multiple date format parsing \(today/tomorrow/YYYY\-MM\-DD/Chinese formats\)

- Query 12306 API for train schedules and ticket availability

- Format output as Markdown table

- Filter seat availability to show only available seats

    

## 3\. Agent Workflow



```Plain Text
User inputs question → "High-speed train from Beijing to Shanghai tomorrow"
    ↓
Chat Router matches train keywords → train_agent
    ↓
Agent.answer() → create_question() → astream(stream_mode="messages")
    ↓
LLM parses intent, extracts parameters:
    ├── from_station = "Beijing"
    ├── to_station = "Shanghai"
    └── date = "tomorrow" → resolves to "2026-06-09"
    ↓
Call train_tool(from_station="Beijing", to_station="Shanghai", date="2026-06-09")
    ├── 1. Load 12306 station data (station_name.js)
    │       └── ~3000 stations, name → code mapping
    ├── 2. Match station codes
    ├── 3. Send HTTP request to 12306 API
    ├── 4. Parse response data
    │       ├── Extract train number, stations, times, duration
    │       └── Parse seat availability
    └── 5. Return JSON result
    ↓
LLM formats as Markdown table
    ↓
Stream filtering (same mechanism as News Agent)
    ↓
SSE response → Frontend renders
```



## 4\. Agent Tool Instructions



|Tool Name|Function|Input Parameters|Output|
|---|---|---|---|
|`train_tool`|Query 12306 train ticket info|`from_station: str`, `to_station: str`, `date: str`|JSON string \(train array or error\)|



## 5\. Agent Input and Output Format



### 5\.1 Request Format



```Plain Text
GET /chat?question=High-speed train from Beijing to Shanghai tomorrow&user_id=xxx
```



### 5\.2 Response Format \(SSE Stream\)



```Plain Text
data:{"content": {"text": "#### 🚄 Beijing → Shanghai (2026-06-09)\n> Found 15 trains\n\n| Train | From | To | Depart | Arrive | Duration | Seats |\n|-------|------|-----|--------|--------|----------|-------|\n| G101 | Beijing S | Shanghai H | 07:00 | 12:30 | 5h30m | 2nd Class:Available 1st Class:3 left |\n...", "done": false}}
data:{"content": "", "done": true}
```



### 5\.3 Tool Return JSON Format



```JSON
{
  "from_station": "Beijing",
  "to_station": "Shanghai",
  "date": "2026-06-09",
  "total": 15,
  "trains": [
    {
      "train_no": "G101",
      "from_station": "Beijing South",
      "to_station": "Shanghai Hongqiao",
      "depart_time": "07:00",
      "arrive_time": "12:30",
      "duration": "5h30m",
      "seats": {
        "Second Class": "Available",
        "First Class": "3 left"
      }
    }
  ]
}
```



## 6\. Station Matching Mechanism



|Method|Example|Description|
|---|---|---|
|Exact match|"Beijing" → "BJP"|Direct station name hit|
|Contains match|"Beijing" in "Beijing South"|Input name within station name|
|Prefix match|"Bei" → "Beijing" startswith "Bei"|Station name starts with input|
|No match|Returns empty `""`|Prompt user station not found|



**Station Data Source**: 12306 official `station_name.js`, \~3000 stations, cached for 1 hour\.



## 7\. Date Parsing Rules



|Input Format|Example|Result|
|---|---|---|
|ISO date|`2024-01-01`|`2024-01-01`|
|Relative|`today`, `tomorrow`|Current/next day|
|Chinese YMD|`2024年1月1日`|`2024-01-01`|
|Chinese MD|`1月1日`|`{current_year}-01-01`|
|Default|Empty/unrecognized|Current date|



## 8\. Design Decisions



|Decision|Chosen|Alternative|Rationale|
|---|---|---|---|
|Station data|Real\-time from 12306|Hardcoded|Station data updates; 1h cache balances performance|
|Station matching|3\-level fuzzy \(exact→contains→prefix\)|Exact only|User input may be incomplete|
|Date parsing|Built\-in Chinese date parser|LLM parsing|Deterministic operation by code, reduces LLM overhead|
|Seat display|Show only available seats|Show all|Reduces noise, improves readability|



## 9\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|12306 API changes|All queries fail|Tool layer catches exceptions, returns clear error|
|12306 anti\-scraping|Requests blocked|Browser User\-Agent/Referer simulation; Cookie acquisition|
|Fuzzy match inaccuracy|Wrong query|Clear error messages; multi\-level matching|
|Network timeout|Query failure|10s connect \+ 15s overall timeout|
|Station data parse failure|Code mapping broken|Exception thrown, Agent returns error message|

# API Reference



## 1\. Overview



### 1\.1 Base Information



|Item|Description|
|---|---|
|Base URL|`http://localhost:8000`|
|Protocol|HTTP / SSE|
|Data Format|JSON / Server\-Sent Events|
|Encoding|UTF\-8|
|CORS Allowed Origin|`http://localhost:8081`|



### 1\.2 API Categories



|Category|Route Prefix|Description|
|---|---|---|
|System|`/`|User authentication, login, registration|
|Chat|`/chat`<br>|Agent conversation, file upload|



---



## 2\. System Endpoints



### 2\.1 Send Login Code



|Item|Description|
|---|---|
|Name|`send_code`|
|Method|POST|
|Path|`/send_code`|



**Request**:



|Parameter|Type|Required|Description|
|---|---|---|---|
|`email`|string|Yes|User email|



**Response**:



|Parameter|Type|Description|
|---|---|---|
|`code`|int<br>|Status, 200 success, 500 failure|
|`msg`|string|Message|



**Error Codes**:



|Code|Description|
|---|---|
|200|Code sent successfully|
|500|Email not registered / send error|



---



### 2\.2 Login



|Item|Description|
|---|---|
|Name|`login`|
|Method|POST|
|Path|`/login`|



**Request**:



|Parameter|Type|Required|Description|
|---|---|---|---|
|`email`|string|Yes|User email|
|`code`|string|Yes|Verification code|



**Error Codes**:



|Code|Description|
|---|---|
|200|Login successful|
|500|Invalid/expired code / login error|



---



### 2\.3 Send Registration Code



|Item|Description|
|---|---|
|Name|`send_register_code`|
|Method|POST|
|Path|`/send_register_code`|



**Error Codes**:



|Code|Description|
|---|---|
|200|Code sent successfully|
|500|Email already registered / send error|



---



### 2\.4 Register



|Item|Description|
|---|---|
|Name|`register`|
|Method|POST|
|Path|`/register`|



**Request**:



|Parameter|Type|Required|Description|
|---|---|---|---|
|`email`|string|Yes|User email|
|`code`|string|Yes|Verification code|
|`user_name`|string|Yes|Username|



**Error Codes**:



|Code|Description|
|---|---|
|200|Registration successful|
|500|Invalid/expired code / email registered / registration error|



---



## 3\. Chat Endpoints



### 3\.1 Chat Conversation



|Item|Description|
|---|---|
|Name|`chat`|
|Method|GET|
|Path|`/chat`|
|Response Type|`text/event-stream` \(SSE\)|



**Request**:



|Parameter|Type|Required|Description|
|---|---|---|---|
|`question`|string|Yes|User question|
|`user_id`|string|Yes|User identifier \(email\)|



**SSE Response Format**:



|Event|Data Format|Description|
|---|---|---|
|Streaming|`data:{"content": {"text": "...", "done": false}}\n\n`|Content chunks|
|End|`data:{"content": "", "done": true}\n\n`|Response complete|
|Error|`data:{"content": "error", "done": true, "error": true}\n\n`|Error information|



**Route Dispatch Rules**:



|Condition|Target|Response|
|---|---|---|
|question contains "chart" keywords|echarts\_agent|Non\-streaming JSON|
|question contains "analysis" keywords|analyze\_agent|Non\-streaming JSON|
|question contains "file" keywords|file\_analyze\_agent|SSE stream|
|question contains news keywords|news\_agent|SSE stream|
|question contains train keywords|train\_agent|SSE stream|
|Other|sql\_question\_agent|SSE stream|



---



### 3\.2 File Upload



|Item|Description|
|---|---|
|Name|`upload`|
|Method|POST|
|Path|`/upload`|
|Content\-Type|`multipart/form-data`|



**Request**:



|Parameter|Type|Required|Description|
|---|---|---|---|
|`file`|UploadFile|Yes|File to upload \(docx only\)|



**Response**:



|Parameter|Type|Description|
|---|---|---|
|`code`|int|Status, 200 success|
|`file_name`|string|Saved filename|
|`msg`|string|Message|



---



## 4\. HTTP Status Codes



|Code|Meaning|Description|
|---|---|---|
|200|Success|Request processed successfully|
|500|Server Error|Server exception, see msg field for details|



**Design Rationale**: All system endpoints return HTTP 200, with business success/failure indicated by the `code` field in the response body\. This simplifies frontend response handling and avoids dependency on HTTP status codes\. The Chat endpoint returns SSE error events instead of HTTP error codes\.



## 5\. API Security



|Measure|Description|
|---|---|
|CORS restriction|Only `http://localhost:8081` allowed|
|User authentication|Via `user_id` \(email\) parameter|
|Permission validation|Agent middleware layer checks role and permissions|
|Code TTL|Redis auto\-expires after 60s|
|File upload|docx only \(size and type validation recommended\)|

# Deployment Guide



## 1\. Environment Requirements



### 1\.1 Hardware Requirements



|Resource|Minimum|Recommended|
|---|---|---|
|CPU|2 cores|4 cores|
|Memory|4 GB|8 GB|
|Disk|10 GB|20 GB|
|Network|Internet access for LLM API|Low\-latency network|



### 1\.2 Software Requirements



|Component|Version|Description|
|---|---|---|
|Python|\>= 3\.10|Uses async/await syntax|
|MySQL|\>= 8\.0|Business data storage|
|Redis|\>= 6\.0|Verification code caching|
|OS|Windows / Linux / macOS|Cross\-platform|



### 1\.3 Network Requirements



|Target|Requirement|Description|
|---|---|---|
|DashScope API|Internet|LLM calls|
|QQ SMTP|Internet|Email sending|
|12306|Internet|Train ticket queries|
|RSS feeds|Internet|News aggregation|
|Redis|Internal|Code caching|
|MySQL|Internal|Data storage|



---



## 2\. Configuration



### 2\.1 Environment Variables \(\.env\)



|Variable|Required|Description|Example|
|---|---|---|---|
|`MODEL_NAME`|Yes|LLM model name|`qwen3-max-2026-01-23`|
|`OPENAI_API_KEY`|Yes|DashScope API Key|`sk-xxx`|
|`OPENAI_API_BASE`|Yes|DashScope API URL|`https://dashscope.aliyuncs.com/compatible-mode/v1`|
|`EMAIL_HOST`|Yes|SMTP server|`smtp.qq.com`|
|`EMAIL_FROM`|Yes|Sender email|`xxx@qq.com`|
|`EMAIL_PASSWORD`|Yes|SMTP auth code|`xxxxx`|
|`SMTP_HOST`|Yes|SMTP server|`smtp.qq.com`|
|`MYSQL_HOST`|Yes|MySQL host|`localhost`|
|`MYSQL_USER`|Yes|MySQL user|`root`|
|`MYSQL_PORT`|Yes|MySQL port|`3306`|
|`MYSQL_PASSWORD`|Yes|MySQL password|`root`|
|`MYSQL_DATABASE`|Yes|MySQL database|`agent`|



**Redis Configuration** \(currently hardcoded, should be configurable\):



|Setting|Current Value|Description|
|---|---|---|
|Redis host|`127.0.0.1`|Hardcoded in `system_router.py:24`|
|Redis port|`6379`|Same|
|Redis DB|`0`|Same|



> **To be confirmed**: Redis configuration should be migrated to `.env` file\.
> 
> 



### 2\.2 Application Configuration \(main\.py\)



|Setting|Current Value|Description|
|---|---|---|
|Listen address|`localhost:8000`|`main.py:71`|
|CORS origin|`http://localhost:8081`|`main.py:53`|
|Static mount|`/static`|`main.py:67`|



### 2\.3 Permission Configuration \(permission\_config\.py\)



|Setting|Location|Description|
|---|---|---|
|Role permission matrix|`TABLE_PERMISSIONS`|Add/modify roles here|
|Table definitions|`ALL_TABLES`|Register new tables here|
|Dangerous keywords|`mysql_tool.py:16`|SQL blocking keywords list|
|Max rows|`mysql_tool.py:14`|`MAX_ROWS = 1000`|



---



## 3\. Deployment Steps



### 3\.1 Database Initialization



```SQL
CREATE DATABASE IF NOT EXISTS agent DEFAULT CHARACTER SET utf8mb4;

CREATE TABLE IF NOT EXISTS user_info (
    id INT AUTO_INCREMENT PRIMARY KEY,
    user_name VARCHAR(100),
    email VARCHAR(200) UNIQUE,
    role VARCHAR(50),
    age INT DEFAULT 0,
    country VARCHAR(100) DEFAULT 'China',
    salary DECIMAL(10,2) DEFAULT 0,
    INDEX idx_email (email),
    INDEX idx_role (role)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Insert test users
INSERT INTO user_info (user_name, email, role) VALUES
('General Manager', 'gm@example.com', '总经理'),
('Sales Manager', 'sm@example.com', '销售经理'),
('Employee', 'emp@example.com', '普通员工');
```



### 3\.2 Application Deployment



```Bash
# 1. Clone project
git clone <repository_url>
cd xnsy_agent

# 2. Create virtual environment
python -m venv venv
source venv/bin/activate  # Linux/Mac
venv\Scripts\activate     # Windows

# 3. Install dependencies
pip install fastapi uvicorn langchain langchain-openai langgraph openai
pip install pydantic pymysql python-dotenv redis
pip install requests feedparser python-docx

# 4. Configure environment
cp .env.example .env  # Edit .env with actual values

# 5. Create required directories
mkdir -p app/logs app/static/upload app/static/download

# 6. Start service
python main.py
```



### 3\.3 Docker Deployment \(Optional\)



```Dockerfile
# Dockerfile
FROM python:3.11-slim
WORKDIR /app
COPY . .
RUN pip install --no-cache-dir -r requirements.txt
EXPOSE 8000
CMD ["python", "main.py"]
```



```YAML
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8000:8000"
    env_file: .env
    depends_on:
      - mysql
      - redis
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: agent
  redis:
    image: redis:7
    ports:
      - "6379:6379"
```



---



## 4\. Startup Commands



### 4\.1 Development



```Bash
python main.py
# or
uvicorn main:app --host localhost --port 8000 --reload
```



### 4\.2 Production



```Bash
# With gunicorn + uvicorn workers (Linux)
gunicorn main:app -w 4 -k uvicorn.workers.UvicornWorker -b 0.0.0.0:8000

# With uvicorn multi-workers
uvicorn main:app --host 0.0.0.0 --port 8000 --workers 4
```



---



## 5\. Verification



### 5\.1 Health Check



```Bash
curl http://localhost:8000/docs
# Should return Swagger UI
```



### 5\.2 API Verification



```Bash
# Test send code
curl -X POST http://localhost:8000/send_code \
  -H "Content-Type: application/json" \
  -d '{"email": "gm@example.com"}'

# Test chat
curl "http://localhost:8000/chat?question=View all sales data&user_id=gm@example.com"

# Test file upload
curl -X POST http://localhost:8000/upload -F "file=@test.docx"
```



### 5\.3 Run Tests



```Bash
python test_permission.py
python test_all_features.py
```



---



## 6\. Rollback Plan



|Step|Action|Description|
|---|---|---|
|1|Stop current service|`kill <pid>` or `docker-compose down`|
|2|Restore previous code|`git checkout <previous_tag>`|
|3|Restore database backup|`mysql < backup.sql`|
|4|Restart service|`python main.py`|
|5|Verify service|Run verification commands|



---



## 7\. Monitoring \& Logging



|Item|Method|Description|
|---|---|---|
|Application logs|`app/logs/app.log`|Rotation: 10MB/file, 5 backups|
|Log format|`time - level - [module] - message`|Standard format|
|Database|MySQL slow query log|Recommended|
|LLM calls|Search log for `AI:LLM`|Tracks LLM call I/O|



## 8\. Risk Points and Countermeasures



|Risk|Impact|Mitigation|
|---|---|---|
|API Key leakage|Financial loss \+ security risk|`.env` in `.gitignore`; use secret management service \(to be confirmed\)|
|DB connection exhaustion|Service unavailable|Configure connection pool; currently short\-lived connections|
|Redis hardcoded|Environment inconsistency|Migrate Redis config to `.env`|
|Single point of failure|Service downtime|Use gunicorn \+ multi\-workers; Nginx reverse proxy recommended \(to be confirmed\)|

# Testing Plan



## 1\. Test Overview



This document covers functional testing, API testing, performance testing, security testing, and exception scenario testing for the xnsy\_agent project\.



### 1\.1 Test Scope



|Test Type|Coverage|
|---|---|
|Functional|Core Agent functionality, permission validation, data flow|
|API|System endpoints, chat endpoints, file upload|
|Performance|LLM response time, database query performance, concurrency|
|Security|SQL injection, permission bypass, path traversal, dangerous operation blocking|
|Exception|Network exception, data exception, invalid input|



### 1\.2 Test Environment



|Item|Configuration|
|---|---|
|Python|3\.10\+|
|MySQL|8\.0 \(with test data\)|
|Redis|6\.0\+|
|Network|Access to DashScope API, 12306, RSS sources|



---



## 2\. Functional Testing



### 2\.1 System Agent Functional Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|SYS\-01|Send login code \- registered email|`email=registered`|code=200, code in Redis|Pending|
|SYS\-02|Send login code \- unregistered email|`email=unregistered`|code=500, msg="Email not registered"|Pending|
|SYS\-03|Send register code \- unregistered|`email=unregistered`|code=200, code in Redis|Pending|
|SYS\-04|Send register code \- registered|`email=registered`|code=500, msg="Email registered"|Pending|
|SYS\-05|Login \- correct code|`email+correct code`|code=200, msg="Login successful"|Pending|
|SYS\-06|Login \- wrong code|`email+wrong code`|code=500|Pending|
|SYS\-07|Login \- expired code|`email+expired code`|code=500|Pending|
|SYS\-08|Register \- success|`email+code+name`|code=200, user in DB|Pending|
|SYS\-09|Register \- duplicate|`registered email+code`|code=500|Pending|



### 2\.2 SQL Question Agent Functional Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|SQL\-01|GM queries all data|GM user \+ question|SSE returns sales data|✅ Passed|
|SQL\-02|Sales manager queries authorized|Sales manager \+ question|SSE returns customer data|✅ Passed|
|SQL\-03|Regular employee queries|Employee \+ question|SSE error: insufficient permissions|✅ Passed|
|SQL\-04|Non\-existent user|Unknown user\_id|SSE error: user not found|✅ Passed|
|SQL\-05|Multi\-table JOIN|Dept manager \+ JOIN query|Returns JOIN result|✅ Passed|
|SQL\-06|Operations queries forbidden column|Operations \+ salary query|Result excludes salary column|Pending|



### 2\.3 ECharts Agent Functional Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|ECH\-01|Generate bar chart|Chart question|Returns ECharts JSON|Pending|
|ECH\-02|Generate pie chart|Chart question|Returns ECharts JSON|Pending|
|ECH\-03|Generate line chart|Chart question|Returns ECharts JSON|Pending|
|ECH\-04|Illegal SQL|DELETE attempt|Returns error|Pending|



### 2\.4 News Agent Functional Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|NWS\-01|GM queries news|GM \+ news question|SSE returns Markdown news|✅ Passed|
|NWS\-02|Employee queries news|Employee \+ news question|SSE error: insufficient permissions|✅ Passed|
|NWS\-03|Filter by category|Category question|Returns LLM\-related news|✅ Passed|
|NWS\-04|Filter by keyword|Keyword question|Returns matching news|✅ Passed|
|NWS\-05|No results|Rare keyword|Returns "No related news"|Pending|



### 2\.5 Train Agent Functional Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|TRN\-01|Normal query|Train question|Returns Markdown train table|Pending|
|TRN\-02|Fuzzy station match|Partial station name|Auto\-matches station|Pending|
|TRN\-03|Chinese date format|Chinese date input|Correctly parses date|Pending|
|TRN\-04|No results|Remote station query|Returns "No trains found"|Pending|



### 2\.6 File Analyze Agent Functional Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|FIL\-01|Normal file analysis|Upload docx|Returns analysis \+ download|Pending|
|FIL\-02|Missing values|docx with empty cells|Fills "None"|Pending|
|FIL\-03|Duplicate data|docx with duplicates|Removes duplicates|Pending|
|FIL\-04|File not found|Non\-existent file|Returns "File not found"|Pending|



---



## 3\. API Testing



### 3\.1 System API Tests



|ID|Endpoint|Method|Test Content|Expected HTTP|
|---|---|---|---|---|
|API\-01|`/send_code`|POST|Valid params|200|
|API\-02|`/send_code`|POST|Missing email|422|
|API\-03|`/send_code`|POST|Invalid email|422|
|API\-04|`/login`|POST|Valid params|200|
|API\-05|`/login`|POST|Wrong code|200 \(business error\)|
|API\-06|`/login`|POST|Missing params|422|
|API\-07|`/send_register_code`|POST|Valid params|200|
|API\-08|`/register`|POST|Valid params|200|
|API\-09|`/register`|POST|Registered email|200 \(business error\)|
|API\-10|`/register`|POST|Missing params|422|



### 3\.2 Chat API Tests



|ID|Endpoint|Method|Test Content|Expected Result|
|---|---|---|---|---|
|API\-11|`/chat`|GET|Missing question|422|
|API\-12|`/chat`|GET|Missing user\_id|422|
|API\-13|`/chat`|GET|Chart keywords|Direct JSON|
|API\-14|`/chat`|GET|Analysis keywords|Direct JSON|
|API\-15|`/chat`|GET|News keywords|SSE streaming|
|API\-16|`/chat`|GET|Train keywords|SSE streaming|
|API\-17|`/chat`|GET|Default route|SSE streaming|
|API\-18|`/upload`|POST|docx upload|code=200 \+ file\_name|
|API\-19|`/upload`|POST|Non\-docx upload|To be confirmed|
|API\-20|`/upload`|POST|No file|422|



---



## 4\. Performance Testing



### 4\.1 Test Metrics



|Metric|Target|Description|
|---|---|---|
|Response time \(non\-streaming\)|\< 5s|System \+ ECharts \+ Analyze|
|SSE first token|\< 3s|First token arrival time|
|SSE completion|\< 30s|Full streaming response|
|Concurrent users|10\+|Simultaneous requests|
|DB query|\< 1s|Single SELECT|
|RSS aggregation|\< 15s|All sources total|



### 4\.2 Test Scenarios



|ID|Scenario|Method|Monitor|
|---|---|---|---|
|PERF\-01|System concurrent|10 concurrent POST|Response time, success rate|
|PERF\-02|SQL Agent sequential|10 sequential GET|SSE completion time|
|PERF\-03|ECharts concurrent|10 concurrent chart requests|Response time|
|PERF\-04|DB pressure|100 sequential queries|Query time, connections|
|PERF\-05|Memory monitoring|1\-hour runtime|Memory growth trend|



---



## 5\. Security Testing



### 5\.1 SQL Injection Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|SEC\-01|DELETE attempt|DELETE SQL|Tool blocks: SELECT only|✅ Passed|
|SEC\-02|DROP attempt|DROP SQL|Tool blocks|✅ Passed|
|SEC\-03|SLEEP injection|SQL with SLEEP\(5\)|Tool blocks dangerous keyword|✅ Passed|
|SEC\-04|INTO OUTFILE|SQL with INTO OUTFILE|Tool blocks|✅ Passed|
|SEC\-05|UNION bypass|SQL with UNION SELECT|SELECT only, UNION needs review|To be confirmed|
|SEC\-06|Comment bypass|SQL with comment|Comment stripped before validation|Pending|



### 5\.2 Permission Bypass Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|SEC\-07|Operations query salary|salary query \+ Operations|Prompt\-level salary column removal|Pending|
|SEC\-08|Sales manager query orders|orders query \+ Sales manager|Tool blocks: no orders access|✅ Passed|
|SEC\-09|Employee SQL query|Any SQL \+ Employee|Middleware blocks: insufficient permissions|✅ Passed|
|SEC\-10|Unknown user|Non\-existent user\_id|Middleware blocks: user not found|✅ Passed|



### 5\.3 Path Traversal Tests



|ID|Scenario|Input|Expected Result|Status|
|---|---|---|---|---|
|SEC\-11|Path traversal|`question=File uploaded:../../etc/passwd`|`Path(name).name` extracts `passwd`|Pending|
|SEC\-12|Empty filename|`question=File uploaded:`|File not found message|Pending|



### 5\.4 Dangerous Operation Blocking



|ID|Scenario|Expected Result|Status|
|---|---|---|---|
|SEC\-13|INSERT|Tool blocks|✅ Passed|
|SEC\-14|UPDATE|Tool blocks|✅ Passed|
|SEC\-15|DELETE|Tool blocks|✅ Passed|
|SEC\-16|DROP|Tool blocks|✅ Passed|
|SEC\-17|ALTER|Tool blocks|✅ Passed|
|SEC\-18|TRUNCATE|Tool blocks|✅ Passed|
|SEC\-19|CREATE|Tool blocks|✅ Passed|
|SEC\-20|EXEC/EXECUTE|Tool blocks|✅ Passed|
|SEC\-21|XP\_CMDSHELL|Tool blocks|✅ Passed|



---



## 6\. Exception Scenario Testing



### 6\.1 Network Exception



|ID|Scenario|Simulation|Expected Result|
|---|---|---|---|
|EXC\-01|DB connection timeout|Stop MySQL|Tool returns "Database error"|
|EXC\-02|Redis unavailable|Stop Redis|Code storage fails, API returns 500|
|EXC\-03|LLM API timeout|Short timeout|Agent raises exception, SSE returns error|
|EXC\-04|All RSS sources down|Network off|news\_tool returns empty result|
|EXC\-05|12306 unreachable|Network off|train\_tool returns network error|
|EXC\-06|SMTP failure|Wrong email password|send\_email returns failure|



### 6\.2 Data Exception



|ID|Scenario|Input|Expected Result|
|---|---|---|---|
|EXC\-07|Large result set|No LIMIT query|Truncated to 1000 rows|
|EXC\-08|Empty result|Non\-existent data|Returns empty array|
|EXC\-09|Special characters|Quotes, newlines|Proper escaping|
|EXC\-10|Oversized input|100K chars|To be confirmed|



### 6\.3 Invalid Input



|ID|Scenario|Input|Expected Result|
|---|---|---|---|
|EXC\-11|Empty question|`question=`|422 validation error|
|EXC\-12|Non\-UTF\-8 encoding|Special encoding|To be confirmed|
|EXC\-13|Invalid user\_id|Excessive user\_id|Permission query returns user not found|
|EXC\-14|SQL injection attempt|`question=1; DROP TABLE users`|Tool blocks|



---



## 7\. Test Execution



### 7\.1 Existing Test Scripts



```Bash
# SQL Agent permission tests
python test_permission.py

# Full feature tests (News + SQL + ECharts + Analyze + File)
python test_all_features.py
```



### 7\.2 Test Results



|Total Cases|Passed|Failed|Not Executed|
|---|---|---|---|
|\~80|To be counted|To be counted|To be counted|



> **To be supplemented**: It is recommended to refactor tests using `pytest` for automated test report generation\.
> 
> 



---



## 8\. Test Risks



|Risk|Impact|Mitigation|
|---|---|---|
|Dependency on external APIs \(LLM/12306/RSS\)|Test stability|Separate unit tests \(Mock\) from integration tests \(real API\)|
|Insufficient test data|Coverage gaps|Prepare complete test datasets and initialization scripts|
|SSE streaming hard to auto\-assert|Test efficiency|Use SSE client libraries; check final output content|
|Code expiration timing|Test flakiness|Set verification code directly in Redis during tests, bypassing email|



