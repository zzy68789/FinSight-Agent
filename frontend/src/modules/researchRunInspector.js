/**
 * 安全解析 Agent 回放接口返回的序列化字段。
 *
 * @param {unknown} value JSON 字符串或已经解析的对象。
 * @param {unknown} fallback 解析失败时的回退值。
 * @returns {unknown} 解析结果。
 */
export function safeParseResearchJson(value, fallback = null) {
  if (value && typeof value === 'object') return value;
  if (typeof value !== 'string' || !value.trim()) return fallback;
  try {
    return JSON.parse(value);
  } catch {
    return fallback;
  }
}

/**
 * 将持久化回放转换为运行检查器可直接消费的结构。
 *
 * @param {object|null} replay 报告回放响应。
 * @returns {{snapshot: object, evidence: object[], metrics: object[]}} 检查器数据。
 */
export function parseResearchReplay(replay) {
  const parseList = values => (Array.isArray(values) ? values : [])
    .map(value => safeParseResearchJson(value, null))
    .filter(value => value && typeof value === 'object');

  return {
    snapshot: safeParseResearchJson(replay?.snapshotJson, {}) || {},
    evidence: parseList(replay?.evidenceJson),
    metrics: parseList(replay?.metricJson)
  };
}

/**
 * 优先使用持久化回放数据，未加载回放时使用实时 Agent 投影。
 *
 * @param {object} projection 实时或历史事件投影。
 * @param {object|null} replay 回放响应。
 * @returns {{snapshot: object, evidence: object[], metrics: object[]}} 当前检查器数据。
 */
export function resolveResearchInspectorData(projection = {}, replay = null) {
  const persisted = parseResearchReplay(replay);
  return {
    snapshot: Object.keys(persisted.snapshot).length ? persisted.snapshot : projection.snapshotSummary || {},
    evidence: persisted.evidence.length ? persisted.evidence : projection.evidence || [],
    metrics: persisted.metrics.length ? persisted.metrics : projection.metrics || []
  };
}

/** 返回证据有效性与来源分布摘要。 */
export function summarizeResearchEvidence(evidence = []) {
  const sourceCounts = new Map();
  let effective = 0;
  let conflicts = 0;

  evidence.forEach(item => {
    const sourceType = item?.sourceType || 'UNKNOWN';
    sourceCounts.set(sourceType, (sourceCounts.get(sourceType) || 0) + 1);
    if (!String(item?.issueCode || '').trim()) effective += 1;
    if (item?.issueCode === 'EVIDENCE_CONFLICT') conflicts += 1;
  });

  return {
    total: evidence.length,
    effective,
    missing: evidence.length - effective,
    conflicts,
    sources: [...sourceCounts.entries()].map(([sourceType, count]) => ({ sourceType, count }))
  };
}
