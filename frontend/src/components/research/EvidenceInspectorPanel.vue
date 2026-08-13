<template>
  <div>
    <div class="grid grid-cols-2 gap-px overflow-hidden rounded-lg border border-slate-200 bg-slate-200 sm:grid-cols-4">
      <QualityStat label="证据总数" :value="String(summary.total)" />
      <QualityStat label="有效证据" :value="String(summary.effective)" tone="positive" />
      <QualityStat label="缺失/异常" :value="String(summary.missing)" :tone="summary.missing ? 'warning' : 'positive'" />
      <QualityStat label="冲突项" :value="String(summary.conflicts)" :tone="summary.conflicts ? 'danger' : 'positive'" />
    </div>

    <div class="mt-5 flex flex-col gap-3 sm:flex-row">
      <label class="min-w-0 flex-1">
        <span class="sr-only">搜索证据</span>
        <input v-model="keyword" type="search" class="min-h-10 w-full rounded-lg border border-slate-200 px-3 text-xs outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="搜索指标、来源、报告期或证据摘录" />
      </label>
      <select v-model="sourceType" class="min-h-10 rounded-lg border border-slate-200 bg-white px-3 text-xs text-slate-700 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" aria-label="筛选证据来源">
        <option value="">全部来源</option>
        <option v-for="source in summary.sources" :key="source.sourceType" :value="source.sourceType">{{ source.sourceType }}（{{ source.count }}）</option>
      </select>
      <label class="inline-flex min-h-10 items-center gap-2 rounded-lg border border-slate-200 px-3 text-xs font-semibold text-slate-600">
        <input v-model="issuesOnly" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600" />
        只看异常
      </label>
    </div>

    <div v-if="filteredEvidence.length" class="mt-4 space-y-3">
      <article v-for="entry in filteredEvidence" :id="`evidence-${entry.index}`" :key="entry.key" class="rounded-lg border p-4" :class="entry.item.issueCode ? 'border-amber-200 bg-amber-50/50' : 'border-slate-200 bg-white'">
        <div class="flex flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
          <div class="min-w-0">
            <div class="flex flex-wrap items-center gap-2">
              <span class="font-mono text-[10px] font-bold text-blue-700">E{{ entry.index }}</span>
              <span class="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-600">{{ entry.item.sourceType || 'UNKNOWN' }}</span>
              <span v-if="entry.item.issueCode" class="rounded bg-amber-100 px-2 py-0.5 text-[10px] font-bold text-amber-800">{{ issueLabel(entry.item.issueCode) }}</span>
            </div>
            <h3 class="mt-2 text-xs font-bold text-slate-900">{{ entry.item.metricName || entry.item.sourceName || '未命名证据' }}</h3>
            <p class="mt-1 text-[11px] text-slate-500">{{ entry.item.sourceName || '未记录来源名称' }}<template v-if="entry.item.reportPeriod"> · {{ entry.item.reportPeriod }}</template></p>
          </div>
          <span v-if="entry.item.confidence !== undefined" class="shrink-0 font-mono text-[10px] text-slate-400">confidence {{ entry.item.confidence }}</span>
        </div>
        <p v-if="entry.item.excerpt" class="mt-3 border-l-2 border-slate-200 pl-3 text-xs leading-5 text-slate-600">{{ entry.item.excerpt }}</p>
        <p v-if="entry.item.normalizedValue !== undefined || entry.item.rawValue !== undefined" class="mt-3 font-mono text-xs font-semibold text-slate-800">{{ entry.item.normalizedValue ?? entry.item.rawValue }}</p>
        <a v-if="safeUrl(entry.item.sourceUrl || entry.item.url)" :href="safeUrl(entry.item.sourceUrl || entry.item.url)" target="_blank" rel="noopener noreferrer" class="mt-3 inline-flex text-[11px] font-semibold text-blue-700 hover:underline">查看原始来源</a>
      </article>
    </div>
    <p v-else class="mt-4 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-5 py-10 text-center text-xs text-slate-400">{{ evidence.length ? '没有符合筛选条件的证据。' : '运行完成或加载可信度回放后显示证据账本。' }}</p>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import { summarizeResearchEvidence } from '../../modules/researchRunInspector.js';
import QualityStat from './QualityStat.vue';

const props = defineProps({
  evidence: { type: Array, default: () => [] }
});

const keyword = ref('');
const sourceType = ref('');
const issuesOnly = ref(false);
const summary = computed(() => summarizeResearchEvidence(props.evidence));
const filteredEvidence = computed(() => {
  const search = keyword.value.trim().toLowerCase();
  return props.evidence
    .map((item, index) => ({
      item,
      index: index + 1,
      key: `${index}-${item.sourceType || ''}-${item.metricName || item.sourceName || ''}`
    }))
    .filter(entry => !sourceType.value || entry.item.sourceType === sourceType.value)
    .filter(entry => !issuesOnly.value || Boolean(entry.item.issueCode))
    .filter(entry => !search || [
      entry.item.metricName,
      entry.item.sourceName,
      entry.item.reportPeriod,
      entry.item.excerpt,
      entry.item.issueCode
    ].some(value => String(value || '').toLowerCase().includes(search)));
});

const issueLabel = issueCode => ({
  DATA_MISSING: '数据缺失',
  MISSING_INPUT: '输入缺失',
  EVIDENCE_CONFLICT: '证据冲突'
})[issueCode] || issueCode;
const safeUrl = value => {
  const url = String(value || '').trim();
  return /^https?:\/\//i.test(url) ? url : '';
};
</script>
