# FinSight Domain Context

## 核心领域语言

- **Research Agent Runtime**：根据研究问题、当前观察和门禁反馈动态选择只读工具、重新规划、综合或停止的执行内核。
- **Durable Turn Commit Module**：`DurableTurnCommitModule` 提供的深模块；把 Planner 动作决策、工具 journal 结果、证据、快照、指标、turn、轻量 checkpoint 与租约心跳收敛到轮次事务，并把最终报告、快照冻结、任务完成和事件 outbox 收敛到完成事务。
- **Lease fencing**：`lease_owner + lease_epoch` 组成的提交令牌。每次重新领取租约都会递增 epoch，旧执行者不能继续提交 turn 或结束任务。
- **Typed Tool Contract**：Planner 可继续输出 JSON，但 `ResearchToolRegistry` 必须按每个工具的稳定 `ToolDefinition` 完成强类型参数解码和校验；工具只读取不可变 `ToolContext` 并返回类型化 `ToolPayload`，状态变化由 Runtime reducer 串行应用。
- **Lightweight Agent Checkpoint**：`agent-state-v3-lite` 只保存任务/快照 ID、上下文哈希、计数、恢复指令和短观察；证据、快照、指标及工具调用索引从业务表和 journal 重新加载，并兼容迁移 v1/v2。
- **Agent Event Delivery**：`AgentEventStreamModule` 以 `agent_event_outbox` 为 canonical 事件源；多实例发布通过数据库 claim、租约、退避和死信协调，SSE 用任务内单调 sequence 回放缺口并通过 `Last-Event-ID` 续传。
- **Agent Event Projection**：前端纯函数 reducer；实时 SSE 与历史 outbox Trace 都归一化为同一事件，再投影成计划、工具时间线、证据、指标、风险、门禁和日志视图。

## 不变量

- 金融关键数字必须由 Java `BigDecimal` 确定性计算。
- 工具只提供研究数据与分析，不允许交易、下单、仓位和保证收益能力。
- 工具外部调用不置于数据库长事务内；开始 journal 先持久化，调用结果随 turn 原子提交。
- Planner 的 `NEXT_ACTION` 遥测必须与对应 turn 在同一 fenced 事务内建立关联，不能留下无法解释执行动作的孤立决策记录。
- 恢复发现状态之后存在 `PLANNED` turn 时，必须复用该 turn 已持久化的完整动作与 Planner 元数据，不能再次调用模型生成同类型但不同参数的动作。
- 未知或损坏的 Agent state version 必须 fail-closed，不能静默从 turn 0 重跑。
- 事件投递采用数据库内原子提交、传输层至少一次、消费端按 `taskId + sequence` 幂等；不得把外部 SSE 发布描述为 exactly-once。
- 最终报告必须同时通过引用、合规和评测门禁，并包含“仅作研究辅助，不构成投资建议”。
