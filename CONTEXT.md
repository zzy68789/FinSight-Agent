# FinSight Domain Context

## 核心领域语言

- **Research Agent Runtime**：根据研究问题、当前观察和门禁反馈动态选择只读工具、重新规划、综合或停止的执行内核。
- **Durable Turn Commit Module**：`DurableTurnCommitModule` 提供的深模块；把工具 journal 结果、证据、快照、指标、turn、轻量 checkpoint 与租约心跳收敛到同一事务边界。
- **Lease fencing**：`lease_owner + lease_epoch` 组成的提交令牌。每次重新领取租约都会递增 epoch，旧执行者不能继续提交 turn 或结束任务。
- **Typed Tool Contract**：Planner 可继续输出 JSON，但每个 `ResearchTool` 必须暴露稳定 `ToolDefinition`，并自行完成参数解码、业务校验、执行、错误分类和结果结构声明。
- **Lightweight Agent Checkpoint**：`agent-state-v2-lite` 只保存任务/快照 ID、上下文哈希、计数、恢复指令和短观察；证据、快照、指标及工具调用索引从业务表和 journal 重新加载。
- **Agent Event Projection**：前端纯函数 reducer；实时 SSE 与历史步骤日志都归一化为同一事件，再投影成计划、工具时间线、证据、指标、风险、门禁和日志视图。

## 不变量

- 金融关键数字必须由 Java `BigDecimal` 确定性计算。
- 工具只提供研究数据与分析，不允许交易、下单、仓位和保证收益能力。
- 工具外部调用不置于数据库长事务内；开始 journal 先持久化，调用结果随 turn 原子提交。
- 未知或损坏的 Agent state version 必须 fail-closed，不能静默从 turn 0 重跑。
- 最终报告必须同时通过引用、合规和评测门禁，并包含“仅作研究辅助，不构成投资建议”。
