<template>
  <ol class="grid grid-cols-3 gap-2" aria-label="研究任务准备状态">
    <li v-for="item in steps" :key="item.number" class="min-w-0">
      <div class="mb-2 h-1 rounded-full" :class="item.complete || item.active ? 'bg-blue-700' : 'bg-slate-200'"></div>
      <div class="flex items-center gap-2">
        <span
          class="flex h-6 w-6 shrink-0 items-center justify-center rounded-full text-[11px] font-bold"
          :class="item.complete ? 'bg-emerald-100 text-emerald-700' : item.active ? 'bg-blue-700 text-white' : 'bg-slate-100 text-slate-400'"
          :aria-current="item.active ? 'step' : undefined"
        >
          <CheckIcon v-if="item.complete" class="h-3.5 w-3.5" aria-hidden="true" />
          <span v-else>{{ item.number }}</span>
        </span>
        <span class="truncate text-xs font-semibold" :class="item.complete || item.active ? 'text-slate-800' : 'text-slate-400'">
          {{ item.label }}
        </span>
      </div>
    </li>
  </ol>
</template>

<script setup>
import { computed } from 'vue';
import { CheckIcon } from 'lucide-vue-next';

const props = defineProps({
  currentStep: {
    type: Number,
    default: 1
  },
  subjectReady: {
    type: Boolean,
    default: false
  },
  questionReady: {
    type: Boolean,
    default: false
  },
  runReady: {
    type: Boolean,
    default: false
  }
});

const steps = computed(() => [
  { number: 1, label: '确认标的', complete: props.subjectReady, active: props.currentStep === 1 && !props.subjectReady },
  {
    number: 2,
    label: '定义问题',
    complete: props.questionReady,
    active: props.currentStep === 2 && !props.questionReady
  },
  {
    number: 3,
    label: '启动研究',
    complete: props.runReady,
    active: props.currentStep === 3 && !props.runReady
  }
]);
</script>
