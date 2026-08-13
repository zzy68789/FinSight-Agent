// frontend/src/services/api.js

import { consumeSseChunk } from '../modules/sseEventStream.js';

const API_BASE = "http://localhost:8000/api";
let authToken = localStorage.getItem('finsight_token') || '';

function generateUUID() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
      var r = Math.random() * 16 | 0, v = c == 'x' ? r : (r & 0x3 | 0x8);
      return v.toString(16);
  });
}

// 会话级 ID：页面一刷新就重置，满足"单次会话记忆"需求
const SESSION_THREAD_ID = generateUUID();

export const currentThreadId = SESSION_THREAD_ID;

async function requestJson(path, options = {}) {
  const response = await fetch(`${API_BASE}${path}`, withAuth(options));
  const payload = await response.json().catch(() => null);
  if (!response.ok) {
      throw new Error(payload?.message || payload?.detail || `请求失败：${response.status}`);
  }
  if (payload && typeof payload === 'object' && 'code' in payload) {
      if (payload.code !== 0) {
          throw new Error(payload.message || '请求失败');
      }
      return payload.data;
  }
  return payload;
}

function withAuth(options = {}) {
  const headers = new Headers(options.headers || {});
  if (authToken) {
      headers.set('Authorization', `Bearer ${authToken}`);
  }
  return { ...options, headers };
}

export function setAuthToken(token) {
  authToken = token || '';
  if (authToken) {
      localStorage.setItem('finsight_token', authToken);
  } else {
      localStorage.removeItem('finsight_token');
  }
}

export async function login(username, password) {
  const data = await requestJson('/auth/login', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, password })
  });
  setAuthToken(data.token);
  return data;
}

export async function register(username, email, password) {
  const data = await requestJson('/auth/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ username, email, password })
  });
  setAuthToken(data.token);
  return data;
}

export async function getCurrentUser() {
  return requestJson('/auth/me');
}

/**
 * 按代码或名称搜索当前系统支持的 A 股和 ETF。
 *
 * @param {string} query 用户输入的证券代码或名称。
 * @returns {Promise<Array<object>>} 待用户确认的证券候选。
 */
export async function searchSecurities(query) {
  const params = new URLSearchParams({ query: String(query || '').trim() });
  return requestJson(`/securities/search?${params.toString()}`);
}
/**
 * 批量上传文件
 * @param {Array<File>} files - 文件对象数组
 */
export async function uploadFiles(files) {
    const formData = new FormData();
    // 遍历文件数组，把它们都塞进 'files' 字段里
    files.forEach(file => {
        formData.append('files', file);
    });

    // 发送 POST 请求到 /upload
    const response = await fetch(`${API_BASE}/upload`, withAuth({
        method: "POST",
        body: formData
    }));
    
    if (!response.ok) {
        const errorData = await response.json();
        throw new Error(errorData.detail || '上传失败');
    }
    
    return await response.json();
}
export async function clearContext() {
  const response = await fetch(`${API_BASE}/clear`, withAuth({
      method: "POST"
  }));
  if (!response.ok) throw new Error('清理上下文失败');
  return await response.json();
}

export async function listTasks({ page = 1, size = 10, status = '', keyword = '' } = {}) {
  const params = new URLSearchParams({
      page: String(page),
      size: String(size)
  });
  if (status) params.set('status', status);
  if (keyword) params.set('keyword', keyword);
  return requestJson(`/tasks?${params.toString()}`);
}

export async function getTask(taskId) {
  return requestJson(`/tasks/${taskId}`);
}

export async function getTaskLogs(taskId) {
  return requestJson(`/tasks/${taskId}/logs`);
}

export async function getThreadReports(threadId = SESSION_THREAD_ID) {
  return requestJson(`/threads/${encodeURIComponent(threadId)}/reports`);
}

export async function listReports({ keyword = '', favoriteOnly = false } = {}) {
  const params = new URLSearchParams({
      favoriteOnly: String(Boolean(favoriteOnly))
  });
  if (keyword) params.set('keyword', keyword);
  return requestJson(`/reports?${params.toString()}`);
}

export async function getReport(reportId) {
  return requestJson(`/reports/${reportId}`);
}

export async function exportReport(reportId, format = 'pdf') {
  const response = await fetch(`${API_BASE}/reports/${reportId}/export?format=${encodeURIComponent(format)}`, withAuth());
  if (!response.ok) {
      throw new Error(`导出失败：${response.status}`);
  }
  const disposition = response.headers.get('Content-Disposition') || '';
  const filenameMatch = disposition.match(/filename="?([^"]+)"?/i);
  return {
      blob: await response.blob(),
      filename: filenameMatch ? filenameMatch[1] : `report.${format}`
  };
}

export async function updateReportFavorite(reportId, favorite) {
  return requestJson(`/reports/${reportId}/favorite?favorite=${String(Boolean(favorite))}`, {
      method: 'POST'
  });
}

export async function deleteReport(reportId) {
  return requestJson(`/reports/${reportId}`, {
      method: 'DELETE'
  });
}

export async function indexReportToKnowledgeBase(reportId) {
  return requestJson(`/reports/${reportId}/knowledge-base`, {
      method: 'POST'
  });
}

export async function adminListUsers({ keyword = '' } = {}) {
  const params = new URLSearchParams();
  if (keyword) params.set('keyword', keyword);
  const query = params.toString();
  return requestJson(`/admin/users${query ? `?${query}` : ''}`);
}

export async function adminUpdateUserRole(userId, role) {
  return requestJson(`/admin/users/${userId}/role?role=${encodeURIComponent(role)}`, {
      method: 'PATCH'
  });
}

export async function adminUpdateUserStatus(userId, status) {
  return requestJson(`/admin/users/${userId}/status?status=${encodeURIComponent(status)}`, {
      method: 'PATCH'
  });
}

export async function adminListTasks({ status = '', ownerId = '', keyword = '' } = {}) {
  const params = new URLSearchParams();
  if (status) params.set('status', status);
  if (ownerId) params.set('ownerId', String(ownerId));
  if (keyword) params.set('keyword', keyword);
  const query = params.toString();
  return requestJson(`/admin/tasks${query ? `?${query}` : ''}`);
}

export async function adminGetTaskLogs(taskId) {
  return requestJson(`/admin/tasks/${taskId}/logs`);
}

export async function adminListReports({ ownerId = '', keyword = '' } = {}) {
  const params = new URLSearchParams();
  if (ownerId) params.set('ownerId', String(ownerId));
  if (keyword) params.set('keyword', keyword);
  const query = params.toString();
  return requestJson(`/admin/reports${query ? `?${query}` : ''}`);
}

export async function adminDeleteReport(reportId) {
  return requestJson(`/admin/reports/${reportId}`, {
      method: 'DELETE'
  });
}

export async function adminSystemHealth() {
  return requestJson('/admin/system/health');
}

async function streamSse(path, payload, onData, onDone, onError, reconnectPath) {
  let taskId = null;
  let lastSequence = 0;
  let reconnectAttempts = 0;
  let request = {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(payload)
  };
  try {
    while (true) {
      try {
        const response = await fetch(`${API_BASE}${path}`, withAuth(request));
        if (!response.ok) {
          const error = new Error(`事件流请求失败：${response.status}`);
          error.nonRetryable = response.status >= 400 && response.status < 500;
          throw error;
        }
        const outcome = await consumeSseResponse(response, event => {
          const sequence = Number(event?.data?.sequence || 0);
          if (Number.isFinite(sequence)) lastSequence = Math.max(lastSequence, sequence);
          taskId = event?.data?.taskId || taskId;
          onData(event);
        }, messageId => {
          const parsed = Number(messageId || 0);
          if (Number.isFinite(parsed)) lastSequence = Math.max(lastSequence, parsed);
        });
        if (outcome.completed) {
          onDone();
          return;
        }
      } catch (error) {
        if (error.nonRetryable || !taskId || !reconnectPath || reconnectAttempts >= 5) throw error;
      }
      if (!taskId || !reconnectPath || reconnectAttempts >= 5) {
        throw new Error('事件流意外中断，且无法恢复任务序号');
      }
      reconnectAttempts += 1;
      await new Promise(resolve => setTimeout(resolve, Math.min(5000, reconnectAttempts * 500)));
      path = reconnectPath(taskId);
      request = {
        method: 'GET',
        headers: { 'Last-Event-ID': String(lastSequence) }
      };
    }
  } catch (error) {
    onError(error);
  }
}

async function consumeSseResponse(response, onData, onMessageId) {
  const reader = response.body.getReader();
  const decoder = new TextDecoder('utf-8');
  let buffer = '';
  while (true) {
    const { done, value } = await reader.read();
    const parsed = consumeSseChunk(buffer, done ? decoder.decode() : decoder.decode(value, { stream: true }), done);
    buffer = parsed.buffer;
    for (const message of parsed.messages) {
      onMessageId(message.id);
      if (message.data === '[DONE]') return { completed: true };
      try {
        const event = JSON.parse(message.data);
        if (event.step === 'error') {
          const error = new Error(event.data?.message || '任务执行失败，请稍后重试');
          error.nonRetryable = true;
          throw error;
        }
        onData(event);
      } catch (error) {
        if (!(error instanceof SyntaxError)) throw error;
      }
    }
    if (done) return { completed: false };
  }
}

export async function streamStockReport(ticker, search_mode, report_period, onData, onDone, onError, threadId = SESSION_THREAD_ID) {
  return streamSse('/stock-reports', {
      ticker,
      search_mode,
      report_period,
      thread_id: threadId
  }, onData, onDone, onError);
}

export async function streamResearchRun(request, onData, onDone, onError, threadId = SESSION_THREAD_ID) {
  return streamSse('/research-runs', {
      ...request,
      thread_id: request.thread_id || threadId
  }, onData, onDone, onError, taskId => `/research-runs/${taskId}/events`);
}

export async function getResearchRunTrace(taskId) {
  return requestJson(`/research-runs/${taskId}/trace`);
}

export async function retryResearchRun(taskId) {
  return requestJson(`/research-runs/${taskId}/retry`, { method: 'POST' });
}

export async function saveStockFeedback(taskId, feedbackType, detail = '') {
  return requestJson(`/stock-reports/${taskId}/feedback`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({
          feedback_type: feedbackType,
          detail
      })
  });
}

export async function getStockReplay(taskId) {
  return requestJson(`/stock-reports/${taskId}/replay`);
}

export async function getStockTrace(taskId) {
  return requestJson(`/stock-reports/${taskId}/trace`);
}

export async function retryStockReport(taskId) {
  return requestJson(`/stock-reports/${taskId}/retry`, { method: 'POST' });
}
