<template>
  <section class="rounded-lg border border-slate-200 bg-white p-5 shadow-sm" aria-labelledby="agent-flow-title">
    <div class="mb-5 flex items-center justify-between gap-3">
      <div>
        <h2 id="agent-flow-title" class="text-sm font-semibold text-slate-950">Agent Flow</h2>
        <p class="mt-1 text-xs text-slate-500">LangGraph4j node execution</p>
      </div>
      <span class="rounded-md border border-slate-200 bg-slate-50 px-2 py-1 text-xs font-medium text-slate-600">
        {{ statusLabel }}
      </span>
    </div>

    <ol class="relative space-y-4">
      <li
        v-for="(step, index) in steps"
        :key="step.id"
        class="relative flex gap-3"
      >
        <div
          v-if="index !== steps.length - 1"
          class="absolute left-4 top-8 h-[calc(100%+0.25rem)] w-px bg-slate-200"
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
            <h3 class="truncate text-sm font-semibold" :class="getTitleStyles(step, index)">
              {{ step.label }}
            </h3>
            <span class="shrink-0 text-[11px] font-medium uppercase tracking-wide" :class="getStateStyles(step, index)">
              {{ getStepState(step, index) }}
            </span>
          </div>
          <p class="mt-1 text-xs leading-5 text-slate-500">{{ step.desc }}</p>
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
  completedSteps: { type: Array, default: () => [] }
});

const steps = [
  { id: 'planner', label: 'Planner', desc: 'Task decomposition and retrieval plan', icon: BrainCircuitIcon },
  { id: 'researcher', label: 'Researcher', desc: 'Document retrieval and web evidence collection', icon: SearchIcon },
  { id: 'writer', label: 'Writer', desc: 'Markdown report generation', icon: FileTextIcon },
  { id: 'reviewer', label: 'Reviewer', desc: 'Quality check and retry decision', icon: ShieldCheckIcon },
  { id: 'refiner', label: 'Refiner', desc: 'Follow-up report revision for same thread', icon: FilePenLineIcon, optional: true }
];

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
  if (props.currentStep === 'idle') return 'Ready';
  if (props.currentStep === 'done') return 'Complete';
  return 'Running';
});

const isActive = (step) => props.currentStep === step.id;

const isCompleted = (step, index) => {
  if (!isActive(step) && completedStepSet.value.has(step.id)) return true;
  if (currentStepIndex.value > index) return true;
  return false;
};

const getStepState = (step, index) => {
  if (isActive(step)) return 'active';
  if (isCompleted(step, index)) return 'done';
  if (step.optional) return 'optional';
  return 'idle';
};

const getMarkerStyles = (step, index) => {
  if (isActive(step)) return 'border-blue-700 bg-blue-700 text-white shadow-sm';
  if (isCompleted(step, index)) return 'border-emerald-600 bg-emerald-600 text-white';
  return 'border-slate-200 bg-white text-slate-400';
};

const getTitleStyles = (step, index) => {
  if (isActive(step)) return 'text-blue-800';
  if (isCompleted(step, index)) return 'text-slate-950';
  return 'text-slate-500';
};

const getStateStyles = (step, index) => {
  if (isActive(step)) return 'text-blue-700';
  if (isCompleted(step, index)) return 'text-emerald-700';
  if (step.optional) return 'text-slate-400';
  return 'text-slate-400';
};
</script>
