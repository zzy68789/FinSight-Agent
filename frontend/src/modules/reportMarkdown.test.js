import test from 'node:test';
import assert from 'node:assert/strict';
import { createReportMarkdown, safeReportLink } from './reportMarkdown.js';

test('报告 Markdown 将原始 HTML 按文本展示', () => {
  const html = createReportMarkdown().render('<img src=x onerror="alert(1)">');

  assert.match(html, /&lt;img src=x onerror=/);
  assert.doesNotMatch(html, /<img/);
});

test('报告链接只允许证据锚点和 HTTP(S)', () => {
  const markdown = createReportMarkdown();

  assert.equal(safeReportLink('#evidence-1'), '#evidence-1');
  assert.equal(safeReportLink('https://example.com/source'), 'https://example.com/source');
  assert.equal(safeReportLink('javascript:alert(1)'), '');
  assert.doesNotMatch(markdown.render('[危险链接](javascript:alert(1))'), /href="javascript:/);
  assert.match(markdown.render('[来源](https://example.com)'), /rel="noopener noreferrer"/);
});
