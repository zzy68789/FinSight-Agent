import test from 'node:test';
import assert from 'node:assert/strict';
import {
  parseReportExportFilename,
  REPORT_EXPORT_FORMATS,
  saveReportArtifact
} from './reportExport.js';

test('导出格式只保留统一的 PDF Word 和 Markdown', () => {
  assert.deepEqual(REPORT_EXPORT_FORMATS.map(item => item.value), ['pdf', 'docx', 'md']);
});

test('优先解析 UTF-8 文件名并兼容普通文件名', () => {
  assert.equal(
    parseReportExportFilename("attachment; filename*=UTF-8''report-thread-1-v3.pdf"),
    'report-thread-1-v3.pdf'
  );
  assert.equal(
    parseReportExportFilename('attachment; filename="report-thread-2-v4.docx"'),
    'report-thread-2-v4.docx'
  );
});

test('浏览器下载使用服务端文件名并始终回收对象地址', () => {
  const clicked = [];
  const appended = [];
  const revoked = [];
  const link = {
    href: '',
    download: '',
    hidden: false,
    click: () => clicked.push(true),
    remove: () => appended.push('removed')
  };
  const documentRef = {
    createElement: () => link,
    body: { appendChild: item => appended.push(item) }
  };
  const urlApi = {
    createObjectURL: () => 'blob:report',
    revokeObjectURL: value => revoked.push(value)
  };

  saveReportArtifact(
    { blob: new Blob(['report']), filename: 'report-thread-1-v3.md' },
    { documentRef, urlApi }
  );

  assert.equal(link.download, 'report-thread-1-v3.md');
  assert.deepEqual(clicked, [true]);
  assert.deepEqual(revoked, ['blob:report']);
  assert.equal(appended[0], link);
  assert.equal(appended[1], 'removed');
});
