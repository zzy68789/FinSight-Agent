# FinSight Agent - A股/ETF 金融投研 Agent

FinSight Agent 是金融投研专用系统，基于 **Spring Boot 3.4.3 + Java 17 + Vue 3**。本仓库不再维护通用 deep-research 编排链路，核心目标收束为：围绕 A 股和 ETF 代码生成可追踪、可回放、带证据账本和合规审查的研究辅助报告。

系统保留 FinSight 中对金融链路仍有价值的基础设施：PDF/RAG 证据输入、Tavily 搜索封装、MySQL 持久化、Redis 运行态降级、ChromaDB 向量检索降级、用户登录、报告库、管理员后台和 SSE 流式推送。

> 合规边界：报告统一标注“仅作研究辅助，不构成投资建议”。系统不做荐股、仓位建议、保证收益、自动交易或回测。

## 功能特性

- **受约束 Research Agent**：`POST /api/research-runs` 接收证券代码和自然语言研究问题，由 Planner 动态选择只读白名单工具，并通过 SSE 推送计划、工具调用、观察、重规划、综合、门禁和停止事件；`POST /api/stock-reports` 仅作为兼容入口转入同一 Runtime。
- **A股/ETF 搜索与解析**：`GET /api/securities/search` 支持按代码或本地主档名称返回候选，`GET /api/securities/{ticker}/preview` 返回规范化代码、资产类型和名称确认状态；普通 A 股支持 `6xxxxx -> .SH`、`0xxxxx / 2xxxxx / 3xxxxx -> .SZ`，常见 ETF 支持 `5xxxxx -> .SH`、`15xxxx / 16xxxx / 18xxxx -> .SZ`。
- **类型化研究意图**：Research Agent 请求支持综合研究、财务质量、估值风险、ETF 跟踪和事件影响；意图进入 Planner 与恢复/复用指纹，但不在 Runtime 中写死工具执行顺序，股票/ETF 不兼容意图会在入队前拒绝。
- **增量证据账本**：Planner 可按问题选择公司主档、TuShare 财务、公开行情、用户上传报告和问题导向的公开网页检索工具；每次工具观察增量合并、校验和去重证据，不再无条件执行全部 Provider。
- **ETF 深度快照**：ETF 聚合 TuShare `fund_daily`、`fund_basic`、`fund_nav`，保存 60 日 OHLC/成交量/成交额、基金资料、单位/累计净值、资产净值和同日折溢价；单接口失败按项降级。
- **确定性指标计算**：`FinancialMetricEngine` 使用 Java `BigDecimal` 计算关键财务指标；缺输入标记 `MISSING_INPUT`，外部数据源失败标记 `DATA_MISSING`。
- **公式审计与上下文隔离**：指标公式由 `MetricDefinitionCatalog` 版本化管理；报告复用摘要包含研究问题、截止日期、观察区间、研究深度、最终快照以及 Planner/toolset/policy 版本，历史 PASS 报告仍需重新通过当前门禁。
- **可恢复 Agent Runtime**：任务持久化请求、轮次、工具调用、预算消耗、停止原因、心跳、租约和 AgentState Checkpoint；SSE 客户端断开不影响后台执行，过期任务从最近完整轮次恢复，最多尝试 3 次。若崩溃前已打开但尚未提交下一轮，恢复会复用该 turn 的原始完整动作和 Planner 元数据，不重新生成可能改变工具参数的动作。
- **反馈感知 Replan 与模型分层**：重规划直接读取最近观察、门禁问题、补证据尝试和证据增量；普通动作优先 FAST，建计划、重规划和复杂恢复使用 SMART，结构失败自动升级并持久化模型、Token、耗时和合法率。
- **原子完成与可续传事件**：Planner 动作、工具结果、证据、turn、Checkpoint 和租约续期按轮次原子提交；PASS 报告、快照冻结、任务完成与版本化事件 outbox 在最终事务内一起提交。发布器通过数据库 claim、租约、退避和死信协调多实例，SSE 支持按 `Last-Event-ID` 回放缺口，实时流与历史 Trace 读取同一事件契约。
- **类型化只读工具**：Planner 参数先按工具 schema 解码为强类型命令，工具只读取不可变上下文并返回类型化 `ToolPayload`，状态变更统一由 Runtime reducer 串行应用。
- **可信度轨迹**：报告页展示 BM25/向量检索分数、证据有效率、阶段耗时、评审结果、快照哈希和缓存命中来源。
- **独立研究页**：`/reports/:reportId` 汇合报告版本、任务回放与证据账本，支持逐行版本对比、证据筛选、正文 `[E#]` 锚点和 ETF ECharts 行情图。
- **证据约束多空工具**：`BullBearCaseBuilder` 由 `build_bull_bear_cases` 工具调用，基于同一确定性指标/风险快照输出正反条件，每条事实论据绑定证据编号，并继续接受引用、合规和自动评测门控。
- **风险评分**：`FinancialRiskScorer` 按基本面、技术面、情绪面、消息面和市场环境输出五维风险评分、风险等级和缺失证据 warning。
- **引用与合规审查**：`CitationReviewer` 除检查证据数量、报告期和就近引用外，还会抽取正文中的百分比、倍数和金额并逐项对齐确定性指标/冻结证据；`FinancialComplianceReviewer` 检查免责声明、保证收益、内幕信息等风险表达。
- **分层评测门控**：所有股票和 ETF 都执行线上引用、数字、报告期和方向性观点硬门禁；离线 `dataset-v1` 另提供 20 个冻结报告样例、24 个检索标注、RAG 指标、历史基线和可选 LLM-as-Judge。
- **Bad Case 反馈与回放**：支持数字错、引用错、逻辑错、信息过期等反馈类型，并可回放 snapshot + evidence + metric。
- **报告库与导出**：支持报告列表、版本查看、Markdown/PDF/Word 导出、收藏、软删除和加入 RAG。
- **用户隔离知识库**：PDF 上传、报告加入 RAG、BM25/Chroma 检索和知识库清理均绑定当前登录用户，不跨用户共享文档。
- **本地可演示降级**：未配置 LLM、Tavily、TuShare、Redis 或 ChromaDB 时，仍可通过本地 fallback 跑通核心流程。

## 当前重构状态

- 固定的 `StockReportWorkflow`、`StockReportRunner`、旧恢复调度和阶段式检查点代码已经删除，`component.workflow` 包不再存在。
- 新执行内核位于 `agent/runtime`、`agent/planning`、`agent/tool`、`agent/memory` 和 `agent/event`；金融确定性计算、数据源和最终审查仍作为受控能力保留。
- 前端 Run 工作区已迁移到 `/api/research-runs`，支持研究问题、截止日期、观察区间和研究深度，并展示动态计划与工具轨迹。
- 旧 `/api/stock-reports` 请求会转换为默认研究问题后进入同一 Agent Runtime；反馈、回放和历史轨迹接口继续兼容。

## 技术栈

**Backend**

- Java 17
- Spring Boot 3.4.3
- Spring Web / Validation / MyBatis 3.0.4
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
- Vue Router
- ECharts

## Research Agent 运行循环

```text
ResearchQuestion -> Plan -> SelectTool -> Act -> Observe
                         ^                    |
                         |------ Replan ------|
                                              v
                                      Synthesize -> Guard -> Stop
```

Agent SSE 示例：

```text
data: {"step":"run_created","data":{...}}
data: {"step":"plan_created","data":{...}}
data: {"step":"tool_started","data":{...}}
data: {"step":"tool_completed","data":{...}}
data: {"step":"replanned","data":{...}}
data: {"step":"synthesis_completed","data":{...}}
data: {"step":"review_completed","data":{...}}
data: {"step":"run_completed","data":{...}}
data: [DONE]
```

## 目录结构

```text
FinSight-Agent/
├── backend/
│   ├── pom.xml
│   ├── src/main/
│       ├── java/com/zzy/finsight/
│       │   ├── auth/              # Bearer Token、密码和用户上下文支撑
│       │   ├── agent/             # Runtime、Planner、工具、状态、事件与恢复
│       │   ├── component/         # analysis/evaluation/marketdata/review 确定性组件
│       │   ├── config/            # CORS、LLM、异步执行器等配置
│       │   ├── controller/        # REST API 与 SSE 接口
│       │   ├── domain/stock/      # 股票领域模型、metric 指标定义和 reference 主档
│       │   ├── dto/               # API 请求响应、股票报告和导出 DTO
│       │   ├── infrastructure/    # Provider、MyBatis TypeHandler、序列化适配
│       │   ├── llm/               # OpenAI-compatible LLM 封装
│       │   ├── mapper/            # 纯 MyBatis Mapper 接口
│       │   ├── rag/               # PDF 解析、切片、embedding、ChromaDB 向量检索
│       │   ├── search/            # Tavily 搜索封装
│       │   └── service/           # Controller 面向的服务接口及 impl 实现
│       └── resources/
│           ├── application.yml
│           ├── financial-eval-set.json
│           ├── mapper/            # MyBatis XML SQL 与结果映射
│           └── db/                # Flyway 迁移、完整 schema 与手动升级脚本
│   └── src/test/resources/evaluation/ # 冻结评测集与版本化基线
├── frontend/                      # Vue 3 前端
├── docs/                          # 路线图、已实现能力、遗留问题、踩坑日志
└── README.md
```

## 快速启动

### 1. 准备 MySQL

先创建数据库：

```sql
CREATE DATABASE IF NOT EXISTS finsight
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;
```

本地连接配置直接维护在 `backend/src/main/resources/application.yml`：

```yaml
spring:
  datasource:
    url: jdbc:mysql://localhost:3306/finsight?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&useSSL=false&allowPublicKeyRetrieval=true
    username: root
    password: 123456

finsight:
  async:
    agent-threads: 8
    agent-queue-capacity: 32
    financial-provider-threads: 6
    financial-provider-queue-capacity: 24
    financial-provider-timeout: PT15S
  agent:
    max-turns: 8
    max-tool-calls: 12
    max-replans: 3
    max-evidence-recoveries: 2
    max-stagnant-turns: 3
    event-outbox-publish-interval-ms: 1000
    event-stream-poll-interval-ms: 1000
    event-outbox-claim-duration: PT30S
    event-outbox-immediate-grace: PT5S
    event-outbox-max-attempts: 8
    max-parallel-tools: 4
    timeout: PT180S
    tool-timeout: PT30S
```

Agent 和工具执行器都使用有界队列；队列满时拒绝新提交并记录 Micrometer 指标。单次工具和整体运行都有显式超时，不会无限占用线程。

数据库由 Flyway 自动管理：空库依次执行 V1～V7；V4 新增 Agent 轮次、工具调用和 AgentState Checkpoint，V5 增加单调 `lease_epoch` fencing，V6 增加 Planner 决策遥测、任务事件序号和事件 outbox，V7 增加 outbox claim/重试/死信状态以及 Planner 动作与 turn 的唯一关联。已有旧库通过 `baseline-version=1` 接管后执行增量迁移，`schema.sql` 保留为当前完整结构参考。

如需临时关闭自动迁移，需按顺序执行历史手动升级脚本；Research Agent 库至少依次执行 `upgrade-research-agent.sql`、`upgrade-durable-agent-turn-commit.sql`、`upgrade-agent-decision-outbox.sql` 和 `upgrade-agent-event-delivery.sql`，保证数据库结构与代码一致。

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
  "runtime": "bounded-research-agent"
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

只有 API Key 使用环境变量，不读取全局 `OPENAI_API_KEY`，避免影响用户的全局 OpenAI / cc-switch 配置。模型、地址、超时、启用开关、MySQL、Redis、ChromaDB、认证和 RAG 阈值等其他配置直接维护在 `application.yml`。

API Key 配置：

```powershell
$env:API_KEY="your_llm_api_key"
$env:TAVILY_API_KEY="your_tavily_key"
$env:TUSHARE_API_KEY="your_tushare_token"
```

在同一个 PowerShell 窗口设置 Key 后再启动后端；如果通过 IntelliJ IDEA 启动，需要把 `API_KEY`、`TAVILY_API_KEY`、`TUSHARE_API_KEY` 加到对应 Spring Boot Run Configuration 的 Environment variables，然后重启后端。不要把真实 Key 写入 `application.yml`、README、Git 或前端代码。

`InvestmentReportWriter` 会使用 SMART 模型增强确定性报告草稿。LLM 只能改写叙述，引用、指标公式和风险明细由 Java 覆盖回确定性内容；未配置 Key、请求失败或输出缺少必要章节时自动回退模板。报告源码中的 `FinSight generation-mode: llm` / `template-fallback` 可用于确认实际生成方式。离线 Judge 使用 `finsight.llm.judge-model`，未单独配置时默认与 SMART 模型同名，并记录模型、Token、结束原因和耗时。

使用 TuShare 时，还需要在 `application.yml` 中把 `finsight.market.tushare.enabled` 改为 `true`。TuShare provider 只在 `hybrid` / `web` 股票报告模式下调用；`document` 模式不会访问外部行情接口。

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
- 上传会重建当前登录用户的知识空间，不影响其他用户。

### 清空知识库

```http
POST /api/clear
```

该接口只清空当前登录用户的知识空间，不会删除其他用户的 PDF 或报告索引。

> 从旧版全局知识库升级时，原 `finsight_docs` 中未带 `knowledge_space` metadata 的记录不会自动归属任何用户；请重新上传 PDF，或在报告库重新执行“加入知识库”。

### 启动 Research Agent

创建任务前可先搜索或预解析证券：

```http
GET /api/securities/search?query=贵州茅台
GET /api/securities/600519/preview
```

名称搜索只返回本地主档能够确认的候选；代码受支持但主档缺少名称时，`preview` 会返回 `resolved=true`、`nameResolved=false`，不会伪造证券名称。前端必须让用户从候选中确认规范化代码，正式 Agent Run 仍会重新解析证券主体。

```http
POST /api/research-runs
Content-Type: application/json
Accept: text/event-stream
```

请求示例：

```json
{
  "ticker": "600519",
  "research_question": "最近两个季度毛利率变化的主要原因是什么？",
  "research_intent": "FINANCIAL_QUALITY",
  "thread_id": "optional",
  "as_of_date": "2026-08-12",
  "time_horizon": "2Y",
  "research_depth": "standard",
  "search_mode": "hybrid",
  "budget": {
    "max_turns": 8,
    "max_tool_calls": 12,
    "timeout_seconds": 180
  }
}
```

字段说明：

- `ticker`：普通 A 股或常见 ETF 的 6 位代码，也支持 `.SH` / `.SZ` 后缀。
- `research_question`：必填，决定计划、证据需求和工具选择。
- `research_intent`：可选，支持 `COMPREHENSIVE`、`FINANCIAL_QUALITY`、`VALUATION_RISK`、`ETF_TRACKING`、`EVENT_IMPACT`，默认 `COMPREHENSIVE`。意图只约束 Planner 研究重点；`FINANCIAL_QUALITY` 仅适用于普通股票，`ETF_TRACKING` 仅适用于 ETF。
- `as_of_date`、`time_horizon`：限定研究时点与观察区间。
- `research_depth`：支持 `quick`、`standard`、`deep`，映射到服务端预算上限。
- `search_mode`：支持 `document`、`hybrid`、`web`。
- `budget`：可选且只能收紧服务端预算。

Agent 重试与动态轨迹：

```http
POST /api/research-runs/{taskId}/retry
GET /api/research-runs/{taskId}/trace
GET /api/research-runs/{taskId}/events?afterSequence={sequence}
```

`events` 返回 `text/event-stream`，支持请求头 `Last-Event-ID: {sequence}`；服务端先回放该序号后的已提交事件，再接续实时流。前端主入口会在非终态连接意外中断时携带最后序号有限重连，重复事件仍由 `taskId + sequence` 幂等过滤。

旧 `POST /api/stock-reports` 继续接受原请求并转入同一 Runtime。历史报告的 Bad Case、回放和旧轨迹接口继续保留：

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

登录后可从报告库点击“独立研究页”，或直接访问前端路由：

```text
/reports/{reportId}
```

该页面仍通过现有归属校验 API 读取报告、同会话版本、任务回放和步骤日志，不创建跨用户读取入口。

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
- `agent_turn`：Planner 每轮结构化动作、观察摘要、Token 和耗时。
- `agent_tool_call`：工具名称、参数摘要、结果、重试次数和稳定错误分类。
- `agent_planner_call`：Planner 决策类型、请求/实际模型、结构合法性、Token、耗时及动作对应的 turn。
- `agent_event_outbox`：带任务内单调序号的版本化 Agent 事件，以及 claim、重试、下次投递和死信状态。
- `checkpoint`：带状态版本、轮次和请求上下文指纹的 AgentState 快照。
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

测试套件包含 `MySqlPersistenceIntegrationTest`：Docker 可用时会启动 MySQL 8.4，真实执行 Flyway V1→V7，并验证 Agent turn、tool call、Checkpoint、任务租约、Planner 遥测与 turn 关联、事件 outbox 和报告租户隔离；未启动 Docker 时该用例会明确跳过。

确定性离线评测与显式基线更新：

```powershell
cd backend
mvn.cmd "-Dtest=FinancialEvaluationRegressionTest" test
mvn.cmd "-Dtest=FinancialEvaluationRegressionTest" "-Dfinsight.eval.update-baseline=true" test
```

评测产物写入 `backend/target/evaluation/<runId>/results.json` 和 `summary.md`。Judge 与真实数据冒烟默认关闭，缺少 Key 时状态为 `SKIPPED`：

```powershell
cd backend
mvn.cmd "-Dtest=LlmJudgeEvaluationTest" "-Dfinsight.eval.judge.enabled=true" test
mvn.cmd "-Dtest=LiveProviderEvaluationTest" "-Dfinsight.eval.live.enabled=true" test
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
- 第一版是单体受约束 Agent，不引入 Supervisor、多 Agent、SQL/反射工具、交易执行、仓位建议或回测能力。
- 未配置真实 LLM 时 Planner 会明确标记 `DETERMINISTIC_FALLBACK`；该模式用于本地可运行和机制测试，不代表已经验证真实模型的规划质量。
- TuShare 真实 token、缓存、限速、接口权限错误提示仍需继续硬化；ETF `total_netasset` 展示单位也需真实数据复核。
- ETF 持仓、跟踪误差、申赎清单、普通股票行情图、高级技术指标、风险裁判、评测趋势管理页面和真实 MySQL 迁移集成实跑属于后续增强。事件 outbox 死信当前只有数据库状态，仍需运维告警和管理入口；按任务轮询续传也需多实例压力测试后再决定是否引入通知通道。
