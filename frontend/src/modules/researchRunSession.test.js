import test from 'node:test';
import assert from 'node:assert/strict';
import {
  advanceActiveResearchSequence,
  clearActiveResearchRun,
  isTerminalResearchStatus,
  latestResearchSequence,
  readActiveResearchRun,
  resolveSubmissionIntent,
  saveActiveResearchRun
} from './researchRunSession.js';

test('相同请求复用客户端幂等键且不同请求生成新键', () => {
  const first = resolveSubmissionIntent({ ticker: '600519.SH', question: '盈利质量' }, null, () => 'request-1');
  const repeated = resolveSubmissionIntent({ question: '盈利质量', ticker: '600519.SH' }, first, () => 'request-2');
  const changed = resolveSubmissionIntent({ ticker: '600519.SH', question: '估值风险' }, first, () => 'request-3');

  assert.equal(repeated.clientRequestId, 'request-1');
  assert.equal(changed.clientRequestId, 'request-3');
});

test('活动任务只允许事件序号单调前进并按任务清理', () => {
  const storage = memoryStorage();
  saveActiveResearchRun({ taskId: 19, threadId: 'thread-1', lastSequence: 3 }, storage);
  advanceActiveResearchSequence(19, 8, storage);
  advanceActiveResearchSequence(19, 5, storage);
  advanceActiveResearchSequence(20, 12, storage);
  assert.equal(readActiveResearchRun(storage).lastSequence, 8);

  clearActiveResearchRun(20, storage);
  assert.equal(readActiveResearchRun(storage).taskId, 19);
  clearActiveResearchRun(19, storage);
  assert.equal(readActiveResearchRun(storage), null);
});

test('历史事件序号兼容版本化事件与 SSE 包装', () => {
  assert.equal(latestResearchSequence([
    { sequence: 2 },
    { payload: { sequence: 7 } },
    { data: { sequence: 5 } }
  ]), 7);
});

test('用户取消状态会终止刷新恢复和事件订阅', () => {
  assert.equal(isTerminalResearchStatus('CANCELLED'), true);
  assert.equal(isTerminalResearchStatus('RUNNING'), false);
});

function memoryStorage() {
  const values = new Map();
  return {
    getItem: key => values.get(key) ?? null,
    setItem: (key, value) => values.set(key, String(value)),
    removeItem: key => values.delete(key)
  };
}
