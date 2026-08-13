import { agentEventTypeLabel } from './agentEventProjection.js';

const TERMINAL_COMPLETED = new Set(['run_completed', 'done']);

/**
 * 从 canonical Agent Event 投影生成 Mission Control 展示模型。
 *
 * @param {object} projection Agent 事件归约后的权威页面状态。
 * @param {boolean} isRunning SSE 运行是否仍在进行。
 * @returns {object} 不包含猜测进度的运行摘要。
 */
export function deriveMissionControlView(projection = {}, isRunning = false) {
  const events = Array.isArray(projection.events) ? projection.events : [];
  const currentStep = projection.currentStep || 'idle';
  const stopped = currentStep === 'run_stopped';
  const completed = TERMINAL_COMPLETED.has(currentStep)
    || (!isRunning && projection.stopReason === 'COMPLETED');
  const hasActiveCanonicalRun = Boolean(projection.taskId)
    && currentStep !== 'idle'
    && !stopped
    && !completed;
  const status = stopped
    ? 'STOPPED'
    : completed
      ? 'COMPLETED'
      : isRunning || hasActiveCanonicalRun ? 'RUNNING' : 'IDLE';
  const evidence = Array.isArray(projection.evidence) ? projection.evidence : [];
  const effectiveEvidenceCount = evidence.filter(item => !String(item?.issueCode || '').trim()).length;
  const latestEvent = events.at(-1) || null;

  return {
    status,
    statusLabel: missionStatusLabel(status),
    currentStep,
    currentStepLabel: agentEventTypeLabel(currentStep),
    taskId: projection.taskId || null,
    requestSummary: projection.requestSummary || null,
    goal: projection.plan?.goal || '',
    unresolvedQuestions: projection.plan?.unresolvedQuestions || [],
    latestTool: projection.latestTool || null,
    evidenceCount: evidence.length,
    effectiveEvidenceCount,
    missingEvidenceCount: evidence.length - effectiveEvidenceCount,
    replanCount: Number(projection.replanCount || 0),
    evidenceRecoveryCount: Number(projection.evidenceRecoveryCount || 0),
    qualityGateDecision: projection.qualityGateDecision || null,
    stopReason: stopped ? projection.stopReason || latestEvent?.reason || latestEvent?.summary || '' : '',
    runStats: projection.runStats || null,
    latestEvent,
    stages: buildStageRail(currentStep, projection.completedSteps || [])
  };
}

/** 返回任务状态的用户可读文本。 */
export function missionStatusLabel(status) {
  return ({
    IDLE: '等待研究任务',
    RUNNING: 'Agent 运行中',
    COMPLETED: '报告已发布',
    STOPPED: '任务受控停止'
  })[status] || status;
}

function buildStageRail(currentStep, completedSteps) {
  const groups = [
    { id: 'CONTEXT', label: '运行上下文', events: ['run_created'] },
    { id: 'PLAN', label: '研究规划', events: ['plan_created', 'replanned'] },
    { id: 'ACT', label: '工具与观察', events: ['tool_started', 'tool_completed', 'evidence_recovery_progress'] },
    { id: 'SYNTH', label: '报告综合', events: ['synthesis_started', 'synthesis_completed'] },
    { id: 'GUARD', label: '质量门禁', events: ['review_completed', 'quality_gate_routed'] },
    { id: 'STOP', label: '结束', events: ['run_completed', 'run_stopped', 'done'] }
  ];
  const seen = new Set(completedSteps);
  return groups.map(group => ({
    ...group,
    active: group.events.includes(currentStep),
    completed: group.events.some(event => seen.has(event)) && !group.events.includes(currentStep)
  }));
}
