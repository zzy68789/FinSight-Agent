# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project

FinSight 是基于 **Spring Boot 3.4.3 (Java 17) + LangGraph4j + Vue 3** 的 Multi-Agent 深度调研报告生成系统。核心是两条独立的 Agent 工作流 + RAG 检索 + SSE 流式推送，外加一条 A股/ETF 投研报告链路。

环境为 **Windows / PowerShell**，命令用 `mvn.cmd`、`npm.cmd`（带 `.cmd` 后缀）。后端无 Maven wrapper，直接用 `mvn.cmd`。

## Commands

```powershell
# 后端（backend/）
cd backend
mvn.cmd test                                          # 全部测试
mvn.cmd "-Dtest=StockCodeResolverTest" test           # 单个测试类
mvn.cmd "-Dtest=AdminServiceTest#systemHealthReportsTushareConfigurationStatus" test  # 单个方法
mvn.cmd package -DskipTests                            # 打包
mvn.cmd spring-boot:run                                # 启动，监听 http://localhost:8000

# 前端（frontend/）
cd frontend
npm.cmd install
npm.cmd run dev
npm.cmd run build
```

后端端口 **8000**；健康检查 `curl http://localhost:8000/` 返回 `{"status":"running","backend":"java","workflow":"langgraph4j"}`。

## Architecture

**两条互相独立的 Agent 工作流**（改动其一不要污染另一条）：

- 通用调研链路 —— `agent/graph/ResearchGraphFactory.java`
  `Router → Planner → Researcher → Writer → Reviewer`；Reviewer PASS 结束、FAIL 回 Planner 最多 3 轮；修改指令走 Refiner。
- 证券代码研究链路 —— `financial/StockReportWorkflow.java`
  `StockResolve → DataSnapshot → MetricEngine → RiskAssessment → EvidenceCollect → InvestmentWriter → CitationReviewer + ComplianceReviewer → Evaluation`。

**后端分层**（`backend/src/main/java/com/zzy/finsight/`）：`controller` → `service` → `repository`(JDBC)，`agent`(通用工作流/节点/状态)、`financial`(投研报告/证据/指标/回放)、`rag`(PDF 解析·切片·embedding·ChromaDB)、`llm`、`search`。

**外部依赖与降级**：MySQL 长期持久化；Redis 存运行态（未启动降级为进程内内存）；ChromaDB 向量检索（未启动降级为进程内向量库，重启丢失）；LLM / Tavily / TuShare 未配置 key 时走本地 fallback，可跑通完整流程。RAG 默认 `BM25 + vector` 融合 + 相关性阈值。

**数据库**：`backend/src/main/resources/db/schema.sql` 由 Spring Boot 启动时按 `spring.sql.init.schema-locations` 自动执行（首次需先手动建 `finsight` 库）。旧库需手动跑 `db/upgrade-*.sql`。schema 变更时新增 `db/upgrade-<feature>.sql`，所有字段带中文 `COMMENT`。

## Conventions

- **环境变量隔离**：只有 API Key 使用项目专用环境变量 `FINSIGHT_LLM_API_KEY`、`FINSIGHT_TAVILY_API_KEY`、`FINSIGHT_TUSHARE_API_KEY`；其他配置直接维护在 `application.yml`，不额外包装环境变量占位符。**不要读取全局 `OPENAI_API_KEY`**——会影响用户的 `cc-switch` 等全局 OpenAI 配置。已有 `ConfigurationPlaceholderTest` 防回退。
- **金融数字确定性**：财务指标一律用 Java `BigDecimal` 计算，不让 LLM 算关键数字。缺输入标 `MISSING_INPUT`，公开数据源失败标 `DATA_MISSING`。
- **投研报告合规**：报告必须标注"仅作研究辅助，不构成投资建议"；不做荐股、仓位、保证收益、自动交易、回测。最终报告需同时通过 `CitationReviewer` 和 `ComplianceReviewer`。
- **不引入 Python**：金融/图表能力用 Java + 前端 ECharts 实现，不接 yfinance / akshare / matplotlib。
- **结构化输出**：Planner 解析 JSON array，Reviewer 强制解析 `{"status":"PASS|FAIL","feedback":""}`，非法输出 fail-closed。
- **前端 SSE 契约**：不轻改 `/api/chat`、`/api/stock-reports` 的路径、请求体和 SSE 字段。后端字段用 `finalReport`/`searchResults`/`reviewStatus`，前端兼容两种命名。
- **注释统一用中文**：所有代码注释（类/方法 Javadoc、行内注释）一律用中文，与现有代码保持一致。

## Progress Docs

在 `zy-agent` 完成功能/改代码/修 bug 后，进度只回写到 `docs/`（**不写 `.ztemp/`**，那里是日志、env 备份和归档计划，且含密钥文件勿读）：

- `docs/目前已经实现的功能.md` —— 已完成能力台账（附类/API/SQL/测试证据）
- `docs/待实现功能.md` —— 待实现路线图（P0–P3 + 验收标准）
- `docs/待解决问题.md` —— 遗留问题与硬化项（优先级总览表 + 分节）
- `docs/PROBLEM_LOG.md` —— 踩坑日志（`## NNN. 标题` + 发生了什么/原因/解决方式/结果 四段）

四个文档风格统一：顶部优先级总览表 + 分优先级分节。未经本轮真实验证不要写"已完成/已通过"。
