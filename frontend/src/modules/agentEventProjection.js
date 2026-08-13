const EVENT_LABELS = Object.freeze({
  idle: '就绪',
  run_created: '任务创建',
  plan_created: '研究规划',
  tool_started: '工具执行',
  tool_completed: '观察更新',
  replanned: '重新规划',
  evidence_recovery_progress: '补充证据',
  synthesis_started: '综合撰写',
  synthesis_completed: '报告生成',
  review_completed: '确定性门禁',
  quality_gate_routed: '门禁路由',
  run_completed: '已完成',
  run_stopped: '已停止',
  stock_resolve: '证券解析',
  data_snapshot: '数据快照',
  metric_engine: '指标计算',
  risk_assessment: '风险评分',
  evidence_collect: '证据账本',
  bull_bear_research: '多空研究',
  writer: '撰写中',
  reviewer: '质检中',
  evaluation: '评测中',
  done: '已完成'
});

/** 创建实时 SSE 与历史 Trace 共用的 Agent 视图状态。 */
export function createAgentEventProjection() {
  return {
    currentStep: 'idle',
    completedSteps: [],
    events: [],
    seenEventIds: [],
    plan: null,
    budget: null,
    taskId: null,
    requestSummary: null,
    latestTool: null,
    replanCount: 0,
    evidenceRecoveryCount: 0,
    qualityGateDecision: null,
    stopReason: '',
    runStats: null,
    metrics: [],
    evidence: [],
    snapshotSummary: null,
    riskAssessment: null,
    compliance: null,
    evaluation: null,
    bullBearResearch: null,
    providerStages: [],
    finalReport: '',
    logs: []
  };
}

/** 将实时 SSE、版本化 outbox 或旧步骤日志规范化为同一事件契约。 */
export function normalizeAgentEvent(event) {
  if (!event) return { step: '', data: {} };
  if (event.step) return { step: event.step, data: event.data || {} };
  if (event.schemaVersion && event.type) {
    return {
      step: event.type,
      data: {
        ...(event.payload || {}),
        schemaVersion: event.schemaVersion,
        eventId: event.eventId,
        sequence: event.sequence,
        taskId: event.taskId,
        turnNo: event.turnNo,
        status: event.status,
        errorMessage: event.errorMessage,
        durationMs: event.durationMs,
        timestamp: event.createdAt
      }
    };
  }
  const data = parsePayload(event.outputSnapshot);
  return {
    step: event.stepName || event.type || '',
    data: {
      ...data,
      status: data.status || event.status,
      turnNo: data.turnNo ?? event.attemptNo,
      timestamp: data.timestamp || event.createdAt
    }
  };
}

/** 以纯函数把一个 Agent 事件归约为页面视图状态。 */
export function reduceAgentEvent(previous, rawEvent) {
  const state = cloneProjection(previous || createAgentEventProjection());
  const event = normalizeAgentEvent(rawEvent);
  const step = event.step;
  const payload = event.data || {};
  if (!step) return state;

  const eventId = payload.taskId && payload.sequence
    ? `${payload.taskId}:${payload.sequence}`
    : payload.eventId || stableFallbackId(step, payload);
  if (state.seenEventIds.includes(eventId)) return state;
  state.seenEventIds = [...state.seenEventIds, eventId].slice(-200);
  state.currentStep = step;
  if (!state.completedSteps.includes(step)) state.completedSteps.push(step);
  state.events = [...state.events, {
    id: eventId,
    type: step,
    status: payload.status || 'SUCCESS',
    sequence: Number(payload.sequence || 0),
    turnNo: payload.turnNo || 0,
    toolName: payload.toolName || '',
    summary: payload.summary || payload.reason || '',
    durationMs: Number(payload.durationMs || 0),
    reason: payload.reason || payload.errorMessage || '',
    route: payload.route || '',
    timestamp: payload.timestamp || ''
  }].slice(-30);

  if (step === 'run_created') {
    state.budget = payload.budget || null;
    state.taskId = payload.taskId || state.taskId;
    state.requestSummary = {
      ticker: payload.ticker || '',
      researchQuestion: payload.researchQuestion || '',
      researchIntent: payload.researchIntent || '',
      asOfDate: payload.asOfDate || '',
      researchDepth: payload.researchDepth || '',
      comparisonTickers: Array.isArray(payload.comparisonTickers) ? payload.comparisonTickers : []
    };
    state.logs.push(`[运行时] 任务 #${payload.taskId || '-'} 已创建，最大 ${payload.budget?.maxTurns || '-'} 轮。`);
  } else if (step === 'plan_created' || step === 'replanned') {
    state.plan = payload.plan || null;
    if (step === 'replanned') state.replanCount = Number(payload.replanCount || state.replanCount + 1);
    state.logs.push(`[规划] ${state.plan?.goal || '研究问题已更新'}`);
    if (payload.degraded) state.logs.push(`[规划降级] ${payload.degradedReason || 'LLM 不可用，使用确定性策略'}`);
  } else if (step === 'tool_started') {
    state.latestTool = {
      name: payload.toolName || '',
      status: 'RUNNING',
      turnNo: Number(payload.turnNo || 0),
      summary: '工具执行中',
      durationMs: 0
    };
    state.logs.push(`[工具] 第 ${payload.turnNo || '-'} 轮调用 ${payload.toolName || '-'}。`);
  } else if (step === 'tool_completed') {
    state.latestTool = {
      name: payload.toolName || '',
      status: payload.status || 'SUCCESS',
      turnNo: Number(payload.turnNo || 0),
      summary: payload.summary || '',
      durationMs: Number(payload.durationMs || 0)
    };
    applyToolResult(state, payload);
  } else if (step === 'evidence_recovery_progress') {
    state.evidenceRecoveryCount += 1;
    state.logs.push(`[补证据] ${payload.summary || payload.errorMessage || '已记录一次证据恢复结果'}`);
  } else if (step === 'synthesis_completed' || step === 'writer') {
    state.finalReport = payload.finalReport || payload.final_report || state.finalReport;
    state.logs.push(`[综合] 第 ${payload.attempt || 1} 版研究报告已生成。`);
  } else if (step === 'review_completed' || step === 'reviewer') {
    state.compliance = payload.compliance || null;
    state.evaluation = payload.evaluation || state.evaluation;
    state.qualityGateDecision = payload.qualityGateDecision || state.qualityGateDecision;
    const passed = (payload.reviewStatus || payload.review_status) === 'PASS';
    state.logs.push(passed ? '[门禁] 引用、合规和评测已通过。' : `[门禁] 未通过：${payload.critique || '请查看轨迹'}`);
  } else if (step === 'run_completed' || step === 'done') {
    state.taskId = payload.taskId || state.taskId;
    state.finalReport = payload.finalReport || state.finalReport;
    state.runStats = {
      turnCount: Number(payload.turnCount || 0),
      toolCallCount: Number(payload.toolCallCount || 0),
      plannerDegraded: Boolean(payload.plannerDegraded)
    };
    state.stopReason = 'COMPLETED';
    state.logs.push(`[完成] 任务 #${state.taskId || '-'} 已写入报告库。`);
  } else if (step === 'run_stopped') {
    state.taskId = payload.taskId || state.taskId;
    state.stopReason = payload.reason || 'Agent 未能在预算内形成可发布报告';
    state.runStats = {
      turnCount: Number(payload.turnCount || 0),
      toolCallCount: Number(payload.toolCallCount || 0),
      plannerDegraded: false
    };
    state.logs.push(`[停止] ${payload.reason || 'Agent 未能在预算内形成可发布报告'}`);
  } else if (step === 'quality_gate_routed') {
    state.qualityGateDecision = {
      ...(state.qualityGateDecision || {}),
      route: payload.route || '',
      issues: payload.issues || [],
      summary: payload.summary || ''
    };
    state.logs.push(`[门禁路由] ${payload.summary || payload.route || '已确定后续动作'}`);
  } else {
    applyLegacyEvent(state, step, payload);
  }
  return state;
}

/** 使用同一 reducer 重放历史持久化事件。 */
export function replayAgentEvents(events, initial = createAgentEventProjection()) {
  return (events || []).reduce(reduceAgentEvent, initial);
}

/** 返回统一事件中文标签。 */
export function agentEventTypeLabel(type) {
  return EVENT_LABELS[type] || type || '-';
}

function applyToolResult(state, payload) {
  const result = payload.result || {};
  state.logs.push(`[观察] ${payload.summary || `${payload.toolName || '工具'} 执行完成`}`);
  if (result.subject) {
    state.logs.push(`[证券解析] ${result.subject.fullCode || '-'}，${result.subject.companyName || '待识别证券'}`);
  }
  if (result.evidence) {
    const existing = new Map(state.evidence.map((item) => [JSON.stringify(item), item]));
    result.evidence.forEach((item) => existing.set(JSON.stringify(item), item));
    state.evidence = [...existing.values()];
    state.snapshotSummary = {
      evidenceCount: state.evidence.length,
      missingCount: state.evidence.filter((item) => item.issueCode).length
    };
  }
  if (result.metrics) state.metrics = result.metrics;
  if (result.riskAssessment) state.riskAssessment = result.riskAssessment;
  if (result.research) state.bullBearResearch = result.research;
}

function applyLegacyEvent(state, step, payload) {
  if (step === 'data_snapshot') {
    state.snapshotSummary = { evidenceCount: payload.evidenceCount || 0, missingCount: payload.missingCount || 0 };
  } else if (step === 'metric_engine') {
    state.metrics = payload.metrics || [];
  } else if (step === 'risk_assessment') {
    state.riskAssessment = payload.riskAssessment || payload.risk_assessment || null;
  } else if (step === 'evidence_collect') {
    state.evidence = payload.evidence || [];
    state.providerStages = payload.stageResults || payload.stage_results || [];
  } else if (step === 'bull_bear_research') {
    state.bullBearResearch = payload.research || null;
  } else if (step === 'evaluation') {
    state.evaluation = payload.evaluation || null;
  }
}

function parsePayload(value) {
  if (!value) return {};
  if (typeof value === 'object') return value;
  try { return JSON.parse(value); } catch { return {}; }
}

function stableFallbackId(step, payload) {
  return [payload.taskId || '', payload.turnNo || '', step, payload.callId || '', payload.timestamp || ''].join(':');
}

function cloneProjection(state) {
  return {
    ...state,
    completedSteps: [...state.completedSteps],
    events: [...state.events],
    seenEventIds: [...state.seenEventIds],
    metrics: [...state.metrics],
    evidence: [...state.evidence],
    providerStages: [...state.providerStages],
    logs: [...state.logs]
  };
}
