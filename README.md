# FinSight Agent - A股/ETF 金融投研 Agent

FinSight Agent 是从 DRAI 重构出来的金融投研专用版本，基于 **Spring Boot 3.4.3 + Java 17 + Vue 3**。本仓库不再维护通用 deep-research 编排链路，核心目标收束为：围绕 A 股和 ETF 代码生成可追踪、可回放、带证据账本和合规审查的研究辅助报告。

系统保留 DRAI 中对金融链路仍有价值的基础设施：PDF/RAG 证据输入、Tavily 搜索封装、MySQL 持久化、Redis 运行态降级、ChromaDB 向量检索降级、用户登录、报告库、管理员后台和 SSE 流式推送。

> 合规边界：报告统一标注“仅作研究辅助，不构成投资建议”。系统不做荐股、仓位建议、保证收益、自动交易或回测。

## 功能特性

- **证券代码报告链路**：`POST /api/stock-reports` 通过 SSE 推送证券代码解析、数据快照、指标计算、风险评分、证据收集、报告撰写、引用/合规审查和自动评测结果。
- **A股/ETF 解析**：普通 A 股支持 `6xxxxx -> .SH`、`0xxxxx / 2xxxxx / 3xxxxx -> .SZ`；常见 ETF 支持 `5xxxxx -> .SH`、`15xxxx / 16xxxx / 18xxxx -> .SZ`。
- **金融数据快照**：通过 `FinancialDataProvider` 扩展点聚合上传报告、本地主档、Tavily fallback 和 TuShare Pro 数据源。
- **确定性指标计算**：`FinancialMetricEngine` 使用 Java `BigDecimal` 计算关键财务指标；缺输入标记 `MISSING_INPUT`，外部数据源失败标记 `DATA_MISSING`。
- **公式审计与报告复用**：指标公式由 `MetricDefinitionCatalog` 版本化管理；数据快照和生成规则分别计算 SHA-256，相同上下文只复用同一用户下已通过评审的报告。
- **可恢复工作流**：任务持久化阶段、请求、尝试次数、心跳和数据库租约；SSE 客户端断开不影响后台执行，超时任务最多恢复 3 次。
- **可信度轨迹**：报告页展示 BM25/向量检索分数、证据有效率、阶段耗时、评审结果、快照哈希和缓存命中来源。
- **风险评分**：`FinancialRiskScoringService` 按基本面、技术面、情绪面、消息面和市场环境输出五维风险评分、风险等级和缺失证据 warning。
- **引用与合规审查**：`CitationReviewer` 检查证据数量、报告期、指标引用和指标值展示；`FinancialComplianceReviewer` 检查免责声明、保证收益、内幕信息等风险表达。
- **自动评测门控**：`FinancialEvaluationService` 加载 `financial-eval-set.json`，对默认样例输出 claim/citation/numeric/keypoint 评测结果。
- **Bad Case 反馈与回放**：支持数字错、引用错、逻辑错、信息过期等反馈类型，并可回放 snapshot + evidence + metric。
- **报告库与导出**：支持报告列表、版本查看、Markdown/PDF/Word 导出、收藏、软删除和加入 RAG。
- **本地可演示降级**：未配置 LLM、Tavily、TuShare、Redis 或 ChromaDB 时，仍可通过本地 fallback 跑通核心流程。

## 当前重构状态

- 后端通用 deep-research 文件已从本仓库移除，包括 `agent/graph`、通用 `agent/node`、通用 `ResearchTaskService` 和 `/api/chat` 控制器。
- 金融投研链路保留在 `backend/src/main/java/com/zzy/drai/financial/`，公开入口为 `/api/stock-reports`。
- RAG、搜索、报告库、用户、管理员后台等基础设施继续复用，但定位为金融投研链路的支撑能力。
- 前端仍有部分通用研究模式 UI/调用需要后续清理，路线图见 `docs/待实现功能.md`。

## 技术栈

**Backend**

- Java 17
- Spring Boot 3.4.3
- Spring Web / Validation / JDBC
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

## 金融工作流

```text
StockResolve
  -> DataSnapshot
  -> MetricEngine
  -> RiskAssessment
  -> EvidenceCollect
  -> InvestmentWriter
  -> CitationReviewer + ComplianceReviewer
  -> Evaluation
  -> END
```

股票报告 SSE 示例：

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
FinSight-Agent/
├── backend/
│   ├── pom.xml
│   └── src/main/
│       ├── java/com/zzy/drai/
│       │   ├── auth/              # 注册、登录、Bearer Token、用户上下文
│       │   ├── config/            # CORS、LLM、异步执行器等配置
│       │   ├── controller/        # REST API 与 SSE 接口
│       │   ├── dto/               # 请求 / 响应 DTO
│       │   ├── financial/         # A股/ETF 投研报告、证据、指标、合规、回放
│       │   ├── llm/               # OpenAI-compatible LLM 封装
│       │   ├── rag/               # PDF 解析、切片、embedding、ChromaDB 向量检索
│       │   ├── repository/        # JDBC 持久化
│       │   ├── search/            # Tavily 搜索封装
│       │   └── service/           # 报告、任务查询、SSE、管理员服务
│       └── resources/
│           ├── application.yml
│           ├── financial-eval-set.json
│           └── db/                # Flyway 迁移、完整 schema 与手动升级脚本
├── frontend/                      # Vue 3 前端
├── docs/                          # 路线图、已实现能力、遗留问题、踩坑日志
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

如需覆盖默认连接配置：

```powershell
$env:DRAI_DATASOURCE_URL="jdbc:mysql://localhost:3306/drai?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true"
$env:DRAI_DATASOURCE_USERNAME="root"
$env:DRAI_DATASOURCE_PASSWORD="your_password"
$env:DRAI_DATASOURCE_DRIVER="com.mysql.cj.jdbc.Driver"
```

数据库由 Flyway 自动管理：空库依次执行 `V1__init.sql` 和后续迁移；已有旧库通过 `baseline-version=1` 接管后执行增量迁移。`schema.sql` 保留为当前完整结构参考，不再由 Spring SQL Init 自动执行。

如需临时关闭自动迁移，可设置 `DRAI_FLYWAY_ENABLED=false`；关闭后需自行保证数据库结构与代码一致。

### 2. 启动后端

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

前端默认请求：

```text
http://localhost:8000/api
```

## 环境变量

项目配置统一使用 `DRAI_*` 前缀，不读取全局 `OPENAI_API_KEY`，避免影响用户的全局 OpenAI / cc-switch 配置。

认证配置：

```powershell
$env:DRAI_AUTH_ENABLED="true"
$env:DRAI_AUTH_TOKEN_SECRET="replace_with_a_long_random_secret"
$env:DRAI_AUTH_TOKEN_TTL_SECONDS="86400"
```

Redis 配置：

```powershell
$env:DRAI_REDIS_HOST="localhost"
$env:DRAI_REDIS_PORT="6379"
$env:DRAI_REDIS_PASSWORD=""
$env:DRAI_REDIS_TIMEOUT="2s"
$env:DRAI_REDIS_RUNTIME_TTL="PT24H"
```

ChromaDB 配置：

```powershell
$env:DRAI_CHROMA_BASE_URL="http://localhost:8000"
$env:DRAI_CHROMA_TENANT="default_tenant"
$env:DRAI_CHROMA_DATABASE="default_database"
$env:DRAI_CHROMA_COLLECTION="drai_docs"
$env:DRAI_CHROMA_TOKEN=""
```

RAG 检索阈值：

```powershell
$env:DRAI_RAG_RELEVANCE_THRESHOLD="0.2"
```

LLM 与搜索配置：

```powershell
$env:DRAI_LLM_BASE_URL="https://your-openai-compatible-endpoint/v1"
$env:DRAI_LLM_API_KEY="your_api_key"
$env:DRAI_LLM_FAST_MODEL="qwen3-max"
$env:DRAI_LLM_SMART_MODEL="deepseek-r1"
$env:DRAI_LLM_TIMEOUT="30s"
$env:DRAI_LLM_MAX_ATTEMPTS="2"
$env:DRAI_LLM_PROVIDER_MAX_RETRIES="0"
$env:DRAI_LLM_LOG_REQUESTS="false"
$env:DRAI_LLM_LOG_RESPONSES="false"
$env:DRAI_EMBEDDING_MODEL="text-embedding-3-small"
$env:DRAI_TAVILY_API_KEY="your_tavily_key"
$env:DRAI_SEARCH_MAX_ATTEMPTS="2"
```

TuShare Pro 配置：

```powershell
$env:DRAI_TUSHARE_ENABLED="true"
$env:DRAI_TUSHARE_BASE_URL="https://api.tushare.pro"
$env:DRAI_TUSHARE_API_KEY="your_tushare_token"
$env:DRAI_TUSHARE_TIMEOUT="10s"
$env:DRAI_FINANCIAL_PROVIDER_THREADS="6"
```

`DRAI_TUSHARE_API_KEY` 兼容上一版变量名 `DRAI_MARKET_API_KEY`。TuShare provider 只在 `hybrid` / `web` 股票报告模式下调用；`document` 模式不会访问外部行情接口。

## API 说明

### 健康检查

```http
GET /
```

### 上传 PDF 研究资料

```http
POST /api/upload
Content-Type: multipart/form-data
Field: files
```

限制：

- 最多 5 个文件
- 单文件最大 20 MB
- 总请求最大 50 MB

### 清空知识库

```http
POST /api/clear
```

### 启动证券代码研究报告

```http
POST /api/stock-reports
Content-Type: application/json
Accept: text/event-stream
```

请求示例：

```json
{
  "ticker": "588200",
  "thread_id": "demo-stock-thread",
  "report_period": "latest",
  "search_mode": "hybrid"
}
```

字段说明：

- `ticker`：普通 A 股或常见 ETF 的 6 位代码，也支持 `.SH` / `.SZ` 后缀。
- `thread_id`：可选；不传时后端生成证券研究报告线程 ID。
- `report_period`：报告期口径，默认 `latest`。
- `search_mode`：支持 `document`、`hybrid`、`web`。

Bad Case 反馈与回放：

```http
POST /api/stock-reports/{taskId}/feedback
GET /api/stock-reports/{taskId}/replay
GET /api/stock-reports/{taskId}/trace
POST /api/stock-reports/{taskId}/retry
```

运行监控：

```http
GET /actuator/health
GET /actuator/prometheus
```

### 报告库

```http
GET /api/reports?keyword=agent&favoriteOnly=false
GET /api/threads/{threadId}/reports
GET /api/reports/{reportId}
GET /api/reports/{reportId}/export?format=pdf|docx|md
POST /api/reports/{reportId}/favorite?favorite=true
POST /api/reports/{reportId}/knowledge-base
DELETE /api/reports/{reportId}
```

### 管理员后台

```http
GET /api/admin/users
PATCH /api/admin/users/{userId}/role
PATCH /api/admin/users/{userId}/status
GET /api/admin/tasks
GET /api/admin/tasks/{taskId}/logs
GET /api/admin/reports
DELETE /api/admin/reports/{reportId}
GET /api/admin/system/health
```

## 数据表

`backend/src/main/resources/db/schema.sql` 会创建核心表：

- `research_task`：任务主表。
- `agent_step_log`：Agent 阶段执行日志。
- `report`：报告内容和版本。
- `checkpoint`：工作流状态快照。
- `stock_analysis_snapshot`：股票报告生成时的数据快照。
- `stock_evidence_item`：金融证据账本。
- `stock_metric_result`：Java 指标引擎计算结果。
- `stock_bad_case_feedback`：Bad Case 反馈和回放快照。
- `app_user`：用户账号与角色。
- `admin_audit_log`：管理员操作审计。

## 测试与构建

后端测试：

```powershell
cd backend
mvn.cmd test
```

后端打包：

```powershell
cd backend
mvn.cmd package -DskipTests
```

前端构建：

```powershell
cd frontend
npm.cmd run build
```

## 当前边界

- 当前仓库聚焦金融投研报告 Agent，不再对外提供通用 `/api/chat` deep-research 链路。
- 前端通用研究模式仍需清理或改造成金融入口，这是下一轮 P0/P1 工作。
- TuShare 真实 token、缓存、限速、接口权限错误提示和更多行情序列仍需继续硬化。
- ETF 深度数据、ECharts 行情图、多空辩论、批量评测 CLI 和真实 MySQL 迁移集成测试属于后续增强。
