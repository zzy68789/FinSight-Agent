# FinSight Research Agent 架构大改方案

更新时间：2026-08-12  
文档状态：P0 代码改造首版已实现；真实 LLM 动态规划、Reviewer 补证据集成链路和 Docker MySQL 中断恢复仍待验收

> 2026-08-12 实现记录：新 Agent Runtime、Planner、只读工具协议、增量证据、预算、持久化、恢复、新 API 和前端动态轨迹已经落地；旧固定 Workflow/Runner 已删除。后端全量测试 151 项通过、3 项按配置跳过，前端生产构建通过。本文后续 P1/P2 能力和 Step 13 中依赖真实 LLM、真实数据源、Docker 的验收项不属于本轮已完成范围。

## Summary

目标是把 FinSight 从 `StockResolve → DataSnapshot → MetricEngine → RiskAssessment → EvidenceCollect → Writer → Reviewer → Evaluation` 的固定报告工作流，改造成一个**受约束的单体 Research Agent Runtime**。LLM 根据自然语言研究问题生成计划，自主选择白名单工具，根据工具观察结果补充证据、调整计划并决定何时停止；Java 继续负责确定性金融计算、工具权限、预算、状态恢复、引用审查、合规审查和最终质量门禁。

第一版只改造执行内核，不引入多 Agent、自动交易、回测、仓位建议、Python 或新的数据库类型。`D:\Code\stock-trading` 只作为行情序列、技术指标、新闻采集、任务管理和模型版本管理的能力参考，不复制其交易执行、随机评分、模拟评测和反射调度实现。

完成后项目的准确定位是：

> FinSight 是面向 A 股与 ETF 的受约束投研 Research Agent。Agent 根据用户研究目标动态规划并调用金融数据、公告检索、RAG、指标计算和风险分析工具，在证据不足时自主补充检索与重规划；Java Runtime 负责运行预算、状态恢复、确定性计算以及引用与合规门禁。

## Step 1：确定“去工作流”的改造边界

本次要删除的是**写死的业务执行顺序**，不是删除所有运行控制代码。

### 删除或退役

- 删除 `component/workflow/StockReportWorkflow.java`，不再以固定方法顺序编排业务。
- 拆分 `StockReportRunner.java`，移除固定的证券解析、全量采集、指标计算、多空分析、写作和审查顺序。
- 停用 Writer 失败后只带反馈重写的固定 `for` 循环，改为根据结构化失败原因决定补证据、重新计算或重写。
- 停止让 `FinancialSnapshotBuilder` 每次无条件调用全部 Provider，由 Agent 选择数据工具。
- 将 `BullBearResearchAgent` 改名并降级为工具，避免把确定性规则组件描述为 Agent。

### 保留并泛化

- 保留 `StockCodeResolver`、`FinancialMetricEngine`、`FinancialRiskScorer` 和所有 `FinancialDataProvider` 的业务能力。
- 保留 `CitationReviewer`、`FinancialComplianceReviewer`、`FinancialEvaluator`，继续执行 fail-closed 最终门禁。
- 保留任务租约、心跳、恢复调度、checkpoint、SSE、步骤日志、报告复用和用户数据隔离。
- 保留 `FinancialEvidenceItem`、`FinancialMetricResult` 和 `FinancialSnapshot` 的证据模型，但允许快照随工具调用增量构建。
- 保留 Java `BigDecimal` 金融计算边界，LLM 不能计算或修正关键财务数字。

### 第一版明确不做

- 不做多个 Agent 固定串联或 Supervisor 多 Agent。
- 不做自动下单、券商登录、仓位建议、收益保证和回测平台。
- 不让 LLM 直接执行 SQL、反射调用 Spring Bean、构造类名或访问未注册工具。
- 不引入 MongoDB、Python、akshare、yfinance 或外部脚本回退。
- 不新增 LangGraph4j；第一版使用 Java 实现小型 Agent Runtime，继续复用现有 LangChain4j 模型接入。

## Step 2：新增自然语言研究入口

新增 `ResearchRequest`，让自然语言问题成为决定计划和工具选择的核心输入。建议第一版请求结构：

```json
{
  "ticker": "600519",
  "research_question": "贵州茅台最近两个季度毛利率变化的主要原因是什么？",
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

字段规则：

- `ticker`：第一版仍只研究单只股票或 ETF，降低状态和证据关联复杂度。
- `research_question`：必填，不再默认等同于“生成一份完整报告”。
- `as_of_date`：限定研究时点，防止新旧数据口径混用。
- `time_horizon`：限定行情、公告和财务数据观察区间。
- `research_depth`：只允许 `quick`、`standard`、`deep`，由服务端映射预算上限。
- `budget`：客户端只能在服务端上限内缩小预算，不能扩大系统配置的最大值。

接口迁移方式：

- 新增 `POST /api/research-runs`，使用 SSE 返回 Agent 运行事件。
- 保留 `/api/stock-reports` 作为兼容入口，将旧请求转换成带默认问题的 `ResearchRequest` 后进入新 Runtime。
- 新增 `GET /api/research-runs/{taskId}/trace` 和 `POST /api/research-runs/{taskId}/retry`。
- 前端完成迁移后，不再让旧接口启动 `StockReportRunner`。
- 等兼容回归完成后删除旧 Runner，不长期维护两套执行内核。

## Step 3：建立 Agent 领域模型和包结构

新增以下包，避免继续把 Agent Runtime 放在 `component.workflow`：

```text
com.zzy.finsight.agent
├─ runtime
│  ├─ ResearchAgentRuntime
│  ├─ DurableAgentRunner
│  ├─ AgentRunRecoveryScheduler
│  └─ AgentBudgetGuard
├─ planning
│  ├─ ResearchPlanner
│  ├─ PlannerModelClient
│  ├─ ResearchPlan
│  └─ AgentAction
├─ tool
│  ├─ ResearchTool
│  ├─ ResearchToolRegistry
│  ├─ ToolPolicyGuard
│  ├─ ToolContext
│  └─ ToolResult
├─ memory
│  ├─ AgentState
│  ├─ EvidenceMemory
│  └─ AgentCheckpointCodec
└─ event
   ├─ AgentEvent
   ├─ AgentEventListener
   └─ AgentTraceReader
```

核心对象职责：

| 对象 | 职责 |
| --- | --- |
| `ResearchPlan` | 保存研究目标、待验证假设、所需证据类型、已完成项和未解决问题 |
| `AgentAction` | 只允许 `CALL_TOOL`、`CALL_TOOLS_PARALLEL`、`REPLAN`、`SYNTHESIZE`、`STOP_INSUFFICIENT_EVIDENCE` |
| `AgentState` | 保存当前计划、轮次、工具预算、证据 ID、指标 ID、审查反馈和停止原因 |
| `ResearchTool<I, O>` | 定义工具名称、输入类型、权限策略、超时和执行方法 |
| `ToolResult<O>` | 返回结构化结果、证据引用、状态、错误码、是否可重试和结果摘要 |
| `ToolPolicyGuard` | 验证工具是否注册、参数是否合法、预算是否足够、是否允许并行 |
| `DurableAgentRunner` | 负责租约、心跳、checkpoint、异常恢复和最终状态落库 |
| `ResearchAgentRuntime` | 执行 plan → act → observe → replan/stop 通用循环，不包含具体金融步骤 |

`AgentState` 只保存证据 ID 和短摘要；完整网页、公告、快照和工具结果落库，避免每轮把所有原始内容重复塞入 Prompt。

## Step 4：实现受约束的 Agent 执行循环

`ResearchAgentRuntime` 每轮执行以下通用逻辑：

1. 首轮由 `ResearchPlanner` 根据请求、工具目录和研究边界生成 `ResearchPlan`。
2. Planner 基于当前计划、预算和观察摘要返回一个结构化 `AgentAction`。
3. `ToolPolicyGuard` 校验工具名称、参数、权限、重复调用和预算。
4. Runtime 串行或受控并行执行工具，将证据和观察结果写入 `EvidenceMemory`。
5. 每轮结束保存 `AgentState` checkpoint，并发布 SSE 事件。
6. Planner 根据新增证据继续调用工具、执行 `REPLAN` 或选择 `SYNTHESIZE`。
7. 进入综合阶段后生成报告，再执行确定性最终门禁。
8. 门禁失败时，根据错误类型回到 Agent 或终止，模型不能自行标记通过。

第一版控制规则直接配置在 `application.yml`：

| 控制项 | 默认规则 |
| --- | --- |
| 最大 Agent 轮次 | 8 轮 |
| 最大工具调用数 | 12 次 |
| 最大 Replan 次数 | 3 次 |
| 单轮最大并行工具数 | 4 个 |
| 整体运行超时 | 180 秒 |
| 单个外部工具超时 | 30 秒 |
| 工具瞬时失败重试 | 最多 2 次，间隔 1 秒；非幂等工具禁止自动重试 |
| Planner 非法结构重试 | 最多修正 2 次，仍非法则 `FAILED_INVALID_ACTION` |
| 最终报告重写 | 最多 2 次，超过后 fail-closed |
| 无新增证据停止 | 连续 2 轮没有新增有效证据时停止为 `INSUFFICIENT_EVIDENCE` |

同一个 `toolName + canonicalArgumentsHash` 默认只执行一次。工具调用失败且标记 `retryable=true` 时，Runtime 才允许按重试规则再次执行。

## Step 5：把现有能力包装成第一批白名单工具

P0 只包装现有能力，不同时开发大量新金融功能。

| 工具名称 | 复用组件 | 输入 | 主要输出 |
| --- | --- | --- | --- |
| `resolve_security` | `StockCodeResolver` | 代码或名称 | `StockSubject` |
| `get_financial_statements` | `TushareMarketDataProvider` | 标的、报告期、字段范围 | 财务证据 ID |
| `get_market_snapshot` | `PublicMarketDataProvider`、TuShare 行情能力 | 标的、截止日 | 行情与估值证据 ID |
| `retrieve_uploaded_reports` | `UploadedReportProvider`、RAG | 标的、问题、报告期 | 文档片段和证据 ID |
| `search_public_evidence` | `EnhancedSearchService`、Tavily | 查询、时间范围 | 网页证据 ID |
| `calculate_financial_metrics` | `FinancialMetricEngine` | 快照 ID、指标集合 | `FinancialMetricResult` ID |
| `assess_financial_risk` | `FinancialRiskScorer` | 指标和证据 ID | `FinancialRiskAssessment` |
| `build_bull_bear_cases` | 由 `BullBearResearchAgent` 改造 | 指标、风险、证据 ID | 结构化多空条件 |
| `check_evidence_coverage` | 从引用审查规则中提取 | 研究计划、证据 ID | 缺失证据类型和覆盖率 |

改造规则：

- Provider 不直接暴露给 Planner，Planner 只能看到稳定、面向研究语义的工具名称。
- 所有工具输入使用 DTO/record 并通过 Jackson 严格反序列化，未知字段和非法枚举直接拒绝。
- 所有工具结果必须包含 `status`、`summary`、`evidenceRefs`、`errorCode` 和 `retryable`。
- 采集工具可以增加证据；计算工具只能读取证据并生成派生结果，不能覆盖原始值。
- 每个工具声明 `READ_ONLY`、`IDEMPOTENT`、`ALLOW_PARALLEL` 和超时策略。
- 第一版工具全部是只读研究工具，注册交易执行类时由架构测试直接失败。

## Step 6：把快照改成增量证据账本

当前 `FinancialSnapshotBuilder` 在报告生成前一次性构建完整快照。改造后由 `EvidenceMemory` 随工具调用增量维护：

- 采集工具成功后先持久化 `stock_evidence_item`，再把证据 ID 加入 `AgentState`。
- `FinancialSnapshot` 改为某一时点的证据视图，不再等同于“所有 Provider 已执行”。
- `calculate_financial_metrics` 只读取当前快照内满足口径的证据。
- 新证据到达后，如果参与计算的输入变化，将原指标结果标记为过期并重新计算。
- 报告生成前冻结最终证据快照，生成稳定的 `dataSnapshotHash`。
- 工具返回的网页正文、公告全文和模型原始输出不直接放入 SSE，只返回摘要和可查询 ID。

`generationContextHash` 必须增加以下字段，否则不同问题可能错误复用同一报告：

- 规范化后的 `researchQuestion`。
- `asOfDate`、`timeHorizon` 和 `researchDepth`。
- 最终 `dataSnapshotHash`。
- Planner、toolset、policy、Writer、Reviewer 和指标公式版本。

## Step 7：重构审查回流和停止条件

最终门禁仍由 Java 决定，审查结果改为结构化路由原因：

| 失败原因 | Runtime 动作 |
| --- | --- |
| `EVIDENCE_INSUFFICIENT` | 预算充足时回到 Planner，由 Agent 选择补充检索工具 |
| `EVIDENCE_CONFLICT` | 调用证据核对或补充来源；不能通过改写掩盖冲突 |
| `NUMERIC_MISMATCH` | 重新读取证据和指标，禁止让 LLM自行修正数字 |
| `CITATION_MISSING` | 证据存在时重写引用；证据不存在时回到 Planner |
| `COMPLIANCE_VIOLATION` | 仅重写违规表达，不增加投资建议内容 |
| `QUALITY_GATE_FAILED` | 按失败维度决定重写或终止，不能跳过 Evaluation |
| `BUDGET_EXHAUSTED` | 立即停止，输出证据不足说明，不生成伪完整报告 |

最终报告只有同时通过以下检查才能保存为 `PASS`：

1. 数字能映射到 `FinancialMetricResult` 或原始证据。
2. 事实结论存在有效证据引用。
3. 报告期和研究截止日期一致。
4. 合规审查通过并包含免责声明。
5. `FinancialEvaluator` 线上门禁通过。

## Step 8：改造数据库、checkpoint 和运行状态

通过新的 Flyway migration 增量演进，不重写 `V1__init.sql`；迁移完成后同步完整 `schema.sql` 和必要的手动升级 SQL。所有新增字段必须带中文 `COMMENT`。

### 复用 `research_task`

`research_task.query` 直接保存研究问题，新增：

- `runtime_type`：固定为 `RESEARCH_AGENT`。
- `turn_count`：已执行 Agent 轮次。
- `tool_call_count`：已消耗工具调用次数。
- `stop_reason`：完成、证据不足、预算耗尽、非法动作或运行失败原因。
- `planner_version`、`toolset_version`、`policy_version`：运行版本追踪。
- `completed_at`：任务终止时间。

### 新增 `agent_turn`

记录 `task_id`、`turn_no`、`phase`、`action_type`、`action_json`、`observation_summary`、`status`、输入/输出 Token、耗时和创建时间。同一任务的 `turn_no` 建唯一索引。

### 新增 `agent_tool_call`

记录 `task_id`、`turn_id`、`call_id`、`tool_name`、`arguments_hash`、`arguments_json`、`result_json`、`status`、`attempt_no`、`duration_ms`、`error_code`、`error_message`、开始和结束时间。按 `task_id + tool_name + arguments_hash` 建查询索引，用于幂等复用和重复调用判断。

### 扩展现有表

- `stock_evidence_item` 增加 `tool_call_id` 和稳定 `evidence_key`，关联证据来源工具并支持去重。
- `checkpoint` 增加 `state_version`、`turn_no` 和 `context_hash`，保存可恢复的 `AgentState`。
- `agent_step_log` 第一阶段继续保存面向前端的摘要事件，完成新 TraceReader 后再评估是否退役。

任务状态统一为：

```text
CREATED
  -> PLANNING
  -> RUNNING_TOOLS
  -> REPLANNING
  -> SYNTHESIZING
  -> REVIEWING
  -> COMPLETED | INSUFFICIENT_EVIDENCE | FAILED | CANCELLED
```

恢复调度仍按当前租约和心跳机制执行。恢复后从最近一个完整 turn checkpoint 继续，不重复执行已经成功且可幂等复用的工具调用。

## Step 9：调整 SSE、Trace 和前端研究工作台

新 SSE 使用 Agent 事件语义，不再假设固定阶段顺序：

- `run_created`
- `plan_created`
- `tool_started`
- `tool_completed`
- `evidence_updated`
- `replanned`
- `synthesis_started`
- `review_completed`
- `run_completed`
- `run_stopped`
- `error`

所有事件统一包含 `taskId`、`threadId`、`eventId`、`turnNo`、`timestamp` 和 `status`。工具事件只发送工具名、参数摘要、耗时、结果状态和证据 ID，不发送 API Key、完整 Prompt 或未脱敏原文。

前端第一版只做必要改造：

- 研究入口增加必填的自然语言问题、研究截止日和研究深度。
- 运行区展示当前研究计划、已验证假设、待补证据和剩余预算。
- 时间线由固定节点改为动态 turn/tool-call 列表。
- 工具调用可展开查看参数摘要、状态、耗时、证据引用和错误原因。
- 报告页继续复用现有正文、证据、版本对比、ETF 图表和 Bad Case 能力。
- 旧 SSE 字段在兼容入口中做适配，不直接破坏现有 `/api/stock-reports` 消费代码。

## Step 10：分阶段迁移旧代码

### P0-1：建立 Runtime 骨架

- [x] 新增 Agent 领域对象、工具协议、注册表、预算守卫和 Planner 降级测试。
- [x] 通过 Runtime 单测证明主动停止、预算边界和连续无新增证据停止；恢复的真实数据库中断测试仍列在 P0-4。

### P0-2：包装现有金融工具

- [x] 将证券解析、数据采集、RAG、指标、风险和多空能力包装成白名单工具。
- [x] Agent 主链不再调用 `FinancialSnapshotBuilder` 的全量 Provider fan-out，增量组装与质量校验迁入 `EvidenceMemory`。
- [x] 建立工具结果到 `FinancialEvidenceItem` 的统一转换。

### P0-3：接入 Planner 和新 API

- [x] 新增 `ResearchPlanner`，要求模型只返回结构化计划和动作。
- [x] 新增 `/api/research-runs`、SSE 事件、Trace 和前端研究问题入口。
- [x] 旧 `/api/stock-reports` 转发到新 Runtime，不再进入旧工作流。

### P0-4：迁移可靠性基础设施

- [x] 泛化 checkpoint、恢复调度、步骤日志和报告缓存校验。
- [x] 更新 `generationContextHash`，加入研究问题和 Agent 版本。
- [ ] 租户隔离已有既有回归、工具调用已有稳定参数摘要；跨进程中断恢复、并发合并和真实 MySQL 幂等测试仍待完成。

### P0-5：删除旧工作流

- [x] 删除 `StockReportWorkflow`。
- [x] 删除旧 `StockReportRunner` 的固定执行代码。
- [x] 删除旧恢复/进度/阶段 Checkpoint 组件；历史轨迹查询以 `LegacyStockReportTraceReader` 暂留兼容。
- [x] 删除只为固定 Writer/Reviewer 循环服务的 checkpoint 状态类和测试。
- [x] 更新架构测试，`agent.runtime` 不直接依赖具体 Provider 实现。

## Step 11：吸收 stock-trading 中适合的能力

这些能力在 Agent Runtime 稳定后作为 P1 工具扩展，不与 P0 内核改造同时开发。

| 参考能力 | FinSight 落点 | 改造要求 |
| --- | --- | --- |
| 日线聚合周线/月线 | `GetMarketSeriesTool`、MySQL 行情表 | 增量同步、来源和截止日可追踪，不引入 MongoDB |
| ta4j RSI/MACD/均线/布林带/OBV | `TechnicalIndicatorTool` | 返回结构化指标、参数、版本和行情证据 ID，不输出买卖信号 |
| 新闻并行采集与去重 | `CollectNewsEventsTool` | 统一来源、发布时间、去重键、有效期和失败原因 |
| DJL 情绪推理 | `NewsSentimentTool` | 只做可选本地推理，显式记录模型是否加载和规则降级，不声称完成训练 |
| 策略权重与版本 | `AgentArtifactRegistry` | 管理 Planner、Prompt、toolset、policy 和模型版本 |
| 动态任务与通知 | 定时研究刷新、报告完成/数据源降级通知 | 使用类型化 Handler、线程池、执行记录和锁，禁止 `new Thread` 与 Bean 反射 |

明确不借鉴：

- `StockSelector` 中基于 `Random` 的预测和“推荐买入”文案。
- 自动登录、验证码识别、交易执行和订单同步。
- 模拟数据回退后继续展示成真实结果。
- 使用固定 `±0.05` 样本收益计算 Sharpe。
- 没有时间切分、交易成本和数据泄漏检查的绩效结论。

## Step 12：后续优化

P0 全部完成并通过真实任务验证后，再按顺序扩展：

1. 普通股票日/周/月行情和 `TechnicalIndicatorTool`。
2. 公告/新闻事件与可选 DJL 情绪工具。
3. 自选股定时研究、数据变化触发和站内通知。
4. Agent Artifact 版本管理和运行质量趋势。
5. 最后评估 Specialist Agent-as-Tool；只有单 Agent 工具协议、预算、恢复和评测稳定后才引入 Supervisor。

多 Agent 不应作为第一阶段目标。多个 LLM 固定串联仍然是工作流，并且会显著增加成本、非确定性和故障定位难度。

## Step 13：验证

### 单元测试

- `ResearchPlannerTest`：不同研究问题产生不同证据需求；非法结构可修正，超过次数后失败。
- `ResearchToolRegistryTest`：只允许注册工具，重名和交易类工具注册失败。
- `ToolPolicyGuardTest`：验证参数、预算、重复调用、并行限制和未授权工具。
- `ResearchAgentRuntimeTest`：覆盖调用工具、并行工具、Replan、主动停止、证据不足和预算耗尽。
- `AgentCheckpointCodecTest`：状态版本兼容、损坏 checkpoint 拒绝恢复。
- `AgentBudgetGuardTest`：轮次、工具数、超时和重写次数均不可突破。

### 集成测试

- 使用 Fake Planner 和 Mock Tool，验证完整 plan → act → observe → synthesize → review 链路。
- 同一股票的“分析盈利能力”和“分析短期风险”必须产生不同工具调用序列。
- Reviewer 返回 `EVIDENCE_INSUFFICIENT` 后必须调用补证据工具，不能只重写原文。
- Planner 请求不存在的工具时，Runtime 拒绝执行并记录 `UNAUTHORIZED_TOOL`。
- 中途进程异常后，从最近完整 turn 恢复，不重复采集已成功证据。
- 两个用户运行相同问题时，私有文档和证据继续保持 owner 隔离。
- 最终引用、合规或 Evaluation 任一失败时，报告不能保存为 `PASS`。

### 兼容与构建验证

```powershell
cd D:\Code\FinSight-Agent\backend
mvn.cmd test

cd D:\Code\FinSight-Agent\frontend
npm.cmd run build
```

同时验证：

- `/api/research-runs` 能持续输出动态 Agent SSE 事件。
- `/api/stock-reports` 兼容入口仍可被旧前端消费。
- 报告库、报告详情、版本对比、PDF 上传、RAG 和 Bad Case 不回归。
- 真实 LLM 未配置时，Fake/本地 fallback 必须明确标记为降级，不能伪装成真实 Agent 决策质量。

### Agent 改造完成标准

以下条件全部满足后，才将项目对外描述为 Research Agent：

1. 相同标的面对不同研究问题会生成不同研究计划。
2. LLM 会选择调用哪些工具，而不是每次固定全量调用。
3. 工具观察结果会改变下一步动作或研究计划。
4. 证据不足时会补充检索或停止，而不是只让 Writer 换一种说法。
5. Agent 能在证据充分时主动选择 `SYNTHESIZE`。
6. 预算耗尽、工具不可用或证据冲突时会明确停止。
7. Trace 能解释每轮计划、工具选择、观察结果和停止原因。
8. 任何 Agent 动作都不能绕过数字、引用、合规和 Evaluation 门禁。

在这些验收条件完成前，简历和文档继续使用“正在改造为 Research Agent”，不要提前声称已具备动态规划、工具自主选择和证据补充回流。
