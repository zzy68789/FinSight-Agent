<template>
  <div ref="container" class="h-64 overflow-y-auto rounded-lg border border-slate-800 bg-slate-950 p-4 font-mono text-[11px] leading-5" aria-live="polite">
    <div v-if="!logs.length" class="text-slate-500">尚无诊断日志。</div>
    <div v-for="(log, index) in logs" :key="`${index}-${log}`" class="flex gap-2 py-0.5">
      <span class="shrink-0 text-blue-300">&gt;</span>
      <span class="break-all text-slate-300">{{ log }}</span>
    </div>
    <div v-if="isRunning" class="mt-2 animate-pulse text-blue-300">_</div>
  </div>
</template>

<script setup>
import { nextTick, ref, watch } from 'vue';

const props = defineProps({
  logs: { type: Array, default: () => [] },
  isRunning: { type: Boolean, default: false }
});

const container = ref(null);

watch(() => props.logs.length, async () => {
  await nextTick();
  if (container.value) container.value.scrollTop = container.value.scrollHeight;
});
</script>
