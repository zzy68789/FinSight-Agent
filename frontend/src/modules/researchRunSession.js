export const ACTIVE_RESEARCH_RUN_KEY = 'finsight_active_research_run';
export const PENDING_RESEARCH_SUBMISSION_KEY = 'finsight_pending_research_submission';

/** 返回同一请求可复用的客户端幂等意图。 */
export function resolveSubmissionIntent(request, previous = null, idFactory = createClientRequestId) {
  const fingerprint = stableStringify(request || {});
  if (previous?.fingerprint === fingerprint && previous?.clientRequestId) {
    return previous;
  }
  return { clientRequestId: idFactory(), fingerprint };
}

/** 读取尚未收到任务回执的提交意图。 */
export function readPendingSubmission(storage = globalThis.localStorage) {
  return readJson(storage, PENDING_RESEARCH_SUBMISSION_KEY);
}

/** 保存任务创建请求的幂等意图。 */
export function savePendingSubmission(intent, storage = globalThis.localStorage) {
  storage?.setItem(PENDING_RESEARCH_SUBMISSION_KEY, JSON.stringify(intent));
}

/** 清除已经获得权威任务回执的提交意图。 */
export function clearPendingSubmission(storage = globalThis.localStorage) {
  storage?.removeItem(PENDING_RESEARCH_SUBMISSION_KEY);
}

/** 读取刷新后可恢复的权威任务指针。 */
export function readActiveResearchRun(storage = globalThis.localStorage) {
  const value = readJson(storage, ACTIVE_RESEARCH_RUN_KEY);
  if (!value || !Number.isFinite(Number(value.taskId)) || Number(value.taskId) <= 0 || !value.threadId) {
    return null;
  }
  return {
    ...value,
    taskId: Number(value.taskId),
    lastSequence: Math.max(0, Number(value.lastSequence || 0))
  };
}

/** 保存任务、线程、请求快照和最后事件序号。 */
export function saveActiveResearchRun(run, storage = globalThis.localStorage) {
  storage?.setItem(ACTIVE_RESEARCH_RUN_KEY, JSON.stringify({
    ...run,
    taskId: Number(run.taskId),
    lastSequence: Math.max(0, Number(run.lastSequence || 0))
  }));
}

/** 仅在任务匹配时推进本地最后事件序号。 */
export function advanceActiveResearchSequence(taskId, sequence, storage = globalThis.localStorage) {
  const current = readActiveResearchRun(storage);
  const nextSequence = Math.max(0, Number(sequence || 0));
  if (!current || current.taskId !== Number(taskId) || nextSequence <= current.lastSequence) return current;
  const updated = { ...current, lastSequence: nextSequence };
  saveActiveResearchRun(updated, storage);
  return updated;
}

/** 清除已结束或无权访问的活动任务指针。 */
export function clearActiveResearchRun(taskId = null, storage = globalThis.localStorage) {
  const current = readActiveResearchRun(storage);
  if (taskId !== null && current && current.taskId !== Number(taskId)) return;
  storage?.removeItem(ACTIVE_RESEARCH_RUN_KEY);
}

/** 返回历史事件中最大的任务内序号。 */
export function latestResearchSequence(events = []) {
  return (events || []).reduce((maximum, event) => Math.max(
    maximum,
    Number(event?.sequence || event?.payload?.sequence || event?.data?.sequence || 0)
  ), 0);
}

/** 判断数据库任务是否已经进入终态。 */
export function isTerminalResearchStatus(status) {
  return ['COMPLETED', 'FAILED', 'INSUFFICIENT_EVIDENCE', 'CANCELLED'].includes(String(status || '').toUpperCase());
}

function readJson(storage, key) {
  if (!storage) return null;
  try {
    return JSON.parse(storage.getItem(key) || 'null');
  } catch {
    return null;
  }
}

function createClientRequestId() {
  if (globalThis.crypto?.randomUUID) return globalThis.crypto.randomUUID();
  return `request-${Date.now().toString(36)}-${Math.random().toString(36).slice(2, 14)}`;
}

function stableStringify(value) {
  if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
  if (value && typeof value === 'object') {
    return `{${Object.keys(value).sort().map(key => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
  }
  return JSON.stringify(value);
}
