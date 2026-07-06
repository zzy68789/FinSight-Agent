# DRAI - Deep Research Agent Intelligence

DRAI 是一个基于 **Spring Boot + LangGraph4j + Vue 3** 的 Multi-Agent 深度调研报告生成系统。用户可以输入研究主题，也可以上传 PDF 构建本地知识库；后端会通过 Agent 工作流完成任务规划、资料检索、报告撰写、质量审查、失败回环和报告修订，并通过 SSE 将执行过程实时推送到前端。

重点展示 Java 后端工程化、Agent 工作流编排、RAG 检索增强、长流程状态持久化和前后端流式交互。

## 功能特性

- **Multi-Agent 工作流编排**：基于 LangGraph4j 构建 Router、Planner、Researcher、Writer、Reviewer、Refiner 多节点状态机，并记录 Planner 子任务的独立检索结果、证据数量和执行耗时。
- **质量审查与失败回环**：Reviewer 节点会结合报告和检索证据进行审查；证据为空时直接返回“证据不足”，失败时回到 Planner 补充规划，最多重试 3 轮。
- **PDF 混合知识库检索**：支持上传最多 5 个 PDF，后端使用 PDFBox 提取文本、切片、生成 embedding，并结合 BM25 与 ChromaDB 向量检索返回本地证据。
- **Document / Hybrid / Web 模式**：Document 模式只基于本地文档回答；Hybrid 模式先查本地证据，不足时补充 Tavily 搜索结果；Web 模式只使用联网搜索。
- **报告修订能力**：同一 `thread_id` 下，如果用户输入“修改、改写、补充、重写、调整”等指令，会进入 Refiner 节点基于旧报告生成新版本。
- **SSE 实时推送**：后端以 Server-Sent Events 推送 `planner`、`researcher`、`writer`、`reviewer`、`refiner` 等执行状态。
- **任务状态持久化**：通过 JDBC 持久化任务、Agent 步骤日志、报告版本和 checkpoint。
- **前端历史与报告工作台**：Vue 前端提供 Run、Tasks、Reports、Settings 四个工作区，可查看历史任务、Agent 执行日志、报告版本，并从报告库发起修订。
- **用户登录与数据隔离**：提供注册、登录、当前用户接口，前端保存 Bearer Token；任务和报告按 `owner_id` 隔离查询。
- **A股投研报告链路**：新增股票代码分析模式和 `/api/stock-reports` SSE 入口，按股票解析、数据快照、指标计算、风险评分、证据账本、报告撰写、引用审查生成八章节 A 股投研报告，并统一标注“不构成投资建议”。
- **金融证据与回放**：新增金融快照、证据、指标、Bad Case 反馈表；财务指标由 Java `BigDecimal` 确定性计算，缺失输入标记为 `MISSING_INPUT`，公开数据源失败标记为 `DATA_MISSING`。
- **风险评分与合规审查**：参考外部金融多 Agent 项目的风险经理和合规 Agent 思路，新增五维风险评分、provider 并行采集阶段记录、合规 issue 列表，最终报告必须同时通过引用审查和合规审查。
- **金融自动评测门控**：`financial-eval-set.json` 已接入 `FinancialEvaluationService`，股票报告命中默认样例时会输出 `evaluation` SSE 事件，并检查关键点覆盖、数字一致性、引用命中和无依据荐股表达。
- **A股本地主档样例**：内置 10 个评测样例股票的公司名称和行业，用于股票解析和 `LOCAL_CONTEXT` 证据补强；实时行情、估值和新闻仍走 provider 扩展。
- **TuShare 授权行情数据源**：新增 `TushareMarketDataProvider`，可通过环境变量接入 TuShare Pro，将利润表、资产负债表、现金流和日行情基础指标转为金融证据账本输入。
- **本地可演示降级**：未配置 LLM API Key 或 Tavily Key 时，系统仍可用本地降级内容跑通完整流程。

## 技术栈

**Backend**

- Java 17
- Spring Boot 3.4.3
- Spring Web / Validation / JDBC
- LangGraph4j
- LangChain4j OpenAI-compatible ChatModel
- PDFBox
- MySQL
- Redis
- ChromaDB
- Tavily Search API
- SSE

**Frontend**

- Vue 3
- Vite
- Tailwind CSS
- markdown-it
- markdown-it-katex
- lucide-vue-next

> 说明：当前 MVP 使用 MySQL 做长期持久化，Redis 保存运行中任务状态、Agent 进度和 thread 最新任务映射；RAG 主链路已接入 ChromaDB 向量检索，本地未启动 ChromaDB 时会降级为进程内向量仓库，便于开发演示。

## 系统流程

```text
Router
  ├─ 新任务 -> Planner -> Researcher -> Writer -> Reviewer
  │                                      ├─ PASS -> END
  │                                      └─ FAIL -> Planner，最多 3 轮
  └─ 修改指令 -> Refiner -> END
```

A股投研报告链路独立于通用调研链路：

```text
StockResolve -> DataSnapshot -> MetricEngine -> RiskAssessment -> EvidenceCollect -> InvestmentWriter -> CitationReviewer + ComplianceReviewer -> Evaluation -> END
```

SSE 返回格式示例：

```text
data: {"step":"planner","data":{...}}
data: {"step":"researcher","data":{...}}
data: {"step":"writer","data":{...}}
data: {"step":"reviewer","data":{...}}
data: {"step":"refiner","data":{...}}
data: [DONE]
```

股票报告 SSE 会额外返回：

```text
data: {"step":"stock_resolve","data":{...}}
data: {"step":"data_snapshot","data":{...}}
data: {"step":"metric_engine","data":{...}}
data: {"step":"risk_assessment","data":{...}}
data: {"step":"evidence_collect","data":{...}}
data: {"step":"writer","data":{...}}
data: {"step":"reviewer","data":{...}}
data: {"step":"evaluation","data":{...}}
data: {"step":"done","data":{...}}
data: [DONE]
```

## 目录结构

```text
DRAI/
├── backend/                       # Java 后端
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/zzy/drai/
│       │   ├── agent/             # LangGraph4j 工作流、节点和状态
│       │   ├── config/            # CORS、Agent Bean 配置
│       │   ├── controller/        # REST API 和 SSE 接口
│       │   ├── dto/               # 请求 / 响应 DTO
│       │   ├── financial/         # A股投研报告、金融证据、指标和回放
│       │   ├── llm/               # OpenAI-compatible LLM 封装
│       │   ├── rag/               # PDF 解析、切片、embedding、ChromaDB 向量检索
│       │   ├── repository/        # JDBC 持久化
│       │   ├── search/            # Tavily 搜索封装
│       │   └── service/           # 任务、报告、SSE 服务
│       └── resources/
│           ├── application.yml
│           └── db/schema.sql      # Spring Boot 自动执行的数据库初始化脚本
├── frontend/                      # Vue 3 前端
├── docs/                          # 演示截图或项目文档
├── .gitignore
└── README.md
```

## 快速启动

### 1. 准备 MySQL

先创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS drai
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

如果你的 MySQL 用户名或密码不是默认值，先设置环境变量：

```powershell
$env:DRAI_DATASOURCE_URL="jdbc:mysql://localhost:3306/drai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DRAI_DATASOURCE_USERNAME="root"
$env:DRAI_DATASOURCE_PASSWORD="your_password"
```

建表脚本位于：

```text
backend/src/main/resources/db/schema.sql
```

这是 Spring Boot 推荐的 classpath 资源目录写法。应用启动时会根据 `application.yml` 中的 `spring.sql.init.schema-locations` 自动执行该脚本，因此不需要额外在 `backend/` 下维护一份重复的 `sql/` 目录。首次启动前只需要确保 MySQL 中已经存在 `drai` 数据库。

如果你的本地库已经用旧版本启动过，请手动执行一次用户隔离升级脚本：

```text
backend/src/main/resources/db/upgrade-user-auth.sql
backend/src/main/resources/db/upgrade-report-library.sql
backend/src/main/resources/db/upgrade-admin-console.sql
backend/src/main/resources/db/upgrade-financial-report.sql
```

### 2. 启动后端

进入后端目录：

```powershell
cd backend
mvn.cmd spring-boot:run
```

默认服务地址：

```text
http://localhost:8000
```

健康检查：

```powershell
curl http://localhost:8000/
```

返回示例：

```json
{
  "status": "running",
  "backend": "java",
  "workflow": "langgraph4j"
}
```

### 3. 启动前端

```powershell
cd frontend
npm.cmd install
npm.cmd run dev
```

前端默认请求后端：

```text
http://localhost:8000/api
```

## 环境变量

后端默认连接本机 MySQL。首次启动前请先创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS drai
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

默认连接配置如下，可按需通过环境变量覆盖：

```powershell
$env:DRAI_DATASOURCE_URL="jdbc:mysql://localhost:3306/drai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DRAI_DATASOURCE_USERNAME="root"
$env:DRAI_DATASOURCE_PASSWORD="your_password"
$env:DRAI_DATASOURCE_DRIVER="com.mysql.cj.jdbc.Driver"
```

认证配置：

```powershell
$env:DRAI_AUTH_ENABLED="true"
$env:DRAI_AUTH_TOKEN_SECRET="replace_with_a_long_random_secret"
$env:DRAI_AUTH_TOKEN_TTL_SECONDS="86400"
```

Redis 默认连接本机 `localhost:6379`。如需覆盖配置：

```powershell
$env:DRAI_REDIS_HOST="localhost"
$env:DRAI_REDIS_PORT="6379"
$env:DRAI_REDIS_PASSWORD=""
$env:DRAI_REDIS_TIMEOUT="2s"
$env:DRAI_REDIS_RUNTIME_TTL="PT24H"
```

Redis 用于保存运行态数据，主要 key 包括：

```text
drai:task:{taskId}:status
drai:task:{taskId}:progress
drai:thread:{threadId}:latestTask
drai:sse:{taskId}:last-event
```

如果本地 Redis 未启动，后端会降级使用进程内内存状态，避免开发环境直接中断；生产环境建议启动 Redis。

ChromaDB 默认连接本机 `http://localhost:8000`。如需本地启动 ChromaDB，可使用 Docker：

```powershell
docker run -d --name drai-chroma -p 8000:8000 chromadb/chroma
```

也可以通过环境变量覆盖 ChromaDB 配置：

```powershell
$env:DRAI_CHROMA_BASE_URL="http://localhost:8000"
$env:DRAI_CHROMA_TENANT="default_tenant"
$env:DRAI_CHROMA_DATABASE="default_database"
$env:DRAI_CHROMA_COLLECTION="drai_docs"
$env:DRAI_CHROMA_TOKEN=""
```

如果本地 ChromaDB 未启动，后端会降级使用进程内向量仓库；这种模式能跑通上传和检索流程，但服务重启后知识库会丢失。要验证真正的持久化向量 RAG，需要提前启动 ChromaDB。

RAG 检索默认使用 `BM25 + vector` 融合，并用相关性阈值判断本地证据是否足够：

```powershell
$env:DRAI_RAG_RELEVANCE_THRESHOLD="0.2"
```

配置大模型和搜索服务：

```powershell
$env:OPENAI_API_BASE="https://your-openai-compatible-endpoint/v1"
$env:OPENAI_API_KEY="your_api_key"
$env:DRAI_LLM_FAST_MODEL="qwen3-max"
$env:DRAI_LLM_SMART_MODEL="deepseek-r1"
$env:DRAI_LLM_TIMEOUT="30s"
$env:DRAI_LLM_MAX_ATTEMPTS="2"
$env:DRAI_LLM_PROVIDER_MAX_RETRIES="0"
$env:DRAI_LLM_LOG_REQUESTS="false"
$env:DRAI_LLM_LOG_RESPONSES="false"
$env:DRAI_EMBEDDING_MODEL="text-embedding-3-small"
$env:TAVILY_API_KEY="your_tavily_key"
$env:DRAI_SEARCH_MAX_ATTEMPTS="2"
```

后端通过 LangChain4j `OpenAiChatModel` 调用 OpenAI-compatible `/chat/completions`，并在外层增加失败重试和本地 fallback。Planner 会优先解析 JSON array，Reviewer 会强制解析 `{"status":"PASS|FAIL","feedback":""}` 结构。未配置 `OPENAI_API_KEY` 时，系统会使用本地降级内容，便于验证完整 Agent 流程；embedding 也会降级为本地 hash 向量，方便开发环境跑通 ChromaDB 写入和查询；搜索服务通过 `SearchSource` 抽象接入 Tavily，并统一做失败重试、去重、质量过滤和本地 fallback，未配置 `TAVILY_API_KEY` 时会返回降级结果。

配置 TuShare Pro 授权行情和财务数据源：

```powershell
$env:DRAI_TUSHARE_ENABLED="true"
$env:DRAI_TUSHARE_BASE_URL="https://api.tushare.pro"
$env:DRAI_TUSHARE_API_KEY="your_tushare_token"
$env:DRAI_TUSHARE_TIMEOUT="10s"
```

`DRAI_TUSHARE_API_KEY` 也兼容上一版通用变量名 `DRAI_MARKET_API_KEY`。TuShare provider 只在 `hybrid` / `web` 股票报告模式下调用，`document` 模式不会访问外部行情接口；未配置或未启用时，系统继续使用本地主档、已上传报告和 Tavily 搜索 fallback。

## API 说明

### 健康检查

```http
GET /
```

### 上传 PDF

```http
POST /api/upload
Content-Type: multipart/form-data
Field: files
```

限制：

- 最多 5 个文件
- 单文件最大 20 MB
- 总请求最大 50 MB

响应示例：

```json
{
  "status": "success",
  "file_count": 1,
  "chunks_stored": 12,
  "message": "知识库构建成功"
}
```

### 清空知识库

```http
POST /api/clear
```

响应示例：

```json
{
  "status": "success",
  "message": "知识库已重置"
}
```

### 启动调研

```http
POST /api/chat
Content-Type: application/json
Accept: text/event-stream
```

请求示例：

```json
{
  "query": "AI Agent 技术趋势",
  "search_mode": "hybrid",
  "thread_id": "demo-thread"
}
```

`search_mode` 支持：

- `document`：只使用本地知识库；没有相关本地证据时停止，不继续联网搜索。
- `hybrid`：先使用 BM25 + ChromaDB 向量检索本地知识库；本地证据不足时自动补充 Tavily 搜索。
- `web`：跳过本地 RAG，只使用 Tavily 搜索。

字段说明：

- `query`：用户调研问题或报告修改指令。
- `search_mode`：支持 `document`、`hybrid` 和 `web`。
- `thread_id`：会话 ID，同一 ID 下可触发报告修订。

### 启动 A股投研报告

```http
POST /api/stock-reports
Content-Type: application/json
Accept: text/event-stream
```

请求示例：

```json
{
  "ticker": "600519",
  "thread_id": "demo-stock-thread",
  "report_period": "latest",
  "search_mode": "hybrid"
}
```

字段说明：

- `ticker`：A 股 6 位代码，或带 `.SH` / `.SZ` 后缀的代码。
- `report_period`：报告期口径，默认 `latest`。
- `search_mode`：复用 `document`、`hybrid`、`web`；MVP 不引入 Python、akshare、OpenSearch 或 pgvector。
- `thread_id`：可选；不传时后端会生成股票报告线程 ID。

Bad Case 反馈与回放：

```http
POST /api/stock-reports/{taskId}/feedback
GET /api/stock-reports/{taskId}/replay
```

## 数据表

后端启动时会执行 `backend/src/main/resources/db/schema.sql`，创建以下核心表：

- `research_task`：调研任务主表。
- `agent_step_log`：Agent 节点执行日志。
- `report`：报告内容和版本。
- `checkpoint`：工作流状态快照。
- `stock_analysis_snapshot`：股票报告生成时使用的数据快照。
- `stock_evidence_item`：金融证据账本，包含来源、报告期、指标名、数值、片段、置信度和缺失标记。
- `stock_metric_result`：Java 指标引擎计算结果、公式和缺失原因。
- `stock_bad_case_feedback`：数字错、引用错、逻辑错、信息过期等反馈和回放快照。

## 测试与构建

后端测试：

```powershell
cd backend
mvn test
```

后端打包：

```powershell
cd backend
mvn package -DskipTests
```

前端构建：

```powershell
cd frontend
npm.cmd run build
```

## Report Library and Export

The report library basic version supports:

- User-scoped report list: `GET /api/reports?keyword=agent&favoriteOnly=false`
- Thread report versions: `GET /api/threads/{threadId}/reports`
- Report detail: `GET /api/reports/{reportId}`
- Export: `GET /api/reports/{reportId}/export?format=pdf|docx|md`
- Favorite toggle: `POST /api/reports/{reportId}/favorite?favorite=true`
- Reuse report as RAG context: `POST /api/reports/{reportId}/knowledge-base`
- Soft delete: `DELETE /api/reports/{reportId}`

For an existing MySQL database, run `backend/src/main/resources/db/upgrade-report-library.sql` once before using favorite, soft delete, and report reuse metadata. New databases created from `backend/src/main/resources/db/schema.sql` already include these columns.

## Admin Console

The admin console basic version supports:

- Admin-only APIs under `/api/admin/**`; non-admin users receive HTTP 403.
- User management: `GET /api/admin/users`, `PATCH /api/admin/users/{userId}/role`, `PATCH /api/admin/users/{userId}/status`.
- Global task monitoring: `GET /api/admin/tasks`, `GET /api/admin/tasks/{taskId}/logs`.
- Global report governance: `GET /api/admin/reports`, `DELETE /api/admin/reports/{reportId}`.
- System status view: `GET /api/admin/system/health`.
- Audit logging for role changes, user status changes, and admin report deletion.

For an existing MySQL database, run `backend/src/main/resources/db/upgrade-admin-console.sql` once before using the admin console. New databases created from `backend/src/main/resources/db/schema.sql` already include `app_user.status`, `app_user.last_login_at`, and `admin_audit_log`.

## 当前边界

当前版本是可运行的 MVP，不是完整企业级平台。以下能力属于后续扩展方向：

- 更完整的 Hybrid RAG：BM25 + ChromaDB 向量检索 + rerank。
- 多搜索源 fallback，例如 Brave、Jina、arXiv、GitHub Search。
- 更细粒度的企业权限、团队空间和审计治理。
- 更完整的人工反馈 HITL 流程、批量评测报告和 CI 门控。
- 更稳定的金融数据源、行业横向对比、ECharts 图表、组合监控和预警。
