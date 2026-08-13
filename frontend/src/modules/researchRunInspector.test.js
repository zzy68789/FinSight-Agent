import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseResearchReplay,
  resolveResearchInspectorData,
  summarizeResearchEvidence
} from './researchRunInspector.js';

test('回放字段逐条解析且忽略损坏记录', () => {
  const parsed = parseResearchReplay({
    snapshotJson: '{"subject":{"fullCode":"600519.SH"}}',
    evidenceJson: ['{"sourceType":"TUSHARE"}', 'not-json'],
    metricJson: ['{"metricName":"ROE","status":"OK"}']
  });

  assert.equal(parsed.snapshot.subject.fullCode, '600519.SH');
  assert.equal(parsed.evidence.length, 1);
  assert.equal(parsed.metrics[0].metricName, 'ROE');
});

test('持久化回放优先于实时投影', () => {
  const data = resolveResearchInspectorData(
    { evidence: [{ sourceType: 'LIVE' }], metrics: [{ metricName: 'LIVE' }] },
    { evidenceJson: ['{"sourceType":"PERSISTED"}'], metricJson: ['{"metricName":"ROE"}'] }
  );

  assert.equal(data.evidence[0].sourceType, 'PERSISTED');
  assert.equal(data.metrics[0].metricName, 'ROE');
});

test('证据摘要区分有效、缺失和冲突记录', () => {
  const summary = summarizeResearchEvidence([
    { sourceType: 'TUSHARE' },
    { sourceType: 'TUSHARE', issueCode: 'DATA_MISSING' },
    { sourceType: 'RAG', issueCode: 'EVIDENCE_CONFLICT' }
  ]);

  assert.deepEqual(summary, {
    total: 3,
    effective: 1,
    missing: 2,
    conflicts: 1,
    sources: [
      { sourceType: 'TUSHARE', count: 2 },
      { sourceType: 'RAG', count: 1 }
    ]
  });
});
