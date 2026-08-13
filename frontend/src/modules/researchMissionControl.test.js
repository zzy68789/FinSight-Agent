import assert from 'node:assert/strict';
import test from 'node:test';
import { replayAgentEvents } from './agentEventProjection.js';
import { deriveMissionControlView } from './researchMissionControl.js';

test('Mission Control 从 canonical 事件生成运行摘要且不计算伪进度', () => {
  const projection = replayAgentEvents([
    { step: 'run_created', data: { eventId: 'e1', taskId: 12, ticker: '600519.SH', researchQuestion: '解释毛利率变化', comparisonTickers: ['000858.SZ'] } },
    { step: 'plan_created', data: { eventId: 'e2', plan: { goal: '解释毛利率变化', unresolvedQuestions: ['收入结构'] } } },
    { step: 'tool_started', data: { eventId: 'e3', turnNo: 2, toolName: 'fetch_financial_data' } },
    { step: 'tool_completed', data: { eventId: 'e4', turnNo: 2, toolName: 'fetch_financial_data', summary: '新增证据', result: { evidence: [{ issueCode: '' }, { issueCode: 'DATA_MISSING' }] } } }
  ]);
  const view = deriveMissionControlView(projection, true);
  assert.equal(view.status, 'RUNNING');
  assert.equal(view.goal, '解释毛利率变化');
  assert.equal(view.latestTool.name, 'fetch_financial_data');
  assert.equal(view.evidenceCount, 2);
  assert.equal(view.effectiveEvidenceCount, 1);
  assert.deepEqual(projection.requestSummary.comparisonTickers, ['000858.SZ']);
  assert.equal('progressPercent' in view, false);
});

test('Mission Control 明确区分完成与受控停止', () => {
  const completed = deriveMissionControlView(replayAgentEvents([
    { step: 'run_completed', data: { eventId: 'done', taskId: 7, turnCount: 6, toolCallCount: 9 } }
  ]), false);
  assert.equal(completed.status, 'COMPLETED');
  assert.equal(completed.runStats.toolCallCount, 9);

  const stopped = deriveMissionControlView(replayAgentEvents([
    { step: 'run_stopped', data: { eventId: 'stop', taskId: 8, reason: 'EVIDENCE_RECOVERY_EXHAUSTED' } }
  ]), false);
  assert.equal(stopped.status, 'STOPPED');
  assert.equal(stopped.stopReason, 'EVIDENCE_RECOVERY_EXHAUSTED');
});

test('SSE 暂时断开时保留 canonical 非终态任务的运行身份', () => {
  const projection = replayAgentEvents([
    { step: 'run_created', data: { eventId: 'created', taskId: 18 } },
    { step: 'tool_started', data: { eventId: 'tool', taskId: 18, toolName: 'search_public_evidence' } }
  ]);
  const view = deriveMissionControlView(projection, false);
  assert.equal(view.status, 'RUNNING');
  assert.equal(view.taskId, 18);
});
