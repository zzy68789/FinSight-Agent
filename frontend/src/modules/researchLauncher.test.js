import assert from 'node:assert/strict';
import test from 'node:test';
import {
  createLatestRequestGate,
  appendComparisonSecurity,
  filterComparisonCandidates,
  RESEARCH_READINESS,
  researchIntentOptions,
  resolveResearchReadiness,
  suggestedResearchQuestions
} from './researchLauncher.js';

const selectedEquity = {
  ticker: '600519',
  fullCode: '600519.SH',
  companyName: '贵州茅台',
  assetType: 'EQUITY'
};

test('研究准备状态按证券、问题和文档依次推进', () => {
  assert.equal(resolveResearchReadiness({}).status, RESEARCH_READINESS.WAITING_SUBJECT);
  assert.equal(resolveResearchReadiness({
    subjectQuery: '贵州茅台',
    isResolving: true
  }).status, RESEARCH_READINESS.RESOLVING_SUBJECT);
  assert.equal(resolveResearchReadiness({
    subjectQuery: '贵州茅台',
    candidateCount: 2
  }).status, RESEARCH_READINESS.SELECTING_SUBJECT);
  assert.equal(resolveResearchReadiness({
    subjectQuery: '贵州茅台',
    selectedSecurity: selectedEquity
  }).status, RESEARCH_READINESS.WAITING_QUESTION);
  assert.equal(resolveResearchReadiness({
    subjectQuery: '贵州茅台',
    selectedSecurity: selectedEquity,
    researchQuestion: '分析盈利质量',
    searchMode: 'document'
  }).status, RESEARCH_READINESS.WAITING_DOCUMENT);
});

test('全部输入就绪后允许提交，提交中阻止重复请求', () => {
  const readyState = {
    subjectQuery: '600519',
    selectedSecurity: selectedEquity,
    researchQuestion: '解释近期财务变化与主要风险',
    searchMode: 'hybrid',
    uploadedFileCount: 0,
    isDocumentReady: true
  };
  const ready = resolveResearchReadiness(readyState);
  assert.equal(ready.status, RESEARCH_READINESS.READY);
  assert.equal(ready.canSubmit, true);

  const submitting = resolveResearchReadiness({ ...readyState, isSubmitting: true });
  assert.equal(submitting.status, RESEARCH_READINESS.SUBMITTING);
  assert.equal(submitting.canSubmit, false);
});

test('股票和 ETF 只暴露各自合法的专属研究意图', () => {
  const equityValues = researchIntentOptions('EQUITY').map(item => item.value);
  const etfValues = researchIntentOptions('ETF').map(item => item.value);
  assert.ok(equityValues.includes('FINANCIAL_QUALITY'));
  assert.ok(!equityValues.includes('ETF_TRACKING'));
  assert.ok(etfValues.includes('ETF_TRACKING'));
  assert.ok(!etfValues.includes('FINANCIAL_QUALITY'));
});

test('建议问题使用中性研究措辞', () => {
  const questions = suggestedResearchQuestions(selectedEquity, 'VALUATION_RISK');
  assert.equal(questions.length, 3);
  assert.ok(questions.every(question => question.includes('贵州茅台')));
  assert.ok(questions.every(question => !/(买入|卖出|仓位|目标收益)/.test(question)));
});

test('证券搜索只接受最后一次输入对应的异步响应', () => {
  const gate = createLatestRequestGate();
  const oldRequest = gate.issue();
  const latestRequest = gate.issue();
  assert.equal(gate.isCurrent(oldRequest), false);
  assert.equal(gate.isCurrent(latestRequest), true);
  gate.invalidate();
  assert.equal(gate.isCurrent(latestRequest), false);
});

test('可比证券候选排除主证券、跨资产类型和重复项', () => {
  const candidates = [
    selectedEquity,
    { fullCode: '000858.SZ', companyName: '五粮液', assetType: 'EQUITY' },
    { fullCode: '510300.SH', companyName: '沪深300ETF', assetType: 'ETF' }
  ];
  const selected = [{ fullCode: '000858.SZ', companyName: '五粮液', assetType: 'EQUITY' }];
  assert.deepEqual(filterComparisonCandidates(candidates, selectedEquity, selected), []);
});

test('可比证券只追加已确认同类候选且最多三个', () => {
  const peers = [
    { fullCode: '000858.SZ', assetType: 'EQUITY' },
    { fullCode: '000568.SZ', assetType: 'EQUITY' },
    { fullCode: '600809.SH', assetType: 'EQUITY' },
    { fullCode: '603369.SH', assetType: 'EQUITY' }
  ];
  const selected = peers.reduce(
    (current, candidate) => appendComparisonSecurity(current, candidate, selectedEquity),
    []
  );
  assert.deepEqual(selected.map(item => item.fullCode), ['000858.SZ', '000568.SZ', '600809.SH']);
  assert.equal(appendComparisonSecurity(selected, selectedEquity, selectedEquity).length, 3);
});
