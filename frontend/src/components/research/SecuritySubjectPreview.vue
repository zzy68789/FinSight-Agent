<template>
  <div>
    <div v-if="selectedSecurity" class="rounded-lg border border-emerald-200 bg-emerald-50/70 p-3">
      <div class="flex items-start justify-between gap-3">
        <div class="min-w-0">
          <div class="flex flex-wrap items-center gap-2">
            <span class="truncate text-sm font-semibold text-slate-950">
              {{ selectedSecurity.companyName || '证券名称待数据源补全' }}
            </span>
            <span class="rounded bg-white px-1.5 py-0.5 text-[10px] font-bold text-emerald-700 ring-1 ring-emerald-200">
              已确认
            </span>
          </div>
          <p class="mt-1 font-mono text-xs font-semibold text-slate-600">{{ selectedSecurity.fullCode }}</p>
          <p class="mt-1 text-xs text-slate-500">
            {{ assetTypeLabel(selectedSecurity.assetType) }}
            <span v-if="selectedSecurity.industry"> · {{ selectedSecurity.industry }}</span>
          </p>
        </div>
        <ShieldCheckIcon class="h-5 w-5 shrink-0 text-emerald-600" aria-hidden="true" />
      </div>
      <p v-if="!selectedSecurity.nameResolved" class="mt-2 text-[11px] leading-5 text-amber-700">
        代码已通过规则校验，本地主档暂无名称；正式运行仍会再次解析。
      </p>
    </div>

    <div v-else-if="candidates.length" class="overflow-hidden rounded-lg border border-slate-200 bg-white">
      <p class="border-b border-slate-100 bg-slate-50 px-3 py-2 text-[11px] font-semibold text-slate-500">
        找到 {{ candidates.length }} 个候选，请确认唯一证券
      </p>
      <div class="max-h-56 overflow-y-auto" role="listbox" aria-label="证券候选">
        <button
          v-for="candidate in candidates"
          :key="candidate.fullCode"
          type="button"
          role="option"
          class="flex min-h-14 w-full items-center justify-between gap-3 border-b border-slate-100 px-3 py-2.5 text-left transition last:border-0 hover:bg-blue-50 focus:bg-blue-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600"
          @click="$emit('select', candidate)"
        >
          <span class="min-w-0">
            <span class="block truncate text-sm font-semibold text-slate-900">
              {{ candidate.companyName || candidate.fullCode }}
            </span>
            <span class="mt-0.5 block text-xs text-slate-500">
              {{ candidate.fullCode }} · {{ assetTypeLabel(candidate.assetType) }}
            </span>
          </span>
          <span class="shrink-0 rounded bg-slate-100 px-2 py-1 text-[10px] font-semibold text-slate-500">
            {{ matchTypeLabel(candidate.matchType) }}
          </span>
        </button>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ShieldCheckIcon } from 'lucide-vue-next';

defineProps({
  selectedSecurity: {
    type: Object,
    default: null
  },
  candidates: {
    type: Array,
    default: () => []
  }
});

defineEmits(['select']);

const assetTypeLabel = assetType => assetType === 'ETF' ? 'ETF' : 'A 股';

const matchTypeLabel = matchType => ({
  EXACT_CODE: '代码匹配',
  EXACT_NAME: '名称匹配',
  CODE_PREFIX: '代码前缀',
  NAME_PREFIX: '名称前缀',
  NAME_CONTAINS: '名称包含'
}[matchType] || '候选');
</script>
