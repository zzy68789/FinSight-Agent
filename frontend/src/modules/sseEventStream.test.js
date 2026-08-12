import test from 'node:test';
import assert from 'node:assert/strict';
import { consumeSseChunk } from './sseEventStream.js';

test('跨 chunk 解析 Agent SSE 序号和数据', () => {
  const first = consumeSseChunk('', 'id: 7\nevent: tool_completed\ndata: {"step":"tool_');
  assert.equal(first.messages.length, 0);
  const second = consumeSseChunk(first.buffer, 'completed","data":{"sequence":7}}\n\n');
  assert.deepEqual(second.messages, [{
    id: '7',
    event: 'tool_completed',
    data: '{"step":"tool_completed","data":{"sequence":7}}'
  }]);
});

test('识别结束消息并兼容无 event 字段', () => {
  const result = consumeSseChunk('', 'data: [DONE]\n\n');
  assert.deepEqual(result.messages, [{ id: '', event: '', data: '[DONE]' }]);
});
