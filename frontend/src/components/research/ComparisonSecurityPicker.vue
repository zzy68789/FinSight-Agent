<template>
  <section class="rounded-lg border border-slate-200 bg-white p-3">
    <div class="flex items-start justify-between gap-3">
      <div>
        <p class="text-xs font-semibold text-slate-700">可比证券</p>
        <p class="mt-0.5 text-[11px] leading-4 text-slate-500">可选，最多 3 个同资产类型证券；将额外采集独立快照</p>
      </div>
      <span class="text-[11px] font-semibold text-slate-400">{{ modelValue.length }}/3</span>
    </div>

    <div v-if="modelValue.length" class="mt-3 flex flex-wrap gap-2">
      <span
        v-for="security in modelValue"
        :key="security.fullCode"
        class="inline-flex items-center gap-1.5 rounded-full bg-blue-50 px-2.5 py-1 text-[11px] font-semibold text-blue-800 ring-1 ring-inset ring-blue-100"
      >
        {{ security.fullCode }} · {{ security.companyName || '待识别名称' }}
        <button
          type="button"
          class="rounded-full p-0.5 text-blue-500 hover:bg-blue-100 hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600"
          :disabled="disabled"
          :aria-label="`移除可比证券 ${security.fullCode}`"
          @click="removeSecurity(security.fullCode)"
        >
          <XIcon class="h-3 w-3" aria-hidden="true" />
        </button>
      </span>
    </div>

    <div v-if="modelValue.length < 3" class="relative mt-3">
      <SearchIcon class="pointer-events-none absolute left-3 top-3 h-3.5 w-3.5 text-slate-400" aria-hidden="true" />
      <input
        v-model="query"
        type="text"
        class="min-h-10 w-full rounded-lg border border-slate-200 pl-8 pr-9 text-xs text-slate-800 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20 disabled:bg-slate-50"
        :disabled="disabled || !primarySecurity"
        placeholder="输入代码或名称后确认"
        aria-label="搜索可比证券"
      />
      <Loader2Icon v-if="loading" class="absolute right-3 top-3 h-3.5 w-3.5 animate-spin text-blue-600" aria-hidden="true" />
    </div>

    <div v-if="visibleCandidates.length" class="mt-2 overflow-hidden rounded-lg border border-slate-200">
      <button
        v-for="candidate in visibleCandidates"
        :key="candidate.fullCode"
        type="button"
        class="flex w-full items-center justify-between gap-3 border-b border-slate-100 px-3 py-2 text-left last:border-b-0 hover:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600"
        @click="selectSecurity(candidate)"
      >
        <span class="min-w-0">
          <span class="block text-xs font-semibold text-slate-800">{{ candidate.fullCode }}</span>
          <span class="block truncate text-[11px] text-slate-500">{{ candidate.companyName || '待识别名称' }}</span>
        </span>
        <span class="text-[10px] font-semibold text-blue-700">添加</span>
      </button>
    </div>
    <p v-else-if="message" class="mt-2 text-[11px] leading-4" :class="error ? 'text-rose-700' : 'text-slate-500'">
      {{ message }}
    </p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import { Loader2Icon, SearchIcon, XIcon } from 'lucide-vue-next';
import { searchSecurities } from '../../services/api.js';
import {
  appendComparisonSecurity,
  createLatestRequestGate,
  filterComparisonCandidates
} from '../../modules/researchLauncher.js';

const props = defineProps({
  primarySecurity: { type: Object, default: null },
  modelValue: { type: Array, default: () => [] },
  disabled: { type: Boolean, default: false }
});

const emit = defineEmits(['update:modelValue']);
const query = ref('');
const candidates = ref([]);
const loading = ref(false);
const error = ref('');
let searchTimer = null;
const searchGate = createLatestRequestGate();

const visibleCandidates = computed(() => filterComparisonCandidates(
  candidates.value,
  props.primarySecurity,
  props.modelValue
));
const message = computed(() => {
  if (error.value) return error.value;
  if (!query.value.trim()) return '';
  if (query.value.trim().length < 2) return '请至少输入 2 个字符';
  if (!loading.value && !visibleCandidates.value.length) return '未找到可添加的同类证券';
  return '';
});

watch(query, value => {
  const token = searchGate.issue();
  if (searchTimer) clearTimeout(searchTimer);
  candidates.value = [];
  error.value = '';
  const normalized = String(value || '').trim();
  if (normalized.length < 2) {
    loading.value = false;
    return;
  }
  loading.value = true;
  searchTimer = setTimeout(async () => {
    try {
      const result = await searchSecurities(normalized);
      if (!searchGate.isCurrent(token)) return;
      candidates.value = Array.isArray(result) ? result : [];
    } catch (requestError) {
      if (!searchGate.isCurrent(token)) return;
      error.value = requestError.message || '可比证券搜索失败';
    } finally {
      if (searchGate.isCurrent(token)) loading.value = false;
    }
  }, 400);
});

watch(() => props.primarySecurity?.fullCode, () => {
  query.value = '';
  candidates.value = [];
});

onBeforeUnmount(() => {
  searchGate.invalidate();
  if (searchTimer) clearTimeout(searchTimer);
});

const selectSecurity = candidate => {
  emit('update:modelValue', appendComparisonSecurity(props.modelValue, candidate, props.primarySecurity));
  query.value = '';
  candidates.value = [];
};

const removeSecurity = fullCode => {
  emit('update:modelValue', props.modelValue.filter(item => item.fullCode !== fullCode));
};
</script>
