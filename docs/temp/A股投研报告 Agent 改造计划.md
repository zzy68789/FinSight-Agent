# A股投研报告 Agent 改造计划

## Summary

目标是把 `D:\Code\zy-agent` 从通用调研 Agent 改成 **A股财报/投研报告分析系统**：用户输入股票代码，系统拉取或复用财报/行情/新闻证据，生成带引用、可复核、可追踪的投资分析报告。底座保留 Java + Spring Boot + LangGraph4j + RAG + SSE + 报告库；参考 [FinSight](https://github.com/kkkano/FinSight) 的金融多智能体、证据账本、报告索引、评测体系，参考 [do-not-miss](https://github.com/warmazxy-maker/do-not-miss-github) 的 Trace、Bad Case、确定性评分思想。

默认选择：**A股优先**、**基于 `zy-agent` 改造**。报告统一标注“仅作研究辅助，不构成投资建议”。

## MVP 阶段

### Step 1：新增股票分析入口

新增 `POST /api/stock-reports`，请求体：

```json
{
  "ticker": "600519",
  "thread_id": "optional",
  "report_period": "latest",
  "search_mode": "hybrid"
}
```

返回继续使用 SSE，事件至少包含：`stock_resolve`、`data_snapshot`、`metric_engine`、`evidence_collect`、`writer`、`reviewer`、`done`。前端在 Run 工作区新增“股票代码分析”模式，输入 `600519` 后自动触发报告生成。

### Step 2：新增金融数据快照模型

新增金融模块，不直接把证据塞成字符串。核心对象：

- `StockSubject`：股票代码、交易所、公司名、行业。
- `FinancialEvidenceItem`：来源类型、来源名、URL、页码、报告期、指标名、原始值、标准化值、片段、置信度、`asOf`。
- `FinancialSnapshot`：一次报告生成时使用的全部行情、财报、新闻、网页证据快照。
- `FinancialMetricResult`：Java 计算出的财务指标和计算公式说明。

数据库新增最小表：`stock_analysis_snapshot`、`stock_evidence_item`、`stock_metric_result`。所有字段写中文 `COMMENT`。旧 `report` 表继续保存最终 Markdown 报告。

### Step 3：股票代码解析与数据源适配

A股代码规则固定：

- `6xxxxx` -> `.SH`
- `0xxxxx`、`2xxxxx`、`3xxxxx` -> `.SZ`
- 已带 `.SH/.SZ` 时直接保留

MVP 使用 `FinancialDataProvider` 接口封装数据源，先实现两个适配器：

- `UploadedReportProvider`：复用现有 PDF 上传/RAG 能力，支持用户上传年报、季报。
- `PublicMarketDataProvider`：拉取公开行情、公司基础信息和新闻摘要；失败时不阻断流程，只在快照中记录 `DATA_MISSING`。

不要在 MVP 引入 Python、akshare、OpenSearch、pgvector 或复杂调度。

### Step 4：确定性财务指标引擎

新增 `FinancialMetricEngine`，只用 Java `BigDecimal` 计算，不让 LLM 计算关键数字。MVP 指标固定为：

- 营收同比
- 毛利率
- 净利率
- ROE
- 资产负债率
- 经营现金流 / 净利润

缺少分子或分母时返回 `MISSING_INPUT`，报告中显示“数据缺失”，禁止模型补写。

### Step 5：金融 Agent 工作流

新增一条股票报告工作流，不替换现有通用调研链路。节点顺序：

```text
StockResolve
  -> DataSnapshot
  -> MetricEngine
  -> EvidenceCollect
  -> InvestmentWriter
  -> CitationReviewer
  -> END / Retry
```

复用现有 [ResearchGraphFactory](D:/Code/zy-agent/backend/src/main/java/com/zzy/drai/agent/graph/ResearchGraphFactory.java) 的 LangGraph4j 思路，但金融链路独立建类，避免污染通用 `Researcher/Writer/Reviewer`。

### Step 6：报告模板与引用规则

MVP 报告固定 8 个章节：

1. 公司概况
2. 核心业务与行业位置
3. 财务表现
4. 盈利能力与现金流
5. 行情与估值观察
6. 新闻与催化因素
7. 主要风险
8. 结论与后续观察点

每个包含数字的结论必须关联 `FinancialEvidenceItem`。报告底部输出“引用与数据快照”，列出来源、报告期、页码/URL、片段。

### Step 7：审查器升级

新增 `CitationReviewer`，PASS 条件：

- 所有财务数字都能在 `FinancialSnapshot` 或 `FinancialMetricResult` 找到。
- 数值误差不超过 0.5% 相对误差，或绝对误差不超过 0.01。
- 报告期不能混用；混用时必须显式说明。
- 至少有 3 条有效证据；否则返回 `FAIL: EVIDENCE_INSUFFICIENT`。

失败后回到 `EvidenceCollect` 或 `InvestmentWriter`，最多重试 2 次。

## 后续优化方向

### P1：FinSight 风格金融能力扩展

把 MVP 单链路拆成轻量多 Agent：价格、新闻、基本面、技术面、风险、深度搜索。先串行实现，再考虑并行组。前端增加股票报告页：财务指标卡、证据列表、风险卡、报告时间线。

### P1：评测体系

新增 `financial-eval-set.json`，先做 10 个 A股样例。指标参考 FinSight RAG Quality V2：

- `claim_support_rate`
- `unsupported_claim_rate`
- `contradiction_rate`
- `numeric_consistency_rate`
- `citation_hit_rate`
- `keypoint_coverage`

每次改 Prompt、检索或 Reviewer 后都跑评测。

### P1：Bad Case 与报告回放

在报告页增加“数字错 / 引用错 / 逻辑错 / 信息过期”反馈。反馈保存后可回放本次 `snapshot + evidence + metric + writer output + reviewer result`，用于定位是数据源、检索、计算、生成还是审查问题。

### P2：更完整的产品化

后续再做组合监控、价格/新闻/风险预警、行业横向对比、同行估值区间、ECharts 图表、报告收藏与导出。不要在 MVP 做荐股、自动交易、回测平台或实盘策略。

## Test Plan

- 单元测试：股票代码标准化、财务指标计算、缺失数据处理、数值误差判断、引用校验。
- 集成测试：Mock `FinancialDataProvider`，输入 `600519` 后生成报告、保存快照、保存引用、SSE 顺序正确。
- 回归测试：现有通用 `/api/chat`、报告库、PDF 上传不受影响。
- 前端验证：输入股票代码触发 SSE，报告页能展示章节、引用、审查状态。
- 评测验证：MVP 至少 10 个样例，数值一致性和引用命中率作为硬门控。

## Assumptions

- 你只发来了一个唯一外部项目链接：`kkkano/FinSight`；另外两个链接目前重复，计划先按 `FinSight + do-not-miss + zy-agent` 综合。
- `FinSight` 是 MIT License，可学习和少量复用设计；`do-not-miss-github` 未看到 License，默认只借鉴架构思想，不复制代码。
- MVP 先做 A股中文投研报告，不做美股、不做自动交易、不做正式投资建议。
- 技术栈不迁移到 Python/FastAPI/React，继续以 `zy-agent` 的 Java/Spring/Vue 为主。
