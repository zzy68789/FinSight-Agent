# ReAct Agent 改造计划

更新时间：2026-07-08

## Summary

把通用调研链路的 Planner 和 Researcher 从"写死的流程"改成基于 LangChain4j function calling 的 ReAct agent：由 LLM 自主决策调用工具、观察结果、迭代，而不是由 Java 代码固定编排检索顺序。这是把项目从"LLM workflow"升级为"真 Agent"的核心改造，直接对应 Agent 岗简历硬伤。

技术选型：**AiServices + @Tool 自动循环**（框架自动跑 ReAct 多轮，不手写循环）。范围：Researcher + Planner 都 agent 化，金融链路不动。

## 核心设计

- **Researcher agent**：拿到 `searchLocalKnowledge` 和 `searchWeb` 两个工具，由 LLM 自主决定调哪个、调几次、何时停止收集证据。替换现有 `collectEvidence` 的阈值+顺序硬编码。
- **Planner agent**：拿到一个轻量 `probeKnowledge` 探查工具，先看本地知识库有什么，再决定子任务往哪个方向拆（看菜下饭），替换现有"盲拆"。
- **失控防线**：Researcher 工具轮次上限 5，Planner 上限 3（对应补上"无成本/轮数控制"缺陷）。
- **降级保留**：LLM 未配置或 agent 失败时，回退到现有确定性 RAG+搜索链路，保证开发/演示不挂。
- **埋点保留**：记录 agent 调了哪些工具、几次，写入 `subTaskResults`，保持 SSE 和前端可观测，不丢现有功能。

## Steps

### 1. 扩展 LlmClient，暴露 ChatModel 给 AiServices

- `llm/LlmClient.java`：新增方法拿到底层 `ChatModel`（或直接在 config 层暴露 fast/smart `ChatModel` bean 供 AiServices 使用）。
- `config/LlmConfig.java`：把 `fastChatModel` / `smartChatModel` 也注册为可注入 bean（当前是 `LangChain4jLlmClient` 私有字段）。

### 2. 定义工具（新增 `agent/tool/` 包）

```java
@Component
public class ResearchTools {
    private final RagService rag;
    private final SearchService search;

    @Tool("检索本地知识库(用户上传的PDF)。当问题可能在已上传资料中找到答案时使用，返回相关文档片段")
    public String searchLocalKnowledge(String query) {
        // 复用 rag.retrieve(query, 5)，格式化为文本，无结果返回明确提示
    }

    @Tool("联网搜索最新/外部信息。当本地知识库无法回答或问题涉及实时信息时使用")
    public String searchWeb(String query) {
        // 复用 search.search(query, 3)，过滤 fallback，截断内容
    }
}
```

- 工具描述文字是 LLM 决策依据，**是本次最需要打磨的部分**，需反复调。
- 工具内部记录调用埋点（供 Researcher 汇总 subTaskResults）。

### 3. 定义 AiServices agent 接口

```java
interface ResearcherAgent {
    @SystemMessage("你是调研员，用工具收集足够证据后停止。document模式不得联网。")
    String research(String query);
}
interface PlannerAgent {
    @SystemMessage("先探查本地知识库，再把问题拆成3-5个检索子问题，每行一个。")
    String plan(String query);
}
```

- config 中用 `AiServices.builder(...).chatModel(smartChatModel).tools(tools).build()` 构建，设 `maxSequentialToolExecutions`。

### 4. 改造 ResearcherNode

- `agent/node/ResearcherNode.java`：`apply()` 改为调 `ResearcherAgent`，从 agent 输出和工具埋点重建 `subTaskResults`。
- 保留 `document` 模式无证据 `shouldStop=true` 逻辑。
- LLM 不可用时走原 `collectEvidence` 降级路径（保留现有私有方法作为 fallback）。

### 5. 改造 PlannerNode

- `agent/node/PlannerNode.java`：接入 `PlannerAgent`，用 `probeKnowledge` 探查后拆子任务。
- 保留原 `PlanParser` 解析逻辑作为输出兜底和降级。

### 6. 魔法数字外置

- 工具轮次上限、topK(5/3)、相关度阈值(0.2) 提为配置项 `drai.agent.*`，对应补上代码质量清单里的魔法数字项。

## Test Plan

- `ResearchToolsTest`：工具正确调用 rag/search，无结果时返回明确提示。
- `ResearcherNodeTest`（已存在，需改）：mock agent，验证证据聚合、document 停止、LLM 不可用降级。
- `PlannerNodeTest`（新增）：验证探查后拆子任务、解析兜底、降级。
- 回归：`mvn.cmd test` 全绿；确认金融链路测试不受影响。
- 评测：改造前后跑 `financial-eval-set` 或通用调研样例，记录工具调用行为，作为简历数据支撑。

## 边界

- 只改通用调研链路（`agent/` 包），金融链路（`financial/`）规则驱动，不动。
- 不改 SSE 契约和前端字段。
- 保留所有现有降级特性。

## 简历表述（改造完成后）

> 基于 LangChain4j function calling 实现 ReAct 式 Planner/Researcher Agent，由 LLM 自主决策调用本地检索/联网搜索工具并观察结果迭代拆解任务，设最大工具调用轮次防失控；LLM 不可用时降级为确定性检索链路，保证可演示性。
