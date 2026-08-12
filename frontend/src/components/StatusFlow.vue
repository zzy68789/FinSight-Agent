<template>
  <section class="overflow-hidden rounded-lg border border-slate-700 bg-blue-950 shadow-sm shadow-slate-950/20" aria-labelledby="agent-flow-title">
    <div class="flex items-center justify-between gap-3 border-b border-slate-700 bg-blue-900 px-5 py-4">
      <div>
        <h2 id="agent-flow-title" class="text-sm font-semibold text-white">Agent 执行轨迹</h2>
        <p class="mt-1 text-xs text-slate-400">{{ flowSubtitle }}</p>
      </div>
      <span class="rounded-md border border-amber-300/30 bg-amber-300/10 px-2 py-1 font-mono text-[11px] font-medium tracking-wide text-amber-200">
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
    <div v-if="events.length" class="border-t border-slate-700 px-5 py-4">
      <p class="text-[11px] font-semibold uppercase tracking-wide text-slate-500">动态 Turn / Tool Timeline</p>
      <ol class="mt-3 space-y-2">
        <li
          v-for="event in events.slice(-10)"
          :key="event.id"
          class="flex items-start justify-between gap-3 rounded-md border border-slate-700 bg-slate-900/60 px-3 py-2"
        >
          <div class="min-w-0">
            <p class="truncate text-xs font-semibold text-slate-200">
              Turn {{ event.turnNo || 0 }} · {{ event.toolName || eventTypeLabel(event.type) }}
            </p>
            <p v-if="event.summary" class="mt-1 line-clamp-2 text-[11px] leading-4 text-slate-400">{{ event.summary }}</p>
          </div>
          <span class="shrink-0 text-[10px] font-semibold" :class="event.status === 'FAILED' ? 'text-rose-300' : event.status === 'DEGRADED' ? 'text-amber-200' : 'text-emerald-300'">
            {{ event.status }}
          </span>
        </li>
      </ol>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import {
  BrainCircuitIcon,
  CheckIcon,
  ClipboardCheckIcon,
  FilePenLineIcon,
  FileTextIcon,
  Loader2Icon,
  SearchIcon,
  ShieldCheckIcon
} from 'lucide-vue-next';

const props = defineProps({
  currentStep: { type: String, default: 'idle' },
  completedSteps: { type: Array, default: () => [] },
  events: { type: Array, default: () => [] }
});

const steps = [
  { id: 'run_created', code: 'RUN', label: '运行上下文', desc: '建立任务、预算、租约和可恢复状态', icon: BrainCircuitIcon },
  { id: 'plan_created', code: 'PLAN', label: '研究规划', desc: '根据自然语言问题生成假设和证据需求', icon: ClipboardCheckIcon },
  { id: 'tool_started', code: 'ACT', label: '工具与观察', desc: '自主选择白名单工具并更新证据账本', icon: SearchIcon },
  { id: 'replanned', code: 'REPLAN', label: '证据补充', desc: '根据观察或审查失败动态调整研究计划', icon: BrainCircuitIcon, optional: true },
  { id: 'synthesis_started', code: 'SYNTH', label: '报告综合', desc: '围绕研究问题组织确定性事实和引用', icon: FileTextIcon },
  { id: 'review_completed', code: 'GUARD', label: '最终门禁', desc: '执行数字、引用、合规和质量检查', icon: ShieldCheckIcon },
  { id: 'run_completed', code: 'STOP', label: '受控停止', desc: '通过后发布，证据不足或预算耗尽时明确停止', icon: CheckIcon }
];

const flowSubtitle = 'Plan → Tool → Observe → Replan / Synthesize → Guard → Stop';
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

const currentStepIndex = computed(() => steps.findIndex((step) => step.id === props.currentStep));
const completedStepSet = computed(() => new Set([...props.completedSteps, ...seenSteps.value]));

const statusLabel = computed(() => {
  if (props.currentStep === 'idle') return '待开始';
  if (props.currentStep === 'done' || props.currentStep === 'run_completed') return '已完成';
  if (props.currentStep === 'run_stopped') return '已停止';
  return '进行中';
});

const eventTypeLabel = (type) => {
  const labels = {
    run_created: '任务创建',
    plan_created: '研究规划',
    tool_started: '工具开始',
    tool_completed: '工具观察',
    replanned: '重新规划',
    synthesis_started: '开始综合',
    synthesis_completed: '报告生成',
    review_completed: '确定性门禁',
    run_completed: '任务完成',
    run_stopped: '受控停止'
  };
  return labels[type] || type;
};

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
  if (isActive(step)) return 'border-amber-300 bg-amber-300 text-slate-950 shadow-sm shadow-slate-950/40';
  if (isCompleted(step, index)) return 'border-emerald-600 bg-emerald-600 text-white';
  return 'border-slate-700 bg-slate-900 text-slate-500';
};

const getTitleStyles = (step, index) => {
  if (isActive(step)) return 'text-amber-100';
  if (isCompleted(step, index)) return 'text-white';
  return 'text-slate-500';
};

const getStateStyles = (step, index) => {
  if (isActive(step)) return 'text-amber-200';
  if (isCompleted(step, index)) return 'text-emerald-300';
  if (step.optional) return 'text-slate-400';
  return 'text-slate-500';
};
</script>
