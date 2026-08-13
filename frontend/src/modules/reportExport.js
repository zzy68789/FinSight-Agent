export const REPORT_EXPORT_FORMATS = Object.freeze([
  Object.freeze({ value: 'pdf', label: 'PDF', description: '便于归档与分享' }),
  Object.freeze({ value: 'docx', label: 'Word', description: '便于继续编辑' }),
  Object.freeze({ value: 'md', label: 'Markdown', description: '保留原始正文' })
]);

/** 从标准 Content-Disposition 响应头中读取服务端生成的报告文件名。 */
export function parseReportExportFilename(contentDisposition, fallback = 'report.pdf') {
  const value = String(contentDisposition || '');
  const encodedMatch = value.match(/filename\*=UTF-8''([^;]+)/i);
  if (encodedMatch) {
    try {
      return decodeURIComponent(encodedMatch[1].trim());
    } catch {
      return encodedMatch[1].trim();
    }
  }
  const plainMatch = value.match(/filename="?([^";]+)"?/i);
  return plainMatch ? plainMatch[1].trim() : fallback;
}

/** 把后端返回的同一报告版本制品交给浏览器下载，不修改报告或任务状态。 */
export function saveReportArtifact(artifact, dependencies = {}) {
  if (!artifact?.blob || !artifact?.filename) {
    throw new Error('导出响应缺少文件内容或文件名');
  }
  const documentRef = dependencies.documentRef || globalThis.document;
  const urlApi = dependencies.urlApi || globalThis.URL;
  if (!documentRef?.createElement || !urlApi?.createObjectURL) {
    throw new Error('当前环境不支持文件下载');
  }

  const objectUrl = urlApi.createObjectURL(artifact.blob);
  const link = documentRef.createElement('a');
  try {
    link.href = objectUrl;
    link.download = artifact.filename;
    link.hidden = true;
    documentRef.body?.appendChild(link);
    link.click();
  } finally {
    link.remove?.();
    urlApi.revokeObjectURL(objectUrl);
  }
}
