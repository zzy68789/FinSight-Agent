/**
 * 将后端状态码转换为界面文案。
 *
 * @param {string} status 后端状态码
 * @returns {string} 中文状态文案
 */
export function statusLabel(status) {
  const value = (status || '').toUpperCase();
  const labels = {
    RUNNING: '运行中',
    COMPLETED: '已完成',
    FAILED: '失败',
    CANCELLED: '已取消',
    INSUFFICIENT_EVIDENCE: '证据不足',
    SUCCESS: '成功',
    PASS: '通过',
    FAIL: '未通过',
    ACTIVE: '启用',
    DISABLED: '停用',
    READY: '就绪',
    OK: '正常'
  };
  return labels[value] || status || '-';
}

/**
 * 将检索模式转换为界面文案。
 *
 * @param {string} mode 检索模式
 * @returns {string} 中文检索模式
 */
export function searchModeLabel(mode) {
  const value = (mode || '').toLowerCase();
  if (value === 'document') return '仅文档';
  if (value === 'hybrid') return '混合检索';
  if (value === 'stock-hybrid') return '股票混合';
  if (value === 'stock-document') return '股票文档';
  if (value === 'stock-web') return '股票联网';
  return mode || '-';
}

/**
 * 将 Agent 步骤名转换为界面文案。
 *
 * @param {string} step Agent 步骤名
 * @returns {string} 中文步骤名
 */
export function stepNameLabel(step) {
  const value = (step || '').toLowerCase();
  const labels = {
    stock_resolve: '证券解析',
    data_snapshot: '数据快照',
    metric_engine: '指标计算',
    risk_assessment: '风险评分',
    evidence_collect: '证据账本',
    bull_bear_research: '多空研究 Agent',
    writer: '撰写',
    reviewer: '质检',
    evaluation: '评测'
  };
  return labels[value] || step || '-';
}

/**
 * 格式化任务和报告时间。
 *
 * @param {string|number|Date} value 时间值
 * @returns {string} 中文短时间
 */
export function formatDate(value) {
  if (!value) return '-';
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit'
  }).format(new Date(value));
}

/**
 * 返回状态徽标的 Tailwind 样式。
 *
 * @param {string} status 后端状态码
 * @returns {string} 状态徽标样式
 */
export function statusStyles(status) {
  const value = (status || '').toUpperCase();
  if (value === 'COMPLETED' || value === 'SUCCESS' || value === 'PASS') {
    return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
  }
  if (value === 'FAILED' || value === 'FAIL') {
    return 'bg-rose-50 text-rose-700 ring-rose-200';
  }
  if (value === 'CANCELLED' || value === 'INSUFFICIENT_EVIDENCE') {
    return 'bg-amber-50 text-amber-800 ring-amber-200';
  }
  if (value === 'RUNNING') return 'bg-blue-50 text-blue-700 ring-blue-200';
  return 'bg-slate-100 text-slate-600 ring-slate-200';
}
