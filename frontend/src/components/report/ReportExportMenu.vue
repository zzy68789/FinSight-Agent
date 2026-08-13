<template>
  <div ref="menuRoot" class="relative inline-flex">
    <button
      type="button"
      class="inline-flex min-h-9 items-center gap-2 rounded-md border border-slate-300 bg-white px-3 text-xs font-semibold text-slate-700 shadow-sm transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600/30 disabled:cursor-not-allowed disabled:opacity-45"
      :disabled="disabled || !reportId || Boolean(exportingFormat)"
      :aria-expanded="menuOpen"
      aria-haspopup="menu"
      @click="menuOpen = !menuOpen"
      @keydown.esc="menuOpen = false"
    >
      <Loader2Icon v-if="exportingFormat" class="h-4 w-4 animate-spin" aria-hidden="true" />
      <DownloadIcon v-else class="h-4 w-4" aria-hidden="true" />
      {{ exportingFormat ? `正在导出 ${formatLabel(exportingFormat)}` : label }}
      <ChevronDownIcon v-if="!exportingFormat" class="h-3.5 w-3.5" aria-hidden="true" />
    </button>

    <div
      v-if="menuOpen"
      class="absolute right-0 top-full z-30 mt-2 w-56 overflow-hidden rounded-md border border-slate-200 bg-white py-1 shadow-xl shadow-slate-900/10"
      role="menu"
      aria-label="选择报告导出格式"
    >
      <button
        v-for="format in REPORT_EXPORT_FORMATS"
        :key="format.value"
        type="button"
        class="flex w-full items-center justify-between gap-4 px-3 py-2.5 text-left transition hover:bg-blue-50 focus:bg-blue-50 focus:outline-none"
        role="menuitem"
        @click="download(format.value)"
      >
        <span class="text-xs font-semibold text-slate-800">{{ format.label }}</span>
        <small class="text-[10px] text-slate-400">{{ format.description }}</small>
      </button>
    </div>
  </div>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from 'vue';
import { ChevronDownIcon, DownloadIcon, Loader2Icon } from 'lucide-vue-next';
import { exportReport } from '../../services/api.js';
import {
  REPORT_EXPORT_FORMATS,
  saveReportArtifact
} from '../../modules/reportExport.js';

const props = defineProps({
  reportId: { type: Number, default: null },
  label: { type: String, default: '导出报告' },
  disabled: { type: Boolean, default: false }
});

const emit = defineEmits(['error', 'exported']);
const menuRoot = ref(null);
const menuOpen = ref(false);
const exportingFormat = ref('');

const formatLabel = value => REPORT_EXPORT_FORMATS.find(item => item.value === value)?.label || value;

const closeWhenClickingOutside = event => {
  if (!menuRoot.value?.contains(event.target)) menuOpen.value = false;
};

const download = async format => {
  if (!props.reportId || exportingFormat.value) return;
  menuOpen.value = false;
  exportingFormat.value = format;
  try {
    const artifact = await exportReport(props.reportId, format);
    saveReportArtifact(artifact);
    emit('exported', { reportId: props.reportId, format, filename: artifact.filename });
  } catch (error) {
    emit('error', `报告导出失败：${error?.message || '未知错误'}`);
  } finally {
    exportingFormat.value = '';
  }
};

onMounted(() => document.addEventListener('pointerdown', closeWhenClickingOutside));
onBeforeUnmount(() => document.removeEventListener('pointerdown', closeWhenClickingOutside));
</script>
