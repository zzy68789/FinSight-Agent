<template>
    <main class="workspace-main mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <aside class="space-y-5 lg:col-span-4">
        <ResearchLauncherSidebar
          v-model:subject-query="subjectQuery"
          v-model:research-intent="researchIntent"
          v-model:research-question="researchQuestion"
          v-model:as-of-date="researchAsOfDate"
          v-model:time-horizon="researchTimeHorizon"
          v-model:research-depth="researchDepth"
          v-model:search-mode="searchMode"
          v-model:comparison-securities="comparisonSecurities"
          :candidates="securityCandidates"
          :selected-security="selectedSecurity"
          :security-state="securitySearchState"
          :security-error="securitySearchError"
          :uploaded-files="uploadedFiles"
          :is-dragging="isDragging"
          :is-document-ready="isDocumentReady"
          :is-submitting="isLoading"
          :readiness="researchReadiness"
          @select-security="selectSecurity"
          @files-selected="processFiles"
          @clear-knowledge-base="clearKnowledgeBase"
          @drag-state-change="isDragging = $event"
          @submit="startStockResearch"
        />

      </aside>

      <section class="space-y-6 lg:col-span-8">
        <div v-if="connectionInterrupted" class="flex flex-col gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-amber-950 sm:flex-row sm:items-center sm:justify-between" role="alert">
          <div>
            <p class="text-sm font-semibold">任务仍在后台执行，但实时事件连接已中断</p>
            <p class="mt-1 text-xs text-amber-800">{{ connectionError || '可以从最后一个已提交事件继续连接，已生成的任务不会丢失。' }}</p>
          </div>
          <div class="flex shrink-0 flex-wrap gap-2">
            <button type="button" class="min-h-9 rounded-md bg-amber-900 px-3 text-xs font-semibold text-white hover:bg-amber-950" @click="reconnectActiveResearchRun">重新连接</button>
            <button type="button" class="min-h-9 rounded-md border border-amber-300 bg-white px-3 text-xs font-semibold text-amber-900 hover:bg-amber-100" @click="emit('open-tasks')">查看任务中心</button>
            <button v-if="latestStockTaskId" type="button" class="min-h-9 rounded-md border border-rose-200 bg-white px-3 text-xs font-semibold text-rose-700 hover:bg-rose-50" @click="cancelActiveResearchRun">取消任务</button>
          </div>
        </div>

        <ResearchMissionControl
          :projection="agentProjection"
          :is-running="isLoading"
          :run-snapshot="activeRunSnapshot"
          :active-tasks="missionTasks"
          :recent-reports="missionReports"
          :records-loading="isMissionRecordsLoading"
          :records-error="missionRecordsError"
          @search-records="searchMissionRecords"
          @refresh-records="loadMissionRecords"
        />

        <ResearchReportPanel
          :report-content="displayedReport"
          :is-running="isLoading"
          :is-typing="isTyping"
          :current-step="currentStep"
          :search-mode="activeRunSnapshot?.searchMode || searchMode"
          :run-snapshot="activeRunSnapshot"
          :report-id="agentProjection.reportId"
          @select-example="researchQuestion = $event"
          @export-error="emit('warning', $event)"
        />

        <ResearchRunInspector
          :projection="agentProjection"
          :replay="stockReplay"
          :trace="stockTrace"
          :is-running="isLoading"
          @feedback="submitStockFeedback"
          @replay="loadStockReplay"
          @clear-replay="stockReplay = null; stockTrace = null"
        />
      </section>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import ResearchLauncherSidebar from '../components/research/ResearchLauncherSidebar.vue';
import ResearchMissionControl from '../components/research/ResearchMissionControl.vue';
import ResearchReportPanel from '../components/research/ResearchReportPanel.vue';
import ResearchRunInspector from '../components/research/ResearchRunInspector.vue';
import { useAgentRunProjection } from '../composables/useAgentRunProjection';
import {
  createLatestRequestGate,
  researchIntentOptions,
  resolveResearchReadiness
} from '../modules/researchLauncher.js';
import {
  advanceActiveResearchSequence,
  clearActiveResearchRun,
  clearPendingSubmission,
  isTerminalResearchStatus,
  latestResearchSequence,
  readActiveResearchRun,
  readPendingSubmission,
  resolveSubmissionIntent,
  saveActiveResearchRun,
  savePendingSubmission
} from '../modules/researchRunSession.js';
import {
  cancelResearchRun,
  clearContext,
  createResearchRun,
  getTask,
  getResearchRunTrace,
  getStockReplay,
  listReports,
  listTasks,
  saveStockFeedback,
  searchSecurities,
  subscribeResearchRun,
  persistCurrentThreadId,
  uploadFiles
} from '../services/api';

const props = defineProps({
  threadId: {
    type: String,
    required: true
  },
  refreshRevision: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['warning', 'completed', 'open-tasks']);

const subjectQuery = ref('');
const selectedSecurity = ref(null);
const securityCandidates = ref([]);
const securitySearchState = ref('idle');
const securitySearchError = ref('');
const securitySearchCompleted = ref(false);
const researchIntent = ref('COMPREHENSIVE');
const researchQuestion = ref('');
const researchAsOfDate = ref(new Date().toISOString().slice(0, 10));
const researchDepth = ref('standard');
const researchTimeHorizon = ref('2Y');
const comparisonSecurities = ref([]);
const {
  agentProjection,
  latestStockTaskId,
  currentStep,
  logs,
  displayedReport,
  isTyping,
  resetAgentRun,
  handleAgentEvent,
  applyAgentTrace,
  bindAgentRun,
  pushAgentLog,
  markAgentRunDone
} = useAgentRunProjection();

const stockReplay = ref(null);
const stockTrace = ref(null);
const isLoading = ref(false);
const uploadedFiles = ref([]);
const isDragging = ref(false);
const isDocumentReady = ref(true);
const searchMode = ref('hybrid');
const activeRunSnapshot = ref(null);
const missionTasks = ref([]);
const missionReports = ref([]);
const missionRecordKeyword = ref('');
const isMissionRecordsLoading = ref(false);
const missionRecordsError = ref('');
const connectionInterrupted = ref(false);
const connectionError = ref('');
let securitySearchTimer = null;
let subscriptionGeneration = 0;
let activeSubscriptionController = null;
const securitySearchGate = createLatestRequestGate();

const researchReadiness = computed(() => resolveResearchReadiness({
  subjectQuery: subjectQuery.value,
  selectedSecurity: selectedSecurity.value,
  isResolving: securitySearchState.value === 'resolving',
  candidateCount: securityCandidates.value.length,
  searchCompleted: securitySearchCompleted.value,
  searchError: securitySearchError.value,
  researchQuestion: researchQuestion.value,
  searchMode: searchMode.value,
  uploadedFileCount: uploadedFiles.value.length,
  isDocumentReady: isDocumentReady.value,
  isSubmitting: isLoading.value
}));

const processFiles = async (files) => {
  const selectedFiles = Array.from(files || []);
  if (selectedFiles.length > 5) {
    window.alert('最多只能上传 5 个文件。');
    return;
  }
  if (selectedFiles.some(file => file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf'))) {
    window.alert('仅支持上传 PDF 文件。');
    return;
  }

  uploadedFiles.value = selectedFiles;
  isDocumentReady.value = selectedFiles.length === 0;
  if (uploadedFiles.value.length === 0) return;

  logs.value.push('[系统] 正在上传 ' + selectedFiles.length + ' 个文档...');
  try {
    const result = await uploadFiles(uploadedFiles.value);
    isDocumentReady.value = true;
    logs.value.push('[系统] 文档已追加到个人知识库，本次索引 ' + result.chunks_stored + ' 个文本块。');
  } catch (error) {
    isDocumentReady.value = true;
    logs.value.push('[错误] 上传失败：' + error.message);
    window.alert('上传失败：' + error.message);
    uploadedFiles.value = [];
  }
};

const clearKnowledgeBase = async () => {
  if (!window.confirm('确认清空当前账号的个人知识库？已保存的报告不会删除。')) return;
  try {
    await clearContext();
    uploadedFiles.value = [];
    isDocumentReady.value = true;
    logs.value.push('[知识库] 已按用户操作清空个人知识库。');
  } catch (error) {
    emit('warning', '清空知识库失败：' + error.message);
  }
};

const selectSecurity = candidate => {
  selectedSecurity.value = candidate;
  securityCandidates.value = [];
  securitySearchCompleted.value = true;
  securitySearchState.value = 'done';
  securitySearchError.value = '';
  const validIntents = researchIntentOptions(candidate?.assetType).map(option => option.value);
  if (!validIntents.includes(researchIntent.value)) {
    researchIntent.value = 'COMPREHENSIVE';
  }
};

const runSecuritySearch = async (query, requestToken) => {
  try {
    const candidates = await searchSecurities(query);
    if (!securitySearchGate.isCurrent(requestToken)) return;
    securityCandidates.value = Array.isArray(candidates) ? candidates : [];
    securitySearchCompleted.value = true;
    securitySearchState.value = 'done';
    if (/^\d{6}(\.(SH|SZ))?$/i.test(query) && securityCandidates.value.length === 1) {
      selectSecurity(securityCandidates.value[0]);
    }
  } catch (error) {
    if (!securitySearchGate.isCurrent(requestToken)) return;
    securityCandidates.value = [];
    securitySearchCompleted.value = true;
    securitySearchState.value = 'error';
    securitySearchError.value = error.message || '证券搜索失败，请稍后重试';
  }
};

const scheduleSecuritySearch = queryValue => {
  const requestToken = securitySearchGate.issue();
  if (securitySearchTimer) clearTimeout(securitySearchTimer);
  selectedSecurity.value = null;
  comparisonSecurities.value = [];
  securityCandidates.value = [];
  securitySearchError.value = '';
  securitySearchCompleted.value = false;

  const query = String(queryValue || '').trim();
  if (query.length < 2) {
    securitySearchState.value = 'idle';
    return;
  }
  securitySearchState.value = 'resolving';
  securitySearchTimer = setTimeout(() => runSecuritySearch(query, requestToken), 400);
};

watch(subjectQuery, scheduleSecuritySearch, { immediate: true });
watch(comparisonSecurities, securities => {
  if (securities.length > 0 && researchDepth.value === 'quick') {
    researchDepth.value = 'standard';
  }
});
watch(() => props.refreshRevision, () => loadMissionRecords());

onMounted(() => {
  loadMissionRecords();
  restoreActiveResearchRun();
});

onBeforeUnmount(() => {
  securitySearchGate.invalidate();
  subscriptionGeneration += 1;
  activeSubscriptionController?.abort();
  if (securitySearchTimer) clearTimeout(securitySearchTimer);
});

const loadMissionRecords = async () => {
  isMissionRecordsLoading.value = true;
  missionRecordsError.value = '';
  try {
    const keyword = missionRecordKeyword.value;
    const [createdPage, runningPage, reports] = await Promise.all([
      listTasks({ page: 1, size: 5, status: 'CREATED', keyword }),
      listTasks({ page: 1, size: 5, status: 'RUNNING', keyword }),
      listReports({ keyword, favoriteOnly: false })
    ]);
    const taskMap = new Map(
      [...(runningPage.items || []), ...(createdPage.items || [])].map(task => [task.id, task])
    );
    missionTasks.value = [...taskMap.values()]
      .sort((left, right) => String(right.updatedAt || right.createdAt).localeCompare(String(left.updatedAt || left.createdAt)))
      .slice(0, 6);
    missionReports.value = (reports || []).slice(0, 5);
  } catch (error) {
    missionRecordsError.value = error.message || '任务与报告加载失败';
  } finally {
    isMissionRecordsLoading.value = false;
  }
};

const searchMissionRecords = keyword => {
  missionRecordKeyword.value = String(keyword || '').trim();
  loadMissionRecords();
};

const startStockResearch = async () => {
  if (!researchReadiness.value.canSubmit || isLoading.value) return;

  const security = selectedSecurity.value;

  isLoading.value = true;
  connectionInterrupted.value = false;
  connectionError.value = '';
  activeRunSnapshot.value = {
    ticker: security.fullCode,
    companyName: security.companyName || '',
    assetType: security.assetType,
    researchIntent: researchIntent.value,
    researchQuestion: researchQuestion.value.trim(),
    asOfDate: researchAsOfDate.value,
    researchDepth: researchDepth.value,
    timeHorizon: researchTimeHorizon.value,
    searchMode: searchMode.value,
    comparisonTickers: comparisonSecurities.value.map(item => item.fullCode)
  };
  stockReplay.value = null;
  stockTrace.value = null;
  resetAgentRun('[初始化] Research Agent：' + security.fullCode
    + '，问题：' + researchQuestion.value.trim());

  const actualMode = searchMode.value;
  const researchRequest = {
    ticker: security.fullCode,
    research_intent: researchIntent.value,
    research_question: researchQuestion.value.trim(),
    as_of_date: researchAsOfDate.value,
    time_horizon: researchTimeHorizon.value,
    research_depth: researchDepth.value,
    search_mode: actualMode,
    comparison_tickers: comparisonSecurities.value.map(item => item.fullCode),
    thread_id: props.threadId
  };
  const pendingSubmission = readPendingSubmission();
  const submissionIntent = resolveSubmissionIntent(researchRequest, pendingSubmission);
  const retryingSameSubmission = pendingSubmission?.clientRequestId === submissionIntent.clientRequestId;

  try {
    if (uploadedFiles.value.length > 0) {
      logs.value.push('[系统] 已绑定 ' + uploadedFiles.value.length + ' 个解析完成的研究文档。');
    } else {
      logs.value.push(retryingSameSubmission
        ? '[恢复] 正在重试同一任务创建请求，保留个人知识库上下文。'
        : '[系统] 未选择新文档，保留个人知识库并结合公开数据源研究。');
    }

    savePendingSubmission(submissionIntent);
    const receipt = await createResearchRun(
      researchRequest,
      submissionIntent.clientRequestId,
      props.threadId
    );
    clearPendingSubmission();
    bindAgentRun(receipt.taskId, activeRunSnapshot.value);
    persistCurrentThreadId(receipt.threadId);
    saveActiveResearchRun({
      taskId: receipt.taskId,
      threadId: receipt.threadId,
      lastSequence: 0,
      requestSnapshot: activeRunSnapshot.value
    });
    pushAgentLog(receipt.reused
      ? `[恢复] 已复用幂等任务 #${receipt.taskId}，正在加载已提交事件。`
      : `[提交] 任务 #${receipt.taskId} 已持久化，正在订阅 Agent 事件。`);
    if (isTerminalResearchStatus(receipt.status)) {
      isLoading.value = false;
      clearActiveResearchRun(receipt.taskId);
      emit('warning', `任务 #${receipt.taskId} 未进入执行队列，请在任务中心查看失败原因。`);
      loadMissionRecords();
      return;
    }
    subscribeToResearchRun(receipt.taskId, 0);
  } catch (error) {
    isLoading.value = false;
    if (error.status >= 400 && error.status < 500) clearPendingSubmission();
    logs.value.push('[错误] 初始化失败：' + error.message);
    emit('warning', '任务创建失败：' + error.message + '。表单内容已保留，可直接重试。');
  }
};

const handleStockEvent = (event) => {
  handleAgentEvent(event);
  const taskId = event?.data?.taskId || latestStockTaskId.value;
  const sequence = Number(event?.data?.sequence || 0);
  if (taskId && sequence) advanceActiveResearchSequence(taskId, sequence);
  if ((event?.step || event?.type) === 'run_created') loadMissionRecords();
};

const subscribeToResearchRun = async (taskId, afterSequence = 0) => {
  const generation = ++subscriptionGeneration;
  activeSubscriptionController?.abort();
  activeSubscriptionController = new AbortController();
  connectionInterrupted.value = false;
  connectionError.value = '';
  await subscribeResearchRun(
    taskId,
    event => {
      if (generation === subscriptionGeneration) handleStockEvent(event);
    },
    () => {
      if (generation !== subscriptionGeneration) return;
      isLoading.value = false;
      connectionInterrupted.value = false;
      clearActiveResearchRun(taskId);
      markAgentRunDone();
      pushAgentLog('[完成] Research Agent 已结束运行。');
      emit('completed');
      loadMissionRecords();
    },
    error => {
      if (generation !== subscriptionGeneration) return;
      isLoading.value = true;
      connectionInterrupted.value = true;
      connectionError.value = error.message;
      logs.value.push('[连接] 事件订阅暂时中断：' + error.message);
      emit('warning', '任务已经保存，但事件连接暂时中断。可直接点击“重新连接”继续恢复。');
    },
    afterSequence,
    activeSubscriptionController.signal
  );
};

const restoreActiveResearchRun = async () => {
  const activeRun = readActiveResearchRun();
  if (!activeRun) return;
  try {
    const [task, trace] = await Promise.all([
      getTask(activeRun.taskId),
      getResearchRunTrace(activeRun.taskId)
    ]);
    activeRunSnapshot.value = activeRun.requestSnapshot || null;
    if (trace.events?.length) {
      applyAgentTrace(trace.events, '[恢复] 已从持久化事件恢复刷新前的 Agent 运行。');
    } else {
      bindAgentRun(activeRun.taskId, activeRun.requestSnapshot || null);
    }
    const afterSequence = Math.max(activeRun.lastSequence, latestResearchSequence(trace.events));
    saveActiveResearchRun({ ...activeRun, lastSequence: afterSequence });
    if (isTerminalResearchStatus(task.status)) {
      isLoading.value = false;
      clearActiveResearchRun(activeRun.taskId);
      markAgentRunDone();
      return;
    }
    isLoading.value = true;
    pushAgentLog(`[恢复] 从事件序号 #${afterSequence} 继续订阅任务 #${activeRun.taskId}。`);
    subscribeToResearchRun(activeRun.taskId, afterSequence);
  } catch (error) {
    bindAgentRun(activeRun.taskId, activeRun.requestSnapshot || null);
    isLoading.value = true;
    connectionInterrupted.value = true;
    connectionError.value = error.message;
    emit('warning', '暂时无法恢复上一次研究任务，任务指针已保留，可稍后重新连接。');
  }
};

const reconnectActiveResearchRun = async () => {
  const activeRun = readActiveResearchRun();
  if (!activeRun) {
    connectionInterrupted.value = false;
    emit('warning', '没有可恢复的活动任务，请到任务中心查看最新状态。');
    return;
  }
  pushAgentLog(`[连接] 正在从事件序号 #${activeRun.lastSequence} 重新连接任务 #${activeRun.taskId}。`);
  await restoreActiveResearchRun();
};

const cancelActiveResearchRun = async () => {
  if (!latestStockTaskId.value || !window.confirm('确认取消当前研究任务？')) return;
  const taskId = latestStockTaskId.value;
  try {
    await cancelResearchRun(taskId);
    subscriptionGeneration += 1;
    activeSubscriptionController?.abort();
    handleAgentEvent({
      step: 'run_stopped',
      data: {
        eventId: `user-cancelled-${taskId}`,
        taskId,
        status: 'CANCELLED',
        reason: 'USER_CANCELLED'
      }
    });
    clearActiveResearchRun(taskId);
    isLoading.value = false;
    connectionInterrupted.value = false;
    connectionError.value = '';
    markAgentRunDone();
    pushAgentLog('[取消] 当前研究任务已取消，旧执行租约已失效。');
    emit('completed');
    loadMissionRecords();
  } catch (error) {
    emit('warning', '取消任务失败：' + error.message);
  }
};

const submitStockFeedback = async (feedbackType, feedbackDetail = '') => {
  if (!latestStockTaskId.value) return;
  try {
    await saveStockFeedback(latestStockTaskId.value, feedbackType, feedbackDetail);
    logs.value.push('[反馈] 已记录 Bad Case：' + feedbackType);
  } catch (error) {
    emit('warning', error.message);
  }
};

const loadStockReplay = async () => {
  if (!latestStockTaskId.value) return;
  try {
    const [replay, trace] = await Promise.all([
      getStockReplay(latestStockTaskId.value),
      getResearchRunTrace(latestStockTaskId.value)
    ]);
    stockReplay.value = replay;
    stockTrace.value = trace;
    if (trace.events?.length) {
      applyAgentTrace(
        trace.events,
        '[回放] 已使用与实时 SSE 相同的状态投影加载历史轨迹。'
      );
    }
    logs.value.push('[回放] 已加载本次快照、证据、指标与可信度轨迹。');
  } catch (error) {
    emit('warning', error.message);
  }
};
</script>
