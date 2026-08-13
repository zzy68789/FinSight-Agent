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

        <StatusFlow :currentStep="currentStep" :completedSteps="completedSteps" :events="agentEvents" />

        <section class="rounded-lg border border-blue-100 bg-white p-5 shadow-sm shadow-blue-100/50">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-blue-950">证券报告质检</h2>
              <p class="mt-1 text-xs text-slate-500">指标、证据和 Bad Case 回放</p>
            </div>
            <ShieldCheckIcon class="h-4 w-4 text-slate-400" aria-hidden="true" />
          </div>

          <div class="grid grid-cols-2 gap-2 text-xs text-slate-600">
            <div class="flex min-h-12 flex-col justify-center rounded-md bg-slate-50 px-3 py-2">
              <span>风险评分</span>
              <span class="mt-1 font-semibold text-slate-900">{{ financialRiskAssessment?.finalScore ?? '-' }}/10 · {{ financialRiskAssessment?.riskLevel || '-' }}</span>
            </div>
            <div class="flex min-h-12 flex-col justify-center rounded-md bg-slate-50 px-3 py-2">
              <span>合规审查</span>
              <span class="mt-1 font-semibold" :class="financialCompliance?.status === 'PASS' ? 'text-emerald-700' : 'text-amber-700'">
                {{ financialCompliance?.status || '-' }} · {{ financialCompliance?.score ?? '-' }}
              </span>
            </div>
            <div class="flex min-h-12 flex-col justify-center rounded-md bg-slate-50 px-3 py-2">
              <span>评测门控</span>
              <span class="mt-1 font-semibold" :class="financialEvaluation?.status === 'PASS' ? 'text-emerald-700' : 'text-amber-700'">
                {{ financialEvaluation?.status || '-' }} · {{ financialEvaluation?.overallScore ?? '-' }}
              </span>
            </div>
            <div class="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
              <span>证据条目</span>
              <span class="font-semibold text-slate-900">{{ financialSnapshotSummary?.evidenceCount ?? financialEvidence.length }}</span>
            </div>
            <div class="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
              <span>缺失项</span>
              <span class="font-semibold text-slate-900">{{ financialSnapshotSummary?.missingCount ?? 0 }}</span>
            </div>
          </div>

          <div v-if="financialRiskAssessment" class="mt-4">
            <div class="h-2 overflow-hidden rounded-full bg-slate-100">
              <div class="h-full rounded-full transition-all" :class="riskScorePercent > 60 ? 'bg-rose-500' : 'bg-blue-700'" :style="{ width: `${riskScorePercent}%` }"></div>
            </div>
            <div class="mt-3 space-y-2">
              <div v-for="dimension in financialRiskAssessment.dimensions" :key="dimension.name" class="rounded-md border border-slate-100 px-3 py-2 text-xs">
                <div class="flex items-center justify-between gap-3">
                  <span class="font-semibold text-slate-800">{{ dimension.name }}</span>
                  <span class="font-mono text-slate-600">{{ dimension.score }}/10 · {{ dimension.weight }}%</span>
                </div>
                <p class="mt-1 leading-5 text-slate-500">{{ dimension.reason }}</p>
              </div>
            </div>
          </div>

          <div v-if="financialMetrics.length > 0" class="mt-4 space-y-2">
            <div v-for="metric in financialMetrics" :key="metric.metricName" class="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2 text-xs">
              <span class="min-w-0 truncate font-medium text-slate-700">{{ metric.metricName }}</span>
              <span class="shrink-0 font-semibold" :class="metric.status === 'OK' ? 'text-emerald-700' : 'text-amber-700'">{{ metric.displayValue }}</span>
            </div>
          </div>

          <div v-if="financialProviderStages.length > 0" class="mt-4 space-y-2">
            <p class="text-xs font-semibold text-slate-500">数据源执行</p>
            <div v-for="stage in financialProviderStages" :key="stage.stageName" class="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2 text-xs">
              <span class="min-w-0 truncate font-medium text-slate-700">{{ stage.stageName }}</span>
              <span class="shrink-0 font-mono" :class="stage.status === 'SUCCESS' ? 'text-emerald-700' : 'text-rose-700'">{{ stage.status }} · {{ stage.durationMs }}ms</span>
            </div>
          </div>

          <div v-if="evidenceBreakdown.length > 0" class="mt-4 flex flex-wrap gap-2">
            <span v-for="item in evidenceBreakdown" :key="item.sourceType" class="rounded-md bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-800 ring-1 ring-blue-100">
              {{ item.sourceType }} · {{ item.count }}
            </span>
          </div>

          <div v-if="financialCompliance?.issues?.length" class="mt-4 space-y-2">
            <p class="text-xs font-semibold text-amber-700">合规问题</p>
            <div v-for="issue in financialCompliance.issues" :key="`${issue.category}-${issue.description}`" class="rounded-md border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              <div class="font-semibold">{{ issue.category }} · {{ issue.severity }}</div>
              <p class="mt-1 leading-5">{{ issue.description }}</p>
            </div>
          </div>

          <div v-if="financialEvaluation?.metricScores?.length" class="mt-4 space-y-2">
            <p class="text-xs font-semibold text-slate-500">评测指标</p>
            <div v-for="metric in financialEvaluation.metricScores" :key="metric.metricName" class="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2 text-xs">
              <span class="min-w-0 truncate font-medium text-slate-700">{{ metric.metricName }}</span>
              <span class="shrink-0 font-mono" :class="metric.status === 'PASS' ? 'text-emerald-700' : 'text-rose-700'">{{ metric.status }} · {{ metric.score }}</span>
            </div>
            <div v-if="financialEvaluation.failedReasons?.length" class="rounded-md border border-amber-100 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">
              {{ financialEvaluation.failedReasons.join('；') }}
            </div>
          </div>

          <div class="mt-4 grid grid-cols-2 gap-2">
            <button
              v-for="type in ['数字错', '引用错', '逻辑错', '信息过期']"
              :key="type"
              type="button"
              class="min-h-9 rounded-lg border border-slate-200 px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-45"
              :disabled="!latestStockTaskId"
              @click="submitStockFeedback(type)"
            >
              {{ type }}
            </button>
          </div>
          <input
            v-model="stockFeedbackDetail"
            type="text"
            class="mt-2 min-h-9 w-full rounded-lg border border-slate-200 px-3 text-xs outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
            placeholder="可选：补充反馈说明"
          />
          <button
            type="button"
            class="mt-3 flex min-h-9 w-full items-center justify-center gap-2 rounded-lg bg-slate-950 px-3 text-xs font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            :disabled="!latestStockTaskId"
            @click="loadStockReplay"
          >
            <EyeIcon class="h-3.5 w-3.5" aria-hidden="true" />
            回放本次快照
          </button>
        </section>

        <section class="overflow-hidden rounded-lg border border-slate-800 bg-slate-950 shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 py-3">
            <div class="flex items-center gap-2">
              <TerminalIcon class="h-4 w-4 text-blue-300" aria-hidden="true" />
              <h2 class="text-xs font-semibold tracking-wide text-slate-200">运行日志</h2>
            </div>
            <span class="text-xs text-slate-500">SSE</span>
          </div>
          <div
            ref="logsContainer"
            class="h-36 overflow-y-auto p-4 font-mono text-[11px] leading-5"
            aria-live="polite"
          >
            <div v-if="logs.length === 0" class="text-slate-500">系统已就绪，等待输入。</div>
            <div v-for="(log, i) in logs" :key="i" class="flex gap-2 py-0.5">
              <span class="shrink-0 text-blue-300">&gt;</span>
              <span class="break-all text-slate-300">{{ log }}</span>
            </div>
            <div v-if="isLoading" class="mt-2 animate-pulse text-blue-300">_</div>
          </div>
        </section>
      </aside>

      <section class="lg:col-span-8">
        <div class="flex min-h-[calc(100vh-9rem)] flex-col overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/70 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-blue-950">研究报告</h2>
              <p class="mt-1 text-sm text-slate-500">智能体工作流生成的报告内容</p>
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
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue';
import {
  EyeIcon,
  FileOutputIcon,
  Loader2Icon,
  ShieldCheckIcon,
  TerminalIcon,
  XIcon
} from 'lucide-vue-next';
import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';
import ResearchLauncherSidebar from '../components/research/ResearchLauncherSidebar.vue';
import StatusFlow from '../components/StatusFlow.vue';
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
  saveStockFeedback,
  searchSecurities,
  streamResearchRun,
  uploadFiles
} from '../services/api';

const props = defineProps({
  threadId: {
    type: String,
    required: true
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
  agentPlan,
  agentBudget,
  agentEvents,
  latestStockTaskId,
  financialMetrics,
  financialEvidence,
  financialSnapshotSummary,
  financialRiskAssessment,
  financialCompliance,
  financialEvaluation,
  bullBearResearch,
  financialProviderStages,
  currentStep,
  completedSteps,
  logs,
  displayedReport,
  isTyping,
  resetAgentRun,
  handleAgentEvent,
  applyAgentTrace,
  pushAgentLog,
  markAgentRunDone
} = useAgentRunProjection();

const stockFeedbackDetail = ref('');
const stockReplay = ref(null);
const stockTrace = ref(null);
const isLoading = ref(false);
const logsContainer = ref(null);
const uploadedFiles = ref([]);
const isDragging = ref(false);
const isDocumentReady = ref(true);
const searchMode = ref('hybrid');
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

const riskScorePercent = computed(() => {
  const score = Number(financialRiskAssessment.value?.finalScore || 0);
  return Math.max(0, Math.min(100, score * 10));
});

const evidenceBreakdown = computed(() => {
  const counts = financialEvidence.value.reduce((result, item) => {
    const key = item.sourceType || 'UNKNOWN';
    result[key] = (result[key] || 0) + 1;
    return result;
  }, {});
  return Object.entries(counts).map(([sourceType, count]) => ({ sourceType, count }));
});

const renderedReport = computed(() => {
  let raw = displayedReport.value || '';
  raw = raw.replace(/\\\[/g, () => '$$').replace(/\\\]/g, () => '$$');
  raw = raw.replace(/\\\(/g, '$').replace(/\\\)/g, '$');
  raw = raw.replace(/\[\s*(\\text|\\frac|\\sum|\\int)/g, '$$$$ $1');
  return md.render(raw);
});

const scrollToBottom = async () => {
  await nextTick();
  if (logsContainer.value) logsContainer.value.scrollTop = logsContainer.value.scrollHeight;
};

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

onBeforeUnmount(() => {
  securitySearchGate.invalidate();
  if (securitySearchTimer) clearTimeout(securitySearchTimer);
});

const currentStepLabel = (step) => agentEventTypeLabel(step);
const searchModeLabel = mode => mode === 'document' ? '仅文档' : '混合检索';

const startStockResearch = async () => {
  if (!researchReadiness.value.canSubmit || isLoading.value) return;

  const security = selectedSecurity.value;

  isLoading.value = true;
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
        scrollToBottom();
      },
      (error) => {
        isLoading.value = false;
        logs.value.push('[错误] ' + error.message);
        scrollToBottom();
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
  scrollToBottom();
};

const submitStockFeedback = async (feedbackType) => {
  if (!latestStockTaskId.value) return;
  try {
    await saveStockFeedback(latestStockTaskId.value, feedbackType, stockFeedbackDetail.value);
    logs.value.push('[反馈] 已记录 Bad Case：' + feedbackType);
    stockFeedbackDetail.value = '';
    scrollToBottom();
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
    scrollToBottom();
  } catch (error) {
    emit('warning', error.message);
  }
};
</script>
