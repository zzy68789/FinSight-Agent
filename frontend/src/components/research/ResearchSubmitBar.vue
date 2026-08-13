<template>
  <div class="fixed inset-x-0 bottom-0 z-40 border-t border-slate-200 bg-white/95 px-4 py-3 shadow-[0_-10px_30px_rgba(15,23,42,0.12)] backdrop-blur lg:sticky lg:z-10 lg:-mx-5 lg:-mb-5 lg:px-5 lg:py-4 lg:shadow-none">
    <div class="mb-3 flex items-start gap-2" aria-live="polite">
      <span class="mt-0.5 h-2 w-2 shrink-0 rounded-full" :class="statusDotClass"></span>
      <p class="text-xs leading-5" :class="readiness.canSubmit ? 'font-semibold text-emerald-700' : 'text-slate-600'">
        {{ readiness.message }}
      </p>
    </div>
    <button
      type="button"
      class="flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none"
      :disabled="!readiness.canSubmit || isSubmitting"
      @click="$emit('submit')"
    >
      <Loader2Icon v-if="isSubmitting" class="h-4 w-4 animate-spin" aria-hidden="true" />
      <SendIcon v-else class="h-4 w-4" aria-hidden="true" />
      <span>{{ isSubmitting ? '正在创建 Agent Run' : '启动研究 Agent' }}</span>
    </button>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { Loader2Icon, SendIcon } from 'lucide-vue-next';

const props = defineProps({
  readiness: {
    type: Object,
    required: true
  },
  isSubmitting: {
    type: Boolean,
    default: false
  }
});

defineEmits(['submit']);

const statusDotClass = computed(() => {
  if (props.readiness.canSubmit) return 'bg-emerald-500';
  if (props.isSubmitting || props.readiness.status === 'RESOLVING_SUBJECT') return 'bg-blue-500';
  if (props.readiness.status === 'WAITING_DOCUMENT') return 'bg-amber-500';
  return 'bg-slate-300';
});
</script>
