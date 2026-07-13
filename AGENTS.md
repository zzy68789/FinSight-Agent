# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## Project

FinSight 是基于 **Spring Boot 3.4.3 (Java 17) + MyBatis + Vue 3** 的 A股/ETF 投研报告生成系统。核心是证券代码研究工作流 + RAG 检索 + SSE 流式推送。

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

后端端口 **8000**；健康检查 `curl http://localhost:8000/` 返回 `{"status":"running","backend":"java","workflow":"stock-report-pipeline"}`。

## Architecture

**证券代码研究链路** —— `component/workflow/StockReportWorkflow.java`
  `StockResolve → DataSnapshot → MetricEngine → RiskAssessment → EvidenceCollect → InvestmentWriter → CitationReviewer + ComplianceReviewer → Evaluation`。

**后端分层**（`backend/src/main/java/com/zzy/finsight/`）：`controller` → `service` 接口 → `service.impl` 实现 → `mapper`(MyBatis XML)。业务计算、数据采集、审查和工作流分别位于 `component.analysis`、`component.marketdata`、`component.review`、`component.workflow`；金融模型位于 `domain.stock`，请求响应位于 `dto`，Provider、MyBatis TypeHandler 和序列化适配位于 `infrastructure`。另有 `rag`、`llm`、`search` 基础模块。

**外部依赖与降级**：MySQL 长期持久化；Redis 存运行态（未启动降级为进程内内存）；ChromaDB 向量检索（未启动降级为进程内向量库，重启丢失）；LLM / Tavily / TuShare 未配置 key 时走本地 fallback，可跑通完整流程。RAG 默认 `BM25 + vector` 融合 + 相关性阈值。

**数据库**：Flyway 负责正式迁移，`backend/src/main/resources/db/schema.sql` 仅保留完整结构参考；MyBatis SQL 位于 `backend/src/main/resources/mapper/`。schema 变更时新增 Flyway migration，并同步完整 schema 与必要的手动升级 SQL，所有字段带中文 `COMMENT`。

## Conventions

- **环境变量隔离**：只有 API Key 使用 `API_KEY`、`TAVILY_API_KEY`、`TUSHARE_API_KEY`；其他配置直接维护在 `application.yml`，不额外包装环境变量占位符。**不要读取全局 `OPENAI_API_KEY`**——会影响用户的 `cc-switch` 等全局 OpenAI 配置。已有 `ConfigurationPlaceholderTest` 防回退。
- **金融数字确定性**：财务指标一律用 Java `BigDecimal` 计算，不让 LLM 算关键数字。缺输入标 `MISSING_INPUT`，公开数据源失败标 `DATA_MISSING`。
- **投研报告合规**：报告必须标注"仅作研究辅助，不构成投资建议"；不做荐股、仓位、保证收益、自动交易、回测。最终报告需同时通过 `CitationReviewer` 和 `ComplianceReviewer`。
- **不引入 Python**：金融/图表能力用 Java + 前端 ECharts 实现，不接 yfinance / akshare / matplotlib。
- **结构化输出**：金融报告的引用审查、合规审查和评测输出保持结构化，非法结果 fail-closed，不得绕过最终门控。
- **前端 SSE 契约**：不轻改 `/api/stock-reports` 的路径、请求体和 SSE 字段。后端字段使用 `finalReport`/`reviewStatus` 等既有命名。
- **注释统一用中文**：所有代码注释（类/方法 Javadoc、行内注释）一律用中文。新增或修改公开类、接口、Controller 映射方法以及包含业务规则的非平凡方法时，必须补充简洁的中文 Javadoc；数据载体的每个字段都要说明含义，Java `record` 使用类级 `@param` 说明组件字段。不要给 getter、setter、构造器或显而易见的代码堆砌无意义注释。

## Progress Docs

在 `zy-agent` 完成功能/改代码/修 bug 后，进度只回写到 `docs/`（**不写 `.ztemp/`**，那里是日志、env 备份和归档计划，且含密钥文件勿读）：

- `docs/目前已经实现的功能.md` —— 已完成能力台账（附类/API/SQL/测试证据）
- `docs/待实现功能.md` —— 待实现路线图（P0–P3 + 验收标准）
- `docs/待解决问题.md` —— 遗留问题与硬化项（优先级总览表 + 分节）
- `docs/PROBLEM_LOG.md` —— 踩坑日志（`## NNN. 标题` + 发生了什么/原因/解决方式/结果 四段）

四个文档风格统一：顶部优先级总览表 + 分优先级分节。未经本轮真实验证不要写"已完成/已通过"。
