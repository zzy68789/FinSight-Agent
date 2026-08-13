<template>
  <section class="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm" aria-labelledby="run-inspector-title">
    <button type="button" class="flex min-h-16 w-full items-center justify-between gap-4 px-5 text-left transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:px-6" :aria-expanded="expanded" @click="expanded = !expanded">
      <div class="flex min-w-0 items-center gap-3">
        <div class="flex h-9 w-9 shrink-0 items-center justify-center rounded-full bg-slate-950 text-amber-300">
          <ScanSearchIcon class="h-4 w-4" aria-hidden="true" />
        </div>
        <div class="min-w-0">
          <h2 id="run-inspector-title" class="text-sm font-bold text-slate-900">运行检查器</h2>
          <p class="mt-0.5 truncate text-[11px] text-slate-500">{{ inspectorSummary }}</p>
        </div>
      </div>
      <div class="flex shrink-0 items-center gap-2">
        <span v-if="projection.taskId" class="hidden font-mono text-[10px] text-slate-400 sm:inline">Task #{{ projection.taskId }}</span>
        <ChevronDownIcon class="h-4 w-4 text-slate-500 transition-transform" :class="expanded ? 'rotate-180' : ''" aria-hidden="true" />
      </div>
    </button>

    <div v-if="expanded" class="border-t border-slate-200">
      <div class="flex flex-col border-b border-slate-200 bg-slate-50 sm:flex-row sm:items-center sm:justify-between">
        <nav class="overflow-x-auto px-2" aria-label="运行检查视图">
          <div class="flex min-w-max" role="tablist">
            <button v-for="tab in tabs" :key="tab.id" type="button" role="tab" class="relative min-h-11 px-3 text-xs font-semibold transition focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600 sm:px-4" :class="activeTab === tab.id ? 'text-blue-800' : 'text-slate-500 hover:text-slate-800'" :aria-selected="activeTab === tab.id" @click="activeTab = tab.id">
              {{ tab.label }}
              <span v-if="tab.count !== null" class="ml-1 rounded-full bg-slate-200 px-1.5 py-0.5 font-mono text-[9px] text-slate-600">{{ tab.count }}</span>
              <span v-if="activeTab === tab.id" class="absolute inset-x-3 bottom-0 h-0.5 bg-blue-700"></span>
            </button>
          </div>
        </nav>
        <button v-if="trace || replay" type="button" class="mx-4 mb-3 inline-flex min-h-8 items-center gap-1.5 rounded-md border border-slate-200 bg-white px-2.5 text-[10px] font-semibold text-slate-500 hover:text-slate-800 sm:mb-0" @click="$emit('clear-replay')">
          <XIcon class="h-3.5 w-3.5" aria-hidden="true" />
          清除回放
        </button>
      </div>

      <div class="p-5 sm:p-6">
        <AgentTracePanel v-if="activeTab === 'trace'" :projection="projection" :trace="trace" />
        <EvidenceInspectorPanel v-else-if="activeTab === 'evidence'" :evidence="inspectorData.evidence" />
        <QualityGatePanel v-else-if="activeTab === 'quality'" :projection="projection" :metrics="inspectorData.metrics" :can-replay="Boolean(projection.taskId)" @feedback="(type, detail) => $emit('feedback', type, detail)" @replay="$emit('replay')" />
        <RuntimeLogPanel v-else :logs="projection.logs || []" :is-running="isRunning" />
      </div>
    </div>
  </section>
</template>

<script setup>
import { computed, ref, watch } from 'vue';
import { ChevronDownIcon, ScanSearchIcon, XIcon } from 'lucide-vue-next';
import { resolveResearchInspectorData, summarizeResearchEvidence } from '../../modules/researchRunInspector.js';
import AgentTracePanel from './AgentTracePanel.vue';
import EvidenceInspectorPanel from './EvidenceInspectorPanel.vue';
import QualityGatePanel from './QualityGatePanel.vue';
import RuntimeLogPanel from './RuntimeLogPanel.vue';

const props = defineProps({
  projection: { type: Object, required: true },
  replay: { type: Object, default: null },
  trace: { type: Object, default: null },
  isRunning: { type: Boolean, default: false }
});

defineEmits(['feedback', 'replay', 'clear-replay']);

const expanded = ref(false);
const activeTab = ref('trace');
const inspectorData = computed(() => resolveResearchInspectorData(props.projection, props.replay));
const evidenceSummary = computed(() => summarizeResearchEvidence(inspectorData.value.evidence));
const tabs = computed(() => [
  { id: 'trace', label: 'Agent 轨迹', count: props.projection.events?.length || 0 },
  { id: 'evidence', label: '证据检查', count: evidenceSummary.value.total },
  { id: 'quality', label: '质量门禁', count: props.projection.qualityGateDecision?.issues?.length || 0 },
  { id: 'logs', label: '诊断日志', count: props.projection.logs?.length || 0 }
]);
const inspectorSummary = computed(() => {
  if (!props.projection.taskId) return '任务启动后可查看事件、证据、门禁与诊断日志';
  if (props.trace) return `已加载持久化轨迹 · ${evidenceSummary.value.effective}/${evidenceSummary.value.total} 条有效证据`;
  if (props.isRunning) return `运行中 · ${props.projection.events?.length || 0} 个已提交事件`;
  return `${props.projection.events?.length || 0} 个事件 · ${evidenceSummary.value.total} 条证据`;
});

watch(() => props.trace, value => {
  if (!value) return;
  expanded.value = true;
  activeTab.value = 'trace';
});
</script>
