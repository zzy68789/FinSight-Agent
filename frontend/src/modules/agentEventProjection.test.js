import test from 'node:test';
import assert from 'node:assert/strict';
import { createAgentEventProjection, reduceAgentEvent, replayAgentEvents } from './agentEventProjection.js';

test('重复 SSE 事件保持幂等', () => {
  const event = { step: 'tool_completed', data: { eventId: 'e-1', toolName: 'resolve_security', summary: '完成' } };
  const once = reduceAgentEvent(createAgentEventProjection(), event);
  const twice = reduceAgentEvent(once, event);
  assert.equal(twice.events.length, 1);
  assert.equal(twice.logs.length, 1);
});

test('历史步骤日志与实时 SSE 使用同一投影结果', () => {
  const live = replayAgentEvents([
    { step: 'run_created', data: { eventId: 'e-1', taskId: 7, budget: { maxTurns: 8 } } },
    { step: 'run_stopped', data: { eventId: 'e-2', taskId: 7, reason: '证据不足' } }
  ]);
  const trace = replayAgentEvents([
    { stepName: 'run_created', outputSnapshot: JSON.stringify({ eventId: 'e-1', taskId: 7, budget: { maxTurns: 8 } }) },
    { stepName: 'run_stopped', outputSnapshot: JSON.stringify({ eventId: 'e-2', taskId: 7, reason: '证据不足' }) }
  ]);
  assert.deepEqual(trace.events, live.events);
  assert.deepEqual(trace.logs, live.logs);
});

test('版本化 outbox 事件可重放并按任务序号去重', () => {
  const event = {
    schemaVersion: 'agent-event-v1',
    taskId: 7,
    threadId: 'thread-1',
    sequence: 3,
    eventId: 'event-3',
    turnNo: 2,
    type: 'tool_completed',
    status: 'SUCCESS',
    payload: {
      toolName: 'calculate_financial_metrics',
      summary: '指标已计算',
      result: { payloadType: 'metrics', metrics: [{ metricName: 'ROE' }] }
    }
  };
  const projection = replayAgentEvents([event, { ...event, eventId: 'retry-event' }]);
  assert.equal(projection.events.length, 1);
  assert.equal(projection.metrics[0].metricName, 'ROE');
  assert.equal(projection.currentStep, 'tool_completed');
});
