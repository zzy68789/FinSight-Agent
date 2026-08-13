<template>
  <section class="overflow-hidden rounded-lg border border-slate-700 bg-white shadow-sm shadow-slate-950/10" aria-labelledby="mission-control-title">
    <header class="bg-slate-950 px-5 py-5 text-white sm:px-6">
      <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p class="text-[10px] font-bold uppercase tracking-[0.18em] text-amber-300">Mission Control</p>
          <h2 id="mission-control-title" class="mt-1 text-lg font-semibold">{{ mission.statusLabel }}</h2>
          <p class="mt-1 text-xs text-slate-400">
            {{ mission.taskId ? `任务 #${mission.taskId}` : '启动研究后，这里将显示权威 Agent 事件' }}
            <span v-if="mission.currentStep !== 'idle'"> · {{ mission.currentStepLabel }}</span>
          </p>
        </div>
        <span class="inline-flex w-fit items-center gap-2 rounded-md border px-2.5 py-1.5 text-xs font-semibold" :class="statusBadgeClass">
          <span class="h-2 w-2 rounded-full" :class="statusDotClass"></span>
          {{ mission.statusLabel }}
        </span>
      </div>

      <ol class="mt-5 grid grid-cols-3 gap-x-2 gap-y-3 sm:grid-cols-6" aria-label="Agent 权威阶段">
        <li v-for="stage in mission.stages" :key="stage.id" class="min-w-0">
          <div class="mb-2 h-1 rounded-full" :class="stage.active ? 'bg-amber-300' : stage.completed ? 'bg-emerald-500' : 'bg-slate-700'"></div>
          <div class="flex items-center gap-1.5">
            <Loader2Icon v-if="stage.active && isRunning" class="h-3.5 w-3.5 shrink-0 animate-spin text-amber-300" aria-hidden="true" />
            <CheckIcon v-else-if="stage.completed" class="h-3.5 w-3.5 shrink-0 text-emerald-400" aria-hidden="true" />
            <span v-else class="h-1.5 w-1.5 shrink-0 rounded-full bg-slate-600"></span>
            <span class="truncate text-[10px] font-semibold" :class="stage.active ? 'text-amber-100' : stage.completed ? 'text-slate-200' : 'text-slate-500'">
              {{ stage.label }}
            </span>
          </div>
        </li>
      </ol>
    </header>

    <nav class="overflow-x-auto border-b border-slate-200 bg-slate-50 px-2" aria-label="Mission Control 视图">
      <div class="flex min-w-max">
        <button
          v-for="tab in tabs"
          :key="tab.id"
          type="button"
          class="relative min-h-11 px-3 text-xs font-semibold transition focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:px-4"
          :class="activeTab === tab.id ? 'text-blue-800' : 'text-slate-500 hover:text-slate-800'"
          :aria-selected="activeTab === tab.id"
          role="tab"
          @click="activeTab = tab.id"
        >
          {{ tab.label }}
          <span v-if="activeTab === tab.id" class="absolute inset-x-3 bottom-0 h-0.5 bg-blue-700"></span>
        </button>
      </div>
    </nav>

    <div class="p-5 sm:p-6">
      <div v-if="activeTab === 'overview'" role="tabpanel">
        <div v-if="mission.status === 'IDLE'" class="rounded-lg border border-dashed border-slate-300 bg-slate-50 px-6 py-10 text-center">
          <RadarIcon class="mx-auto h-7 w-7 text-slate-400" aria-hidden="true" />
          <h3 class="mt-3 text-sm font-semibold text-slate-900">等待研究任务</h3>
          <p class="mx-auto mt-1 max-w-md text-xs leading-5 text-slate-500">在左侧确认证券和研究问题。任务创建后，计划、工具、证据和门禁事件会在这里同步更新。</p>
        </div>
        <template v-else>
          <div class="grid gap-3 md:grid-cols-2">
            <article class="rounded-lg border border-slate-200 bg-slate-50 p-4">
              <p class="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">Research brief</p>
              <div class="mt-2 flex flex-wrap items-center gap-2">
                <span class="font-mono text-sm font-bold text-slate-950">{{ activeRequest.ticker || '-' }}</span>
                <span v-if="activeRequest.companyName" class="text-sm font-semibold text-slate-700">{{ activeRequest.companyName }}</span>
                <span v-if="activeRequest.researchIntent" class="rounded bg-blue-50 px-2 py-1 text-[10px] font-bold text-blue-700">{{ intentLabel(activeRequest.researchIntent) }}</span>
              </div>
              <p class="mt-3 text-sm leading-6 text-slate-700">{{ activeRequest.researchQuestion || '等待任务问题' }}</p>
              <p v-if="activeRequest.asOfDate" class="mt-2 text-[11px] text-slate-500">数据截止日 {{ activeRequest.asOfDate }} · {{ activeRequest.researchDepth || '-' }}</p>
              <p v-if="activeRequest.comparisonTickers?.length" class="mt-1 text-[11px] text-slate-500">
                可比证券 {{ activeRequest.comparisonTickers.join('、') }}
              </p>
            </article>

            <article class="rounded-lg border border-slate-200 bg-white p-4">
              <p class="text-[10px] font-bold uppercase tracking-[0.12em] text-slate-400">Planner objective</p>
              <p class="mt-2 text-sm font-semibold leading-6 text-slate-900">{{ mission.goal || '正在形成研究计划' }}</p>
              <ul v-if="mission.unresolvedQuestions.length" class="mt-3 space-y-1 text-xs leading-5 text-slate-500">
                <li v-for="question in mission.unresolvedQuestions.slice(0, 3)" :key="question" class="flex gap-2">
                  <span class="mt-2 h-1 w-1 shrink-0 rounded-full bg-amber-500"></span>
                  <span>{{ question }}</span>
                </li>
              </ul>
            </article>
          </div>

          <dl class="mt-3 grid grid-cols-2 border-y border-slate-200 sm:grid-cols-4">
            <div class="px-3 py-3 first:pl-0">
              <dt class="text-[10px] font-semibold text-slate-400">最近工具</dt>
              <dd class="mt-1 truncate text-xs font-bold text-slate-800" :title="mission.latestTool?.name">{{ toolLabel(mission.latestTool?.name) }}</dd>
              <p class="mt-1 text-[10px]" :class="statusTextClass(mission.latestTool?.status)">{{ statusLabel(mission.latestTool?.status) }}</p>
            </div>
            <div class="border-l border-slate-200 px-3 py-3">
              <dt class="text-[10px] font-semibold text-slate-400">有效证据</dt>
              <dd class="mt-1 text-lg font-bold text-slate-950">{{ mission.effectiveEvidenceCount }}<span class="ml-1 text-xs font-medium text-slate-400">/ {{ mission.evidenceCount }}</span></dd>
            </div>
            <div class="border-l border-slate-200 px-3 py-3">
              <dt class="text-[10px] font-semibold text-slate-400">重新规划</dt>
              <dd class="mt-1 text-lg font-bold text-slate-950">{{ mission.replanCount }}</dd>
              <p class="mt-1 text-[10px] text-slate-400">补证据 {{ mission.evidenceRecoveryCount }} 次</p>
            </div>
            <div class="border-l border-slate-200 px-3 py-3 last:pr-0">
              <dt class="text-[10px] font-semibold text-slate-400">质量门禁</dt>
              <dd class="mt-1 text-xs font-bold" :class="gateStatusClass">{{ gateStatusLabel }}</dd>
              <p v-if="mission.qualityGateDecision?.route" class="mt-1 truncate text-[10px] text-slate-400">{{ mission.qualityGateDecision.route }}</p>
            </div>
          </dl>

          <div v-if="mission.stopReason" class="mt-4 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
            <p class="text-xs font-bold text-amber-900">任务未发布报告</p>
            <p class="mt-1 text-xs leading-5 text-amber-800">{{ mission.stopReason }}</p>
          </div>

          <div v-if="projection.budget" class="mt-4 flex flex-wrap gap-2 text-[10px] font-semibold text-slate-500">
            <span class="rounded bg-slate-100 px-2 py-1">最大轮次 {{ projection.budget.maxTurns }}</span>
            <span class="rounded bg-slate-100 px-2 py-1">工具预算 {{ projection.budget.maxToolCalls }}</span>
            <span class="rounded bg-slate-100 px-2 py-1">Replan {{ projection.budget.maxReplans }}</span>
            <span class="rounded bg-slate-100 px-2 py-1">补证据 {{ projection.budget.maxEvidenceRecoveries }}</span>
          </div>
        </template>
      </div>

      <div v-else-if="activeTab === 'activity'" role="tabpanel">
        <ol v-if="projection.events?.length" class="divide-y divide-slate-100">
          <li v-for="event in [...projection.events].reverse()" :key="event.id" class="grid gap-2 py-3 sm:grid-cols-[6rem_1fr_auto] sm:items-start">
            <div class="font-mono text-[10px] text-slate-400">Turn {{ event.turnNo || 0 }}<span v-if="event.sequence"> · #{{ event.sequence }}</span></div>
            <div class="min-w-0">
              <p class="truncate text-xs font-bold text-slate-800">{{ event.toolName || eventTypeLabel(event.type) }}</p>
              <p v-if="event.summary" class="mt-1 text-xs leading-5 text-slate-500">{{ event.summary }}</p>
            </div>
            <div class="flex items-center gap-2 text-[10px] font-semibold">
              <span v-if="event.durationMs" class="font-mono text-slate-400">{{ event.durationMs }}ms</span>
              <span :class="statusTextClass(event.status)">{{ statusLabel(event.status) }}</span>
            </div>
          </li>
        </ol>
        <p v-else class="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-5 py-8 text-center text-xs text-slate-500">任务创建后显示持久化 Agent 事件。</p>
      </div>

      <div v-else role="tabpanel">
        <div class="flex flex-col gap-2 sm:flex-row">
          <input v-model="recordKeyword" type="search" class="min-h-10 flex-1 rounded-lg border border-slate-200 px-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="搜索证券、报告编号或研究问题" @keyup.enter="$emit('search-records', recordKeyword)" />
          <button type="button" class="min-h-10 rounded-lg border border-slate-200 px-4 text-xs font-semibold text-slate-700 hover:bg-slate-50" @click="$emit('search-records', recordKeyword)">搜索</button>
          <button type="button" class="min-h-10 rounded-lg border border-slate-200 px-3 text-slate-500 hover:bg-slate-50" aria-label="刷新任务和报告" @click="$emit('refresh-records')"><RefreshCwIcon class="h-4 w-4" :class="recordsLoading ? 'animate-spin' : ''" /></button>
        </div>
        <p v-if="recordsError" class="mt-3 text-xs text-rose-700">{{ recordsError }}</p>
        <div class="mt-4 grid gap-4 md:grid-cols-2">
          <section class="rounded-lg border border-slate-200">
            <div class="border-b border-slate-100 bg-slate-50 px-4 py-3"><h3 class="text-xs font-bold text-slate-900">当前任务</h3></div>
            <div v-if="activeTasks.length" class="divide-y divide-slate-100">
              <article v-for="task in activeTasks" :key="task.id" class="px-4 py-3">
                <div class="flex items-start justify-between gap-3"><p class="line-clamp-2 text-xs font-semibold leading-5 text-slate-800">{{ task.query }}</p><span class="shrink-0 text-[10px] font-bold" :class="statusTextClass(task.status)">{{ taskStatusLabel(task.status) }}</span></div>
                <p class="mt-1 font-mono text-[10px] text-slate-400">#{{ task.id }} · {{ formatDate(task.updatedAt || task.createdAt) }}</p>
              </article>
            </div>
            <p v-else class="px-4 py-8 text-center text-xs text-slate-400">暂无待执行或运行中任务。</p>
          </section>

          <section class="rounded-lg border border-slate-200">
            <div class="border-b border-slate-100 bg-slate-50 px-4 py-3"><h3 class="text-xs font-bold text-slate-900">最近完成报告</h3></div>
            <div v-if="recentReports.length" class="divide-y divide-slate-100">
              <RouterLink v-for="report in recentReports.slice(0, 5)" :key="report.id" :to="`/reports/${report.id}`" class="block px-4 py-3 transition hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600">
                <div class="flex items-center justify-between gap-3"><p class="text-xs font-semibold text-slate-800">报告 #{{ report.id }} · v{{ report.version }}</p><span class="text-[10px] font-bold" :class="statusTextClass(report.reviewStatus)">{{ report.reviewStatus }}</span></div>
                <p class="mt-1 text-[10px] text-slate-400">任务 #{{ report.taskId || '-' }} · {{ formatDate(report.createdAt) }}</p>
              </RouterLink>
            </div>
            <p v-else class="px-4 py-8 text-center text-xs text-slate-400">暂无已完成报告。</p>
          </section>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref } from 'vue';
import { RouterLink } from 'vue-router';
import {
  CheckIcon,
  Loader2Icon,
  RadarIcon,
  RefreshCwIcon
} from 'lucide-vue-next';
import { agentEventTypeLabel } from '../../modules/agentEventProjection.js';
import { deriveMissionControlView } from '../../modules/researchMissionControl.js';
import { formatDate } from '../../modules/presentation.js';

const props = defineProps({
  projection: { type: Object, required: true },
  isRunning: { type: Boolean, default: false },
  runSnapshot: { type: Object, default: null },
  activeTasks: { type: Array, default: () => [] },
  recentReports: { type: Array, default: () => [] },
  recordsLoading: { type: Boolean, default: false },
  recordsError: { type: String, default: '' }
});

defineEmits(['search-records', 'refresh-records']);
const activeTab = ref('overview');
const recordKeyword = ref('');
const tabs = [
  { id: 'overview', label: '运行概览' },
  { id: 'activity', label: '活动轨迹' },
  { id: 'records', label: '任务与报告' }
];

const mission = computed(() => deriveMissionControlView(props.projection, props.isRunning));
const activeRequest = computed(() => props.runSnapshot || mission.value.requestSummary || {});
const gateStatusLabel = computed(() => {
  if (mission.value.status === 'COMPLETED') return 'PASS';
  if (mission.value.status === 'STOPPED') return 'STOPPED';
  return mission.value.qualityGateDecision?.status || '等待门禁';
});
const gateStatusClass = computed(() => gateStatusLabel.value === 'PASS' ? 'text-emerald-700' : gateStatusLabel.value === 'STOPPED' ? 'text-amber-700' : 'text-slate-500');
const statusBadgeClass = computed(() => ({
  IDLE: 'border-slate-700 bg-slate-900 text-slate-300',
  RUNNING: 'border-amber-300/40 bg-amber-300/10 text-amber-100',
  COMPLETED: 'border-emerald-400/40 bg-emerald-400/10 text-emerald-200',
  STOPPED: 'border-amber-400/40 bg-amber-400/10 text-amber-200'
})[mission.value.status]);
const statusDotClass = computed(() => ({
  IDLE: 'bg-slate-500', RUNNING: 'bg-amber-300', COMPLETED: 'bg-emerald-400', STOPPED: 'bg-amber-400'
})[mission.value.status]);

const eventTypeLabel = type => agentEventTypeLabel(type);
const intentLabel = value => ({ COMPREHENSIVE: '综合研究', FINANCIAL_QUALITY: '财务质量', VALUATION_RISK: '估值风险', ETF_TRACKING: 'ETF 跟踪', EVENT_IMPACT: '事件影响' })[value] || value;
const toolLabel = name => name || '等待工具调用';
const taskStatusLabel = status => ({ CREATED: '待执行', RUNNING: '运行中' })[status] || status;
const statusLabel = status => ({ RUNNING: '运行中', SUCCESS: '成功', PASS: '通过', DEGRADED: '需处理', FAILED: '失败', CREATED: '待执行' })[status] || status || '等待';
const statusTextClass = status => ['SUCCESS', 'PASS', 'COMPLETED'].includes(status)
  ? 'text-emerald-700'
  : ['FAILED', 'ERROR'].includes(status) ? 'text-rose-700'
    : ['DEGRADED', 'STOPPED'].includes(status) ? 'text-amber-700' : 'text-blue-700';

</script>
