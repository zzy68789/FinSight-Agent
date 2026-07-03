<template>
  <section class="overflow-hidden rounded-lg border border-slate-800 bg-slate-950 shadow-sm shadow-slate-950/20" aria-labelledby="agent-flow-title">
    <div class="flex items-center justify-between gap-3 border-b border-slate-800 bg-slate-900 px-5 py-4">
      <div>
        <h2 id="agent-flow-title" class="text-sm font-semibold text-white">Agent 执行轨迹</h2>
        <p class="mt-1 text-xs text-slate-400">{{ flowSubtitle }}</p>
      </div>
      <span class="rounded-md border border-blue-300/30 bg-blue-300/10 px-2 py-1 text-xs font-medium text-blue-100">
        {{ statusLabel }}
      </span>
    </div>

    <ol class="relative space-y-4 px-5 py-5">
      <li
        v-for="(step, index) in steps"
        :key="step.id"
        class="relative flex gap-3"
      >
        <div
          v-if="index !== steps.length - 1"
          class="absolute left-4 top-8 h-[calc(100%+0.25rem)] w-px bg-slate-700"
          aria-hidden="true"
        ></div>

        <div
          class="relative z-10 flex h-8 w-8 shrink-0 items-center justify-center rounded-lg border transition"
          :class="getMarkerStyles(step, index)"
          :aria-label="`${step.label} ${getStepState(step, index)}`"
        >
          <Loader2Icon v-if="isActive(step)" class="h-4 w-4 animate-spin" aria-hidden="true" />
          <CheckIcon v-else-if="isCompleted(step, index)" class="h-4 w-4" aria-hidden="true" />
          <component v-else :is="step.icon" class="h-4 w-4" aria-hidden="true" />
        </div>

        <div class="min-w-0 flex-1 pb-1">
          <div class="flex items-center justify-between gap-3">
            <div class="min-w-0">
              <p class="text-[11px] font-semibold uppercase text-slate-500">{{ step.code }}</p>
              <h3 class="truncate text-sm font-semibold" :class="getTitleStyles(step, index)">
                {{ step.label }}
              </h3>
            </div>
            <span class="shrink-0 text-[11px] font-medium uppercase tracking-wide" :class="getStateStyles(step, index)">
              {{ getStepState(step, index) }}
            </span>
          </div>
          <p class="mt-1 text-xs leading-5 text-slate-400">{{ step.desc }}</p>
        </div>
      </li>
    </ol>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import {
  BrainCircuitIcon,
  CheckIcon,
  FilePenLineIcon,
  FileTextIcon,
  Loader2Icon,
  SearchIcon,
  ShieldCheckIcon
} from 'lucide-vue-next';

const props = defineProps({
  currentStep: { type: String, default: 'idle' },
  completedSteps: { type: Array, default: () => [] },
  flowType: { type: String, default: 'research' }
});

const researchSteps = [
  { id: 'planner', code: 'PLAN', label: '规划', desc: '拆解任务并制定检索计划', icon: BrainCircuitIcon },
  { id: 'researcher', code: 'EVIDENCE', label: '检索', desc: '收集文档证据和联网资料', icon: SearchIcon },
  { id: 'writer', code: 'DRAFT', label: '撰写', desc: '生成 Markdown 研究报告', icon: FileTextIcon },
  { id: 'reviewer', code: 'CHECK', label: '质检', desc: '检查报告质量并决定是否重试', icon: ShieldCheckIcon },
  { id: 'refiner', code: 'REVISE', label: '修订', desc: '基于同一会话继续调整报告', icon: FilePenLineIcon, optional: true }
];

const stockSteps = [
  { id: 'stock_resolve', code: 'RESOLVE', label: '股票解析', desc: '标准化 A 股代码与交易所', icon: BrainCircuitIcon },
  { id: 'data_snapshot', code: 'SNAPSHOT', label: '数据快照', desc: '拉取或复用财报、行情、新闻证据', icon: SearchIcon },
  { id: 'metric_engine', code: 'METRIC', label: '指标计算', desc: '用 Java 确定性计算核心财务指标', icon: FilePenLineIcon },
  { id: 'risk_assessment', code: 'RISK', label: '风险评分', desc: '按基本面、技术面、情绪、消息和市场环境评分', icon: ShieldCheckIcon },
  { id: 'evidence_collect', code: 'LEDGER', label: '证据账本', desc: '沉淀引用、缺失项和置信度', icon: SearchIcon },
  { id: 'writer', code: 'DRAFT', label: '撰写', desc: '生成固定八章节股票投研报告', icon: FileTextIcon },
  { id: 'reviewer', code: 'CITATION', label: '引用审查', desc: '检查数字、口径和证据充分性', icon: ShieldCheckIcon }
];

const steps = computed(() => props.flowType === 'stock' ? stockSteps : researchSteps);
const flowSubtitle = computed(() => props.flowType === 'stock'
  ? 'Resolve → Snapshot → Metric → Risk → Evidence → Writer → Reviewer'
  : 'Planner → Researcher → Writer → Reviewer');
const seenSteps = ref(new Set());

watch(
  () => props.currentStep,
  (step) => {
    if (step && step !== 'idle' && step !== 'done') {
      seenSteps.value = new Set([...seenSteps.value, step]);
    }
  },
  { immediate: true }
);

const currentStepIndex = computed(() => steps.value.findIndex((step) => step.id === props.currentStep));
const completedStepSet = computed(() => new Set([...props.completedSteps, ...seenSteps.value]));

const statusLabel = computed(() => {
  if (props.currentStep === 'idle') return '待开始';
  if (props.currentStep === 'done') return '已完成';
  return '进行中';
});

const isActive = (step) => props.currentStep === step.id;

const isCompleted = (step, index) => {
  if (!isActive(step) && completedStepSet.value.has(step.id)) return true;
  if (currentStepIndex.value > index) return true;
  return false;
};

const getStepState = (step, index) => {
  if (isActive(step)) return '进行中';
  if (isCompleted(step, index)) return '已完成';
  if (step.optional) return '可选';
  return '待开始';
};

const getMarkerStyles = (step, index) => {
  if (isActive(step)) return 'border-blue-300 bg-blue-300 text-blue-950 shadow-sm shadow-blue-900/40';
  if (isCompleted(step, index)) return 'border-emerald-600 bg-emerald-600 text-white';
  return 'border-slate-700 bg-slate-900 text-slate-500';
};

const getTitleStyles = (step, index) => {
  if (isActive(step)) return 'text-blue-100';
  if (isCompleted(step, index)) return 'text-white';
  return 'text-slate-500';
};

const getStateStyles = (step, index) => {
  if (isActive(step)) return 'text-blue-200';
  if (isCompleted(step, index)) return 'text-emerald-300';
  if (step.optional) return 'text-slate-400';
  return 'text-slate-500';
};
</script>
