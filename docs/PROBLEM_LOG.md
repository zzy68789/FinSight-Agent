# do not miss 问题日志

这份文档只记录明确指定要沉淀的问题。每条记录保持同一结构：发生了什么、原因、解决方式、结果。

## 001. 通用调研 Agent 不能直接输入 A 股股票代码生成投研报告

### 发生了什么

用户希望直接输入 A 股股票代码生成投研报告，但原有系统只有通用研究入口 `/api/chat`，无法按股票代码进入金融报告链路。

### 原因

原 `/api/chat` 面向通用研究问题，缺少股票代码解析、金融数据快照、财务指标和引用审查语义。即使通过 Prompt 拼接股票问题，也无法形成可复核的投研报告流程。

### 解决方式

新增独立股票报告链路 `/api/stock-reports`，并新增 `com.zzy.drai.financial` 包。工作流拆为 `StockResolve -> DataSnapshot -> MetricEngine -> EvidenceCollect -> Writer -> Reviewer`，同时保留通用调研链路不变。

### 结果

A 股投研报告入口与通用调研入口解耦，后端 69 个测试通过。

## 002. 财务数字如果交给 LLM 计算，容易出现编造、口径不一致或无法复核

### 发生了什么

投研报告需要展示营收、利润率、ROE、资产负债率和现金流等关键指标，但如果直接让 LLM 推导数字，报告内容容易出现编造或口径不一致。

### 原因

LLM 对关键财务指标没有确定性约束，且原系统只把证据作为文本片段传入，没有结构化指标输入和固定公式。

### 解决方式

新增 `FinancialMetricEngine`，用 Java `BigDecimal` 计算营收同比、毛利率、净利率、ROE、资产负债率、经营现金流 / 净利润。缺少分子或分母时返回 `MISSING_INPUT`，不让模型补写关键数字。

### 结果

财务指标变成可复核的确定性计算结果，新增指标单元测试覆盖正常计算和缺失输入场景。

## 003. 公开行情、新闻或财报数据源不可用时，流程可能被外部依赖阻断

### 发生了什么

本地演示或开发环境中，金融 API、Tavily、ChromaDB 或已上传 PDF 可能缺失，股票报告流程容易被外部数据源失败中断。

### 原因

早期实现更偏向强依赖外部 API，没有把数据不可用视为可追踪的证据状态。

### 解决方式

`PublicMarketDataProvider` 捕获失败并写入 `DATA_MISSING` 证据。报告中明确显示缺失项，不输出荐股或估值判断。

### 结果

MVP 在外部数据失败时仍可跑通，缺失项可追踪，流程不会因为单个 provider 不可用直接中断。

## 004. Bad Case 缺少可回放的快照与证据绑定

### 发生了什么

计划要求后续能定位“数字错 / 引用错 / 逻辑错 / 信息过期”等 Bad Case，但原报告库只保存最终报告。

### 原因

缺少金融 snapshot、evidence、metric 与反馈之间的绑定关系，无法定位错误来自数据、指标、撰写还是引用审查。

### 解决方式

新增 `stock_bad_case_feedback`，反馈时绑定 `snapshot_json`；新增 `/api/stock-reports/{taskId}/replay`，用于查看 snapshot、evidence 和 metric。

### 结果

前端新增四类反馈按钮和回放入口，后续可以基于快照持续优化评测与报告质量。

## 005. PowerShell 执行多个 Maven 测试类时逗号会被解析成参数列表

### 发生了什么

在 PowerShell 中直接执行 `mvn.cmd -Dtest=A,B test` 会导致命令失败。

### 原因

PowerShell 对未加引号的逗号参数有特殊解析，会把它拆成参数列表，而不是按 Maven Surefire 期望的单个 `-Dtest` 字符串传入。

### 解决方式

给 `-Dtest` 参数加引号，例如 `mvn.cmd "-Dtest=StockCodeResolverTest,FinancialMetricEngineTest,CitationReviewerTest,StockReportControllerTest" test`。

### 结果

验证命令稳定执行，确认测试先因缺类失败，补实现后 10 个目标测试通过。

## 006. 外部参考项目更正后需要重新判断可迁移亮点

### 发生了什么

用户更正了两个外部参考项目，需要重新判断哪些能力适合借鉴到当前 A 股投研 Agent。

### 原因

之前参考项目不对应用户真正想看的项目，不能继续沿用旧结论。新项目分别偏 Python 金融团队工作流和 Java 多 Agent 财报报告。

### 解决方式

新增 `docs/外部金融项目借鉴执行计划.md`，只借鉴多角色、fan-out、风险评分、合规 issue、可视化呈现，不引入 Python/yfinance、SEC、美股自动交易或仓位建议。

### 结果

形成 P0/P1/P2 执行计划，并完成 P0/P1 核心实现，借鉴边界明确。

## 007. 股票数据源串行采集会拖慢 SSE 首屏反馈

### 发生了什么

股票数据源采集如果串行执行，后续接入更多 provider 后会拖慢 SSE 首屏反馈。

### 原因

原 `FinancialSnapshotBuilder` 按 provider 顺序采集，缺少每个数据源的阶段耗时和失败状态。

### 解决方式

`FinancialSnapshotBuilder` 改为 provider fan-out 并行，`FinancialSnapshot` 增加 `stageResults`，前端展示每个数据源状态、耗时和证据数。

### 结果

新增并行测试证明两个慢 provider 总耗时低于串行，数据源失败也能保留降级证据。

## 008. 报告缺少金融合规约束

### 发生了什么

报告只有引用审查，缺少“不能保证收益、必须提示风险”的金融合规约束。

### 原因

`CitationReviewer` 关注证据和数字口径，不负责判断保证收益、内幕信息、免责声明等合规文本。

### 解决方式

新增 `FinancialComplianceReviewer` 和 `FinancialComplianceReviewResult`。Reviewer payload 返回 `compliance`，最终通过条件改成引用审查和合规审查都 PASS。

### 结果

前端新增合规状态和 issue 列表，后端新增 4 个合规测试，报告边界更清晰。

## 009. 风险评分把“未见重大利空”误判为负面风险

### 发生了什么

风险评分中“未见重大利空”被错误识别成负面风险。

### 原因

简单关键词命中先匹配了“利空”，没有处理中文否定和中性语义。

### 解决方式

`FinancialRiskScoringService` 调整为优先匹配“平稳、中性、未见重大利空”等中性或正向语义，再识别负面词。缺失证据仍按中性偏谨慎给分。

### 结果

风险评分测试通过，报告能输出五维风险评分和原因，避免该类假阳性风险。

## 010. `financial-eval-set.json` 只有样例定义，不能真正约束报告质量

### 发生了什么

项目已有 `financial-eval-set.json`，但它只是样例定义，不能自动评价生成报告。

### 原因

缺少加载评测集、计算指标、接入股票报告收尾阶段的评测 Module。

### 解决方式

新增 `FinancialEvaluationService`、评测结果模型和 `evaluation` SSE 事件。命中默认样例时，把评测结果纳入最终状态。

### 结果

评测指标可在前端和日志中展示，报告质量具备可回归基础。

## 011. 股票代码能解析交易所，但公司名称和行业仍显示“待识别”

### 发生了什么

股票代码能解析到交易所，但公司名称和行业仍显示“待识别”。

### 原因

没有稳定本地主档，公开搜索失败时公司主体信息也会缺失。

### 解决方式

新增 `AShareCompanyDirectory` 和 `AShareMasterDataProvider`，为 10 个评测样例股票输出公司名称、行业和 `LOCAL_CONTEXT` 证据。只补公司主档，不冒充实时行情或估值数据。

### 结果

评测样例具备稳定主体识别，股票解析和 provider 单测通过。

## 012. 长流程任务和 provider 并发使用无界线程池或默认公共线程池

### 发生了什么

长流程任务和 provider 并发使用无界线程池或默认公共线程池，后续并发任务增加时资源不可控。

### 原因

`ResearchTaskService`、`StockReportService` 内部使用 `newCachedThreadPool()`，`CompletableFuture.supplyAsync()` 未指定执行器。

### 解决方式

新增 `AsyncExecutionConfig`，注入 `workflowExecutor` 和 `financialProviderExecutor`，并用环境变量控制线程数。

### 结果

通用研究、股票报告和金融 provider fan-out 改为 Spring 管理的有界执行器，快照并行测试和相关目标测试通过。

## 013. TuShare API 已获取，但项目缺少授权行情 provider

### 发生了什么

用户已经获取 TuShare API，但项目没有单独证券行情 API Key 配置和授权行情 provider。

### 原因

原金融公开数据只通过 `PublicMarketDataProvider` 复用 Tavily 搜索摘要，无法把 TuShare 财务和行情字段转成可复核证据。

### 解决方式

新增 `TushareMarketDataProvider` 和 `drai.market.tushare.*` 配置，支持 `DRAI_TUSHARE_API_KEY` / `DRAI_MARKET_API_KEY`，并把 `income`、`balancesheet`、`cashflow`、`daily_basic` 转成金融证据。

### 结果

目标测试 4 个通过，真实 token 由用户本地配置后联调，API Key 不进入前端或仓库。

## 014. 输入 `588200` 分析股票时前端只显示“网络异常”

### 发生了什么

点击“分析股票”输入 `588200` 后，前端只显示“网络异常”，浏览器 Network 中 `/api/stock-reports` 返回 500。

### 原因

`588200` 属于 5 开头的沪市基金 / ETF 类代码，当前 `StockCodeResolver` 只支持普通沪深 A 股股票。工作流快速抛出业务异常后，`SseService.completeWithError` 会让 SSE 首包变成 HTTP 500。

### 解决方式

保持 MVP 边界不扩展 ETF / 基金报告，改为发送 `error` SSE 事件并在前端读取后端 message，同时把解析器错误文案改为“当前仅支持沪深 A 股普通股票代码”。

### 结果

`StockReportControllerTest,StockCodeResolverTest` 8 个目标测试通过，前端构建通过。用户会看到明确的不支持原因，不再被误导为网络问题。

## 015. 支持 ETF 时不能复用普通股票财务指标口径

### 发生了什么

用户同意将 `588200` 这类 ETF 代码纳入分析入口，但如果只放行 5 开头代码，系统会继续按普通上市公司股票生成 A 股投研报告。

### 原因

原 `StockSubject` 没有资产类型，`StockCodeResolver` 只能区分交易所，`FinancialMetricEngine`、`TushareMarketDataProvider` 和 `InvestmentReportWriter` 都默认使用普通股票财务报表口径。ETF 没有上市公司营业收入、毛利率、ROE 等指标，直接复用会造成报告口径错误。

### 解决方式

新增 `StockAssetType.ETF` 并贯穿 `StockSubject`；解析器支持沪市 `5xxxxx`、深市 `15xxxx/16xxxx/18xxxx` ETF；TuShare ETF 分支优先调用 `fund_daily`，指标引擎只输出 ETF 收盘价、涨跌幅和成交额；报告模板改为 ETF 研究报告，不再展示 ROE、毛利率等公司财务章节。

### 结果

`588200` 可进入 ETF 报告链路，前端显示“股票/ETF分析”和“ETF解析”。`StockCodeResolverTest,FinancialMetricEngineTest,InvestmentReportWriterTest,TushareMarketDataProviderTest` 目标测试通过，ETF 基础支持已落地；基金净值、持仓、规模和跟踪误差仍作为后续增强。
