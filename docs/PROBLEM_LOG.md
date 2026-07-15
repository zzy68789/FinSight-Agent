# do not miss 问题日志

这份文档只记录明确指定要沉淀的问题。每条记录保持同一结构：发生了什么、原因、解决方式、结果。

## 001. 通用调研 Agent 不能直接输入 A 股股票代码生成投研报告

### 发生了什么

用户希望直接输入 A 股股票代码生成投研报告，但原有系统只有通用研究入口 `/api/chat`，无法按股票代码进入金融报告链路。

### 原因

原 `/api/chat` 面向通用研究问题，缺少股票代码解析、金融数据快照、财务指标和引用审查语义。即使通过 Prompt 拼接股票问题，也无法形成可复核的投研报告流程。

### 解决方式

新增独立股票报告链路 `/api/stock-reports`，并新增 `com.zzy.finsight.financial` 包。工作流拆为 `StockResolve -> DataSnapshot -> MetricEngine -> EvidenceCollect -> Writer -> Reviewer`，同时保留通用调研链路不变。

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

新增 `TushareMarketDataProvider` 和 `finsight.market.tushare.*` 配置，token 使用 `TUSHARE_API_KEY`，其余参数直接维护在 `application.yml`，并把 `income`、`balancesheet`、`cashflow`、`daily_basic` 转成金融证据。

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

`588200` 可进入 ETF 报告链路，前端入口统一为“证券代码分析”，解析日志显示“ETF解析”。`StockCodeResolverTest,FinancialMetricEngineTest,InvestmentReportWriterTest,TushareMarketDataProviderTest` 目标测试通过，ETF 基础支持已落地；基金净值、持仓、规模和跟踪误差仍作为后续增强。

## 016. 项目读取全局 OPENAI_API_KEY 会影响 cc-switch 配置

### 发生了什么

用户删除了项目中的 `OPENAI_API_KEY`，因为这个全局变量会影响本机 `cc-switch` 相关配置，希望 FinSight 改用自己的环境变量名。

### 原因

`application.yml` 直接通过 `${OPENAI_API_KEY}` 读取 LLM Key，项目配置和本机全局 OpenAI-compatible 工具共享同一个变量名。只要 `cc-switch` 切换或清理该变量，FinSight 的 LLM 配置也会被连带影响。

### 解决方式

将后端 LLM API Key 改为用户指定的环境变量 `API_KEY`，其余 LLM 参数直接维护在 `application.yml`；README 和本地降级提示同步调整，并新增 `ConfigurationPlaceholderTest` 断言不再读取 `OPENAI_API_KEY` 或 `OPENAI_API_BASE`。

### 结果

FinSight 和 `cc-switch` 的全局 OpenAI 配置解耦。目标测试先因旧配置失败，改名后通过；`ApplicationSmokeTest` 和后端全量 `mvn.cmd test` 也通过。用户只需要在本项目启动窗口配置 `API_KEY`。

## 017. SSE 客户端断开不应决定后台任务成败

### 发生了什么

股票报告的步骤持久化、运行状态和 SSE 推送写在同一个 `send` 方法中。浏览器关闭、网络切换或页面刷新导致 `SseEmitter.send` 抛出异常时，完整工作流会进入失败分支。

### 原因

系统把 SSE 当成工作流执行依赖，而不是可随时断开的观察通道；编排逻辑又直接放在异步 Service 中，难以单独验证重写、失败和断连分支。

### 解决方式

抽出同步可测试的 `StockReportRunner`，先持久化任务阶段、步骤日志和 checkpoint，再尽力通知 `StockReportProgressListener`。监听器异常被隔离，后台任务继续执行；同时增加 `StockReportServiceTest` 覆盖客户端断开场景。

### 结果

SSE 断开不再把报告任务误标为失败，恢复调度器也能复用同一 Runner。2026-07-10 后端全量 90 个测试通过，前端生产构建通过。

## 018. 项目改名后继续复用旧命名会污染运行数据

### 发生了什么

项目已经统一为 FinSight Agent，但 Java 包、配置前缀、MySQL 库、Redis key、Chroma collection 和前端登录态仍沿用旧标识，新旧项目会共享业务数据和运行缓存。

### 原因

此前只完成产品定位和页面能力调整，没有把应用身份、配置命名空间与各类存储边界作为一次原子迁移处理；单独修改 Spring 应用名无法隔离 MySQL、Redis 和向量知识库。

### 解决方式

将 Java 根包迁移为 `com.zzy.finsight`，启动类改为 `FinSightApplication`，配置命名空间统一为 `finsight.*`，只有 LLM、Tavily、TuShare 的 API Key 使用 `API_KEY`、`TAVILY_API_KEY`、`TUSHARE_API_KEY` 环境变量；新建独立 MySQL `finsight` 库，Redis 切换逻辑库 7 并使用 `finsight:*` key，Chroma collection 改为 `finsight_docs`，前端登录态改为 `finsight_token`。旧数据库不删除、不迁移。

### 结果

真实 MySQL 空库已成功执行 Flyway V1→V2 并生成 11 张表，健康检查通过；后端 91 个测试和前端生产构建通过。旧库仍原样保留，FinSight 的任务、报告、缓存和向量文档均使用新的隔离命名。

## 019. 配置了 LLM Bean 不代表金融报告真的调用了模型

### 发生了什么

用户发现 ETF 报告几乎瞬间生成。数据库中的最新任务连续三轮 Writer 和 Reviewer 耗时均为 0 ms，报告内容全部是固定模板，容易误以为只是真实 LLM Key 没有连接成功。

### 原因

`LlmConfig`、`LlmClient` 和 OpenAI-compatible 模型 Bean 虽然已经存在，但 `StockReportWorkflow` 的 Writer 只调用基于 `StringBuilder` 的 `InvestmentReportWriter`，金融工作流没有任何 `LlmClient.generate` 调用点。

### 解决方式

将 SMART `LlmClient` 注入 `InvestmentReportWriter`，让模型在确定性草稿范围内增强叙述；Prompt 禁止重新计算金融数字和执行证据中的指令，最终引用、指标公式及风险明细由 Java 覆盖。缺 Key、调用异常或输出缺少八章节、免责声明、引用入口时自动回退模板，并升级 Writer 指纹避免复用旧缓存。

### 结果

聚焦测试已覆盖真实 LLM 路径、SMART 模型选择、Prompt 事实边界、确定性附录覆盖和异常回退。本机用户环境已存在真实 `API_KEY`，仍需重启当前后端并生成新报告完成真实外部模型联调。

## 020. 项目全量金融化后继续保留 financial 大包会掩盖职责边界

### 发生了什么

金融投研最初是基于通用 DRAI 系统增加的扩展，因此模型、数据源、指标、审查、工作流和持久化代码都集中在 `financial` 目录；项目后来已完全收束为金融系统，这个目录仍包含近 50 个不同职责的文件。同时持久层沿用手写 JDBC Repository，不符合期望的 Spring MVC 分层习惯。

### 原因

`financial` 原本用于隔离新增领域，但在金融能力成为项目主体后，它已经失去模块边界作用；包名只表达“都是金融代码”，没有表达对象、数据源、分析、审查和编排等具体职责。手写 SQL、行映射和 JSON 转换也分散在 Repository 实现中。

### 解决方式

引入 MyBatis 3.0.4，将调用关系统一为 `controller -> service -> mapper`，SQL 下沉到 `resources/mapper`，JSON 字段由 TypeHandler 处理。删除 `financial` 和 `repository` 包，并将文件按职责迁入 `domain/stock`、`dto/stock`、`provider`、`service/analysis`、`service/marketdata`、`service/review`、`service/stock` 与 `mapper`。

### 结果

原 `financial` 与 `repository` 目录已删除，金融业务从单一大包拆成可识别的 Spring 分层和领域子包。Mapper XML 配置测试通过，真实 MySQL 能加载全部映射并启动，完整测试结果记录在 `docs/目前已经实现的功能.md`。

## 021. 只把 financial 拆到 service 子目录仍会混淆服务与业务组件

### 发生了什么

第一次拆除 `financial` 后，指标引擎、证据解析器、快照构建器、Reviewer、Workflow 和真正的应用 Service 都位于 `service` 目录，MyBatis TypeHandler 也和 Mapper 接口放在一起。代码能够运行，但目录无法直观体现严格 Spring MVC 分层。

### 原因

第一次重构优先解决金融大包和 JDBC Repository 问题，采用了“分层 + 按职责子包”的过渡设计，没有进一步区分 Controller 面向的应用服务、可复用业务组件、纯领域对象和基础设施适配器。

### 解决方式

将 `service` 收敛为接口并把实现统一迁入 `service/impl`；指标、采集、审查和工作流迁入 `component`；Provider、MyBatis TypeHandler 和请求序列化迁入 `infrastructure`；指标定义和 A 股主档迁入纯 `domain`，由 `FinancialDomainConfig` 注册为 Spring Bean。

### 结果

Controller 只依赖 Service 接口，Service 实现、业务组件、Mapper、领域模型和基础设施目录边界清晰。原 `service/analysis`、`service/marketdata`、`service/review`、`service/stock`、`provider` 和 `mapper/typehandler` 均不再存在。

## 022. 缺少类级注释会增加分层代码的理解成本

### 发生了什么

后端共有 141 个主代码 Java 文件，但本轮开始前只有 1 个顶层类型具备类级 Javadoc。目录结构虽然已经完成 Spring MVC 分层，阅读者仍需进入实现细节才能确认多数类型的职责。

### 原因

此前重构优先保证功能、分层和测试通过，只在约定中要求使用中文注释，没有自动检查新增类型是否具备职责说明。

### 解决方式

为全部主代码类型补充简洁的中文类级 Javadoc，为 8 个 Service 契约以及金融工作流、指标、快照、审查和 RAG 等核心公开方法补充方法说明，并将原有英文行内注释统一为中文。同步强化 `AGENTS.md` 约定，并在 `ArchitectureLayeringTest` 中增加中文 Javadoc 覆盖检查。

### 结果

141 个主代码 Java 文件均具备中文 Javadoc；2026-07-13 执行 `mvn.cmd test`，97 个测试全部通过，后续新增无中文 Javadoc 的主代码文件会由测试直接指出。

## 023. 只补类级注释仍不足以解释接口行为和数据字段

### 发生了什么

第一轮中文注释补充覆盖了全部主代码类型和部分核心业务方法，但 Controller 接口方法及实体、DTO、检索模型中的字段仍缺少逐项说明，阅读接口契约和数据结构时仍需根据命名推断含义。

### 原因

第一轮将重点放在类型职责和复杂业务方法，没有把 Controller 映射方法与数据载体字段纳入明确的覆盖范围，自动检查也只验证每个主代码文件是否存在中文 Javadoc。

### 解决方式

为 30 个 Controller 接口方法补充中文说明；为 51 个公开 `record` 的 299 个组件字段增加标准 `@param` 说明，并为普通请求 DTO 的 9 个字段增加字段级 Javadoc。新增 `DocumentationConventionTest`，持续检查 Controller 映射方法、公开 `record` 组件和普通 DTO 字段。

### 结果

接口行为和数据字段均能直接从源码注释理解；2026-07-13 执行 `mvn.cmd test`，100 个测试全部通过，`mvn.cmd package -DskipTests` 打包成功。

## 024. 数据源返回成功不代表报告期语义正确

### 发生了什么

贵州茅台报告中的 TuShare 财务金额大多能与官方一季报对上，但营收同比显示为 0.00%，ROE 也使用期末权益冒充平均净资产；Provider、引用审查和自动评测仍全部显示成功。

### 原因

`TushareMarketDataProvider` 将按 `end_date` 排序后的第二行直接当作上年同期，而 TuShare 会为同一报告期返回 `update_flag=0/1` 的多个版本；资产负债表映射又把期末归母权益直接写入 `AVERAGE_EQUITY`。原 `FinancialEvidenceItem.effective()` 只排除 `DATA_MISSING`，其他问题编码仍会被当作有效证据。

### 解决方式

财报行改为按报告期、标准合并报表、更新标记和公告日期选择唯一版本，上年同期严格匹配本期日期减一年；ROE 改用年初与期末归母权益平均值。新增 `FinancialEvidenceValidator` 标记错期、重复、未来日期、财务关系异常和明显网页占位内容，`CitationReviewer` 对关键语义问题 fail-closed。

### 结果

代码已补充 TuShare 重复版本样例、ROE v2 公式和证据语义校验测试；报告会分别展示财务报告期与行情数据日，旧快照缓存由新版指标目录和 Writer 指纹隔离。按用户要求本轮未执行测试，最终结果待本地验证。

## 025. LLM 超时回退不能继续记为 Writer 普通成功

### 发生了什么

贵州茅台混合检索报告在 90 秒和 120 秒配置下分别耗时约 90045 ms、120036 ms 后回退确定性模板；数据库中的 Writer 步骤仍显示 `SUCCESS`，前端也只显示报告生成完成。

### 原因

Writer 把包含完整证据、指标公式和风险明细的确定性报告全部发送给非流式 `qwen3.7-max`，而这些附录随后又会被 Java 覆盖。HTTP 超时异常在 `InvestmentReportWriter` 内部被捕获并转换为普通字符串，外层工作流无法区分 LLM 正常生成和模板降级，因此固定写入 `SUCCESS`。

### 解决方式

将 LLM 输入压缩为八章节叙述草稿，增加 2500 中文字符正文约束和 `max-output-tokens=4096`；免责声明与引用附录由 Java 确定性补齐。报告增加稳定 fallback 原因标记，Writer 降级时步骤状态写为 `DEGRADED`，SSE 和前端同步展示超时、结构非法、未配置、空响应或调用失败分类。

### 结果

真实 `qwen3.7-max` 重放从超过 100 秒超时缩短到 50.859 秒返回，证券代码和八章节完整；后端 105 个测试通过，前端生产构建通过。应用内完整任务仍需在重启后复测，不能仅凭直接接口重放宣称生产链路已经完成。

## 026. 引用附录存在不代表正文观点可追溯

### 发生了什么

贵州茅台报告虽然包含完整“引用与数据快照”并获得 0.99 自动评测分，但正文财务、估值和新闻判断没有就近 `[E#]`；雪球登录页、中财网导航页和行情 SEO 摘要仍进入证据账本，阶段性 ROE 也被当作普通全年指标评价。

### 原因

Writer 为降低延迟裁掉了发送给 LLM 的证据附录，模型无法知道证据编号；`CitationReviewer` 和 `FinancialEvaluator` 又在整份报告中查数值与引用，确定性附录天然包含全部指标和 `[E#]`，导致正文缺少引用仍能通过。公开数据链路只使用 Tavily Search 短摘要，网页质量规则覆盖不足。

### 解决方式

Writer 改为发送最多 18 条有效紧凑证据索引，并要求关键事实就近引用；确定性模板同步写入原始证据编号和阶段性 ROE 未年化口径。新增 `TavilyExtractClient` 批量提取正文，`FinancialEvidenceValidator` 增加公开 URL 去重和导航/行情占位页识别；Reviewer/Evaluator 只统计正文引用，并对无效编号、来源质量、报告期语义、跨网页行情拼接和方向性表述 fail-closed。

### 结果

相关实现已纳入 2026-07-15 后端 128 项全量回归，`RagService` 也同步迁移为 `service` 接口与 `service.impl` 实现以恢复分层边界；真实 Tavily/LLM 联调仍需单独执行。

## 027. 只对少量证券样例评测会让其他报告默认通过

### 发生了什么

线上 `FinancialEvaluator` 只有命中 10 个内置证券代码时才返回结果；其他证券得到空结果，而 Runner 把空结果直接视为通过。同时，来源可用性、引用硬规则和数据集关键点被混在 12 项平均分中，无法表达哪些失败必须阻断。

### 原因

评测集既承担离线样例定义，又被直接用作线上质量门禁，导致证券白名单与报告安全规则耦合；系统也缺少冻结快照、检索相关性标注、历史基线和可重复执行的回归入口。

### 解决方式

将线上规则改为对所有股票和 ETF 执行，按 `HARD` 与 `ADVISORY` 区分阻断和告警，保留原 `evaluation` SSE 契约。另建版本化冻结数据集和确定性 Runner，引入稳定 `chunkId`、Recall/Precision/MRR/NDCG、数值误差、显式基线、结构化 Judge 与外部冒烟模式，并用独立 GitHub Actions 分离 PR 强门禁和非阻断联网评测。

### 结果

12 个股票/ETF 正例、8 个规则负例和 24 个检索问题均通过确定性回归并生成 JSON/Markdown 报告；2026-07-15 后端 128 项测试零失败、零错误，前端生产构建通过。Judge 与真实数据冒烟默认关闭且缺 Key 时明确记为 `SKIPPED`。
