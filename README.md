# DRAI - Deep Research Agent Intelligence

DRAI 是一个基于 **Spring Boot + LangGraph4j + Vue 3** 的 Multi-Agent 深度调研报告生成系统。用户可以输入研究主题，也可以上传 PDF 构建本地知识库；后端会通过 Agent 工作流完成任务规划、资料检索、报告撰写、质量审查、失败回环和报告修订，并通过 SSE 将执行过程实时推送到前端。

重点展示 Java 后端工程化、Agent 工作流编排、RAG 检索增强、长流程状态持久化和前后端流式交互。

## 功能特性

- **Multi-Agent 工作流编排**：基于 LangGraph4j 构建 Router、Planner、Researcher、Writer、Reviewer、Refiner 多节点状态机。
- **质量审查与失败回环**：Reviewer 节点会审查生成报告，失败时回到 Planner 补充规划，最多重试 3 轮。
- **PDF 知识库检索**：支持上传最多 5 个 PDF，后端使用 PDFBox 提取文本、切片并进行本地检索。
- **Hybrid / Document 模式**：Document 模式只基于本地文档回答；Hybrid 模式可在本地文档不足时补充 Tavily 搜索结果。
- **报告修订能力**：同一 `thread_id` 下，如果用户输入“修改、改写、补充、重写、调整”等指令，会进入 Refiner 节点基于旧报告生成新版本。
- **SSE 实时推送**：后端以 Server-Sent Events 推送 `planner`、`researcher`、`writer`、`reviewer`、`refiner` 等执行状态。
- **任务状态持久化**：通过 JDBC 持久化任务、Agent 步骤日志、报告版本和 checkpoint。
- **本地可演示降级**：未配置 LLM API Key 或 Tavily Key 时，系统仍可用本地降级内容跑通完整流程。

## 技术栈

**Backend**

- Java 17
- Spring Boot 3.4.3
- Spring Web / Validation / JDBC
- LangGraph4j
- LangChain4j OpenAI-compatible client
- PDFBox
- MySQL
- Tavily Search API
- SSE

**Frontend**

- Vue 3
- Vite
- Tailwind CSS
- markdown-it
- markdown-it-katex
- lucide-vue-next

> 说明：项目中保留了 Redis、ChromaDB 等扩展方向，但当前 MVP 的主流程使用 JDBC 持久化和本地文本检索完成演示。

## 系统流程

```text
Router
  ├─ 新任务 -> Planner -> Researcher -> Writer -> Reviewer
  │                                      ├─ PASS -> END
  │                                      └─ FAIL -> Planner，最多 3 轮
  └─ 修改指令 -> Refiner -> END
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
│       │   ├── llm/               # OpenAI-compatible LLM 封装
│       │   ├── rag/               # PDF 解析、切片、本地检索
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

配置大模型和搜索服务：

```powershell
$env:OPENAI_API_BASE="https://your-openai-compatible-endpoint/v1"
$env:OPENAI_API_KEY="your_api_key"
$env:DRAI_LLM_FAST_MODEL="qwen3-max"
$env:DRAI_LLM_SMART_MODEL="deepseek-r1"
$env:TAVILY_API_KEY="your_tavily_key"
```

未配置 `OPENAI_API_KEY` 时，系统会使用本地降级内容，便于验证完整 Agent 流程；未配置 `TAVILY_API_KEY` 时，搜索服务会返回降级结果。

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

字段说明：

- `query`：用户调研问题或报告修改指令。
- `search_mode`：支持 `hybrid` 和 `document`。
- `thread_id`：会话 ID，同一 ID 下可触发报告修订。

## 数据表

后端启动时会执行 `backend/src/main/resources/db/schema.sql`，创建以下核心表：

- `research_task`：调研任务主表。
- `agent_step_log`：Agent 节点执行日志。
- `report`：报告内容和版本。
- `checkpoint`：工作流状态快照。

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

## 当前边界

当前版本是可运行的 MVP，不是完整企业级平台。以下能力属于后续扩展方向：

- 接入真正的向量数据库，例如 ChromaDB。
- Redis 保存运行中任务状态、SSE 会话状态和 thread 最新任务映射。
- 更完整的 Hybrid RAG：BM25 + 向量检索 + rerank。
- 多搜索源 fallback，例如 Brave、Jina、arXiv、GitHub Search。
- 用户登录、权限、多租户隔离和后台管理。
- 报告导出 PDF / Word。
- 更完整的人工反馈 HITL 流程。
