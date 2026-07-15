// frontend/src/services/api.js

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
  if (!response.ok) {
      throw new Error(`请求失败：${response.status}`);
  }
  const payload = await response.json();
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

async function streamSse(path, payload, onData, onDone, onError) {
  try {
      const response = await fetch(`${API_BASE}${path}`, withAuth({
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify(payload),
      }));

      if (!response.ok) throw new Error('网络异常，请稍后重试');

      const reader = response.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';

      while (true) {
          const { done, value } = await reader.read();
          if (done) break;
          buffer += decoder.decode(value, { stream: true });
          const lines = buffer.split('\n');
          buffer = lines.pop() || '';
          for (const line of lines) {
              if (line.startsWith('data:')) {
                  const dataStr = line.slice(5).trim();
                  if (dataStr === '[DONE]') {
                      onDone(); return;
                  }
                  try {
                      const event = JSON.parse(dataStr);
                      if (event.step === 'error') {
                          throw new Error(event.data?.message || '任务执行失败，请稍后重试');
                      }
                      onData(event);
                  } catch(e){
                      if (!(e instanceof SyntaxError)) throw e;
                  }
              }
          }
      }
      if (buffer.startsWith('data:')) {
          const dataStr = buffer.slice(5).trim();
          if (dataStr === '[DONE]') {
              onDone(); return;
          }
          try {
              const event = JSON.parse(dataStr);
              if (event.step === 'error') {
                  throw new Error(event.data?.message || '任务执行失败，请稍后重试');
              }
              onData(event);
          } catch(e){
              if (!(e instanceof SyntaxError)) throw e;
          }
      }
  } catch (error) { onError(error); }
}

export async function streamStockReport(ticker, search_mode, report_period, onData, onDone, onError, threadId = SESSION_THREAD_ID) {
  return streamSse('/stock-reports', {
      ticker,
      search_mode,
      report_period,
      thread_id: threadId
  }, onData, onDone, onError);
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
