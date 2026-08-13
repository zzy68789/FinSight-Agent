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
          @drag-state-change="isDragging = $event"
          @submit="startStockResearch"
        />

      </aside>

      <section class="space-y-6 lg:col-span-8">
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
          @select-example="researchQuestion = $event"
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
  clearContext,
  getResearchRunTrace,
  getStockReplay,
  listReports,
  listTasks,
  saveStockFeedback,
  searchSecurities,
  streamResearchRun,
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

const emit = defineEmits(['warning', 'completed']);

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
let securitySearchTimer = null;
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
    logs.value.push('[系统] 知识库已构建，已索引 ' + result.chunks_stored + ' 个文本块。');
  } catch (error) {
    isDocumentReady.value = true;
    logs.value.push('[错误] 上传失败：' + error.message);
    window.alert('上传失败：' + error.message);
    uploadedFiles.value = [];
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
watch(() => props.refreshRevision, () => loadMissionRecords());

onMounted(() => loadMissionRecords());

onBeforeUnmount(() => {
  securitySearchGate.invalidate();
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
  activeRunSnapshot.value = {
    ticker: security.fullCode,
    companyName: security.companyName || '',
    assetType: security.assetType,
    researchIntent: researchIntent.value,
    researchQuestion: researchQuestion.value.trim(),
    asOfDate: researchAsOfDate.value,
    researchDepth: researchDepth.value,
    timeHorizon: researchTimeHorizon.value,
    searchMode: searchMode.value
  };
  stockReplay.value = null;
  stockTrace.value = null;
  resetAgentRun('[初始化] Research Agent：' + security.fullCode
    + '，问题：' + researchQuestion.value.trim());

  const actualMode = searchMode.value;

  try {
    if (uploadedFiles.value.length > 0) {
      logs.value.push('[系统] 已绑定 ' + uploadedFiles.value.length + ' 个解析完成的研究文档。');
    } else {
      logs.value.push('[系统] 正在清理上一轮知识库上下文...');
      await clearContext();
      logs.value.push('[系统] 上下文已清理，将使用公开数据源与降级缺失标记。');
    }

    streamResearchRun(
      {
        ticker: security.fullCode,
        research_intent: researchIntent.value,
        research_question: researchQuestion.value.trim(),
        as_of_date: researchAsOfDate.value,
        time_horizon: researchTimeHorizon.value,
        research_depth: researchDepth.value,
        search_mode: actualMode
      },
      handleStockEvent,
      () => {
        isLoading.value = false;
        markAgentRunDone();
        pushAgentLog('[完成] Research Agent 已结束运行。');
        emit('completed');
      },
      (error) => {
        isLoading.value = false;
        logs.value.push('[错误] ' + error.message);
      },
      props.threadId
    );
  } catch (error) {
    isLoading.value = false;
    logs.value.push('[错误] 初始化失败：' + error.message);
    window.alert('系统错误：' + error.message);
  }
};

const handleStockEvent = (event) => {
  handleAgentEvent(event);
  if ((event?.step || event?.type) === 'run_created') loadMissionRecords();
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
