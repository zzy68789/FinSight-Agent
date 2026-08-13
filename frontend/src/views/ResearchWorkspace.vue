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
          @feedback="submitStockFeedback"
          @replay="loadStockReplay"
          @search-records="searchMissionRecords"
          @refresh-records="loadMissionRecords"
        />

        <div class="flex min-h-[calc(100vh-9rem)] flex-col overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/70 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-blue-950">研究报告</h2>
              <p class="mt-1 text-sm text-slate-500">Research Agent 通过质量门禁后发布的在线报告</p>
            </div>
            <div class="flex items-center gap-2 text-xs font-medium text-slate-500">
              <span class="rounded-md bg-white px-2 py-1 text-blue-700 ring-1 ring-blue-100">证券代码分析</span>
              <span class="rounded-md bg-white px-2 py-1 text-blue-700 ring-1 ring-blue-100">{{ searchModeLabel(searchMode) }}</span>
              <span class="rounded-md bg-white px-2 py-1 text-blue-700 ring-1 ring-blue-100">{{ currentStepLabel(currentStep) }}</span>
            </div>
          </div>

          <div class="flex-1 p-5 sm:p-6 lg:p-8">
            <div v-if="!displayedReport && !isLoading" class="flex min-h-[28rem] flex-col items-center justify-center rounded-lg border border-dashed border-blue-200 bg-blue-50/50 px-6 text-center">
              <div class="flex h-14 w-14 items-center justify-center rounded-lg bg-white text-blue-500 shadow-sm ring-1 ring-blue-100">
                <FileOutputIcon class="h-7 w-7" aria-hidden="true" />
              </div>
              <h3 class="mt-5 text-base font-semibold text-blue-950">暂无研究报告</h3>
              <p class="mt-2 max-w-sm text-sm leading-6 text-slate-500">
                完成一次研究后，报告会显示在这里。
              </p>
            </div>

            <div v-else-if="isLoading && !displayedReport" class="flex min-h-[28rem] flex-col justify-center rounded-lg border border-blue-100 bg-white px-6">
              <div class="mx-auto w-full max-w-xl">
                <div class="mb-6 flex items-center gap-3">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                    <Loader2Icon class="h-5 w-5 animate-spin" aria-hidden="true" />
                  </div>
                  <div>
                    <h3 class="text-sm font-semibold text-blue-950">研究流程运行中</h3>
                    <p class="mt-1 text-xs text-slate-500">当前步骤：{{ currentStepLabel(currentStep) }}</p>
                  </div>
                </div>

                <div class="space-y-3">
                  <div class="h-3 w-5/6 animate-pulse rounded bg-slate-200"></div>
                  <div class="h-3 w-full animate-pulse rounded bg-slate-200"></div>
                  <div class="h-3 w-4/5 animate-pulse rounded bg-slate-200"></div>
                  <div class="mt-5 h-24 animate-pulse rounded-lg bg-slate-100"></div>
                </div>
              </div>
            </div>

            <article v-else class="research-paper report-content prose prose-slate max-w-none">
              <div v-html="renderedReport"></div>
              <span v-if="isTyping" class="ml-1 inline-block h-5 w-2 animate-pulse bg-blue-700 align-middle"></span>
            </article>

            <section v-if="stockTrace" class="mt-6 border-t border-slate-200 pt-5">
              <div class="mb-3 flex items-center justify-between gap-3">
                <div>
                  <h3 class="text-sm font-semibold text-slate-950">报告可信度轨迹</h3>
                  <p class="mt-1 text-xs text-slate-500">任务 #{{ stockTrace.taskId }} · {{ stockTrace.stage }}</p>
                </div>
                <button type="button" class="rounded-md p-1 text-slate-500 transition hover:bg-slate-100 hover:text-slate-800" aria-label="关闭回放" @click="stockReplay = null; stockTrace = null">
                  <XIcon class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
              <div class="grid grid-cols-2 border-y border-slate-200 text-sm sm:grid-cols-4">
                <div class="px-3 py-3">
                  <p class="text-xs text-slate-500">任务状态</p>
                  <p class="mt-1 font-semibold text-slate-900">{{ stockTrace.status }}</p>
                </div>
                <div class="border-l border-slate-200 px-3 py-3">
                  <p class="text-xs text-slate-500">报告版本</p>
                  <p class="mt-1 font-semibold text-slate-900">v{{ stockTrace.reportVersion || '-' }}</p>
                </div>
                <div class="border-l-0 border-slate-200 px-3 py-3 sm:border-l">
                  <p class="text-xs text-slate-500">证据有效率</p>
                  <p class="mt-1 font-semibold text-slate-900">{{ stockTrace.evidenceEffective }}/{{ stockTrace.evidenceTotal }}</p>
                </div>
                <div class="border-l border-slate-200 px-3 py-3">
                  <p class="text-xs text-slate-500">生成方式</p>
                  <p class="mt-1 font-semibold" :class="stockTrace.cacheHit ? 'text-emerald-700' : 'text-blue-700'">{{ stockTrace.cacheHit ? '缓存复用' : '实时生成' }}</p>
                </div>
              </div>
              <div class="mt-4 grid gap-2 text-xs sm:grid-cols-2">
                <div class="min-w-0">
                  <span class="text-slate-500">数据快照</span>
                  <p class="mt-1 truncate font-mono text-slate-800" :title="stockTrace.dataSnapshotHash">{{ stockTrace.dataSnapshotHash || '-' }}</p>
                </div>
                <div class="min-w-0">
                  <span class="text-slate-500">生成上下文</span>
                  <p class="mt-1 truncate font-mono text-slate-800" :title="stockTrace.generationContextHash">{{ stockTrace.generationContextHash || '-' }}</p>
                </div>
              </div>
              <div v-if="stockTrace.retrievalResults?.length" class="mt-5">
                <h4 class="text-xs font-semibold text-slate-900">混合检索</h4>
                <div v-for="(retrieval, index) in stockTrace.retrievalResults" :key="`${retrieval.query}-${index}`" class="mt-2 border-t border-slate-200 pt-2 text-xs">
                  <div class="flex flex-wrap items-center gap-x-4 gap-y-1 text-slate-600">
                    <span>候选 {{ retrieval.candidateCount }}</span>
                    <span>接受 {{ retrieval.acceptedCount }}</span>
                    <span>过滤 {{ retrieval.filteredCount }}</span>
                    <span>{{ retrieval.durationMs }} ms</span>
                  </div>
                  <div class="mt-2 space-y-1">
                    <div v-for="entry in retrieval.traceEntries" :key="`${entry.rank}-${entry.source}`" class="flex items-center justify-between gap-3 py-1">
                      <span class="min-w-0 truncate text-slate-700">#{{ entry.rank }} {{ entry.source }}</span>
                      <span class="shrink-0 font-mono text-slate-500">{{ entry.channels.join('+') }} · {{ entry.fusionScore }}</span>
                    </div>
                  </div>
                </div>
              </div>
              <div class="mt-5 overflow-x-auto">
                <table class="w-full min-w-[520px] text-left text-xs">
                  <thead class="border-y border-slate-200 text-slate-500">
                    <tr><th class="py-2">阶段</th><th>尝试</th><th>状态</th><th>耗时</th></tr>
                  </thead>
                  <tbody class="divide-y divide-slate-100">
                    <tr v-for="stage in stockTrace.stages" :key="`${stage.stage}-${stage.attemptNo}-${stage.createdAt}`">
                      <td class="py-2 font-medium text-slate-800">{{ stage.stage }}</td>
                      <td>{{ stage.attemptNo }}</td>
                      <td>{{ stage.status }}</td>
                      <td>{{ stage.durationMs }} ms</td>
                    </tr>
                  </tbody>
                </table>
              </div>
            </section>
          </div>
        </div>
      </section>
    </main>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import {
  FileOutputIcon,
  Loader2Icon,
  XIcon
} from 'lucide-vue-next';
import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';
import ResearchLauncherSidebar from '../components/research/ResearchLauncherSidebar.vue';
import ResearchMissionControl from '../components/research/ResearchMissionControl.vue';
import { useAgentRunProjection } from '../composables/useAgentRunProjection';
import { agentEventTypeLabel } from '../modules/agentEventProjection';
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

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
});
md.use(mk);

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

const renderedReport = computed(() => {
  let raw = displayedReport.value || '';
  raw = raw.replace(/\\\[/g, () => '$$').replace(/\\\]/g, () => '$$');
  raw = raw.replace(/\\\(/g, '$').replace(/\\\)/g, '$');
  raw = raw.replace(/\[\s*(\\text|\\frac|\\sum|\\int)/g, '$$$$ $1');
  return md.render(raw);
});

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

const currentStepLabel = (step) => agentEventTypeLabel(step);
const searchModeLabel = mode => mode === 'document' ? '仅文档' : '混合检索';

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
