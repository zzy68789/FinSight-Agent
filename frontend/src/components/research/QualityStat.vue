<template>
  <div class="rounded-lg border px-3 py-3" :class="toneClass">
    <p class="text-[10px] font-semibold text-slate-500">{{ label }}</p>
    <p class="mt-1 text-xs font-bold" :class="valueClass">{{ value }}</p>
  </div>
</template>

<script setup>
import { computed } from 'vue';

const props = defineProps({
  label: { type: String, required: true },
  value: { type: String, required: true },
  tone: { type: String, default: 'neutral' }
});

const toneClass = computed(() => ({
  positive: 'border-emerald-100 bg-emerald-50/60',
  warning: 'border-amber-100 bg-amber-50/60',
  danger: 'border-rose-100 bg-rose-50/60',
  neutral: 'border-slate-200 bg-slate-50'
})[props.tone] || 'border-slate-200 bg-slate-50');
const valueClass = computed(() => ({
  positive: 'text-emerald-700', warning: 'text-amber-700', danger: 'text-rose-700', neutral: 'text-slate-800'
})[props.tone] || 'text-slate-800');
</script>
