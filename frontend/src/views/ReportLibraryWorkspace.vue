<template>
    <main class="workspace-main mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <section class="lg:col-span-4">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="text-base font-semibold text-blue-950">{{ reportScope === 'library' ? '报告库' : '报告版本' }}</h2>
                <p class="mt-1 text-sm text-slate-500">{{ reportScope === 'library' ? '当前用户保存的全部报告' : `当前会话：${activeThreadId}` }}</p>
              </div>
              <button type="button" class="rounded-lg border border-blue-100 bg-white p-2 text-slate-600 transition hover:bg-blue-50 hover:text-blue-700" @click="loadReports(activeThreadId)" aria-label="刷新报告">
                <RefreshCwIcon class="h-4 w-4" :class="isLoadingReports ? 'animate-spin' : ''" aria-hidden="true" />
              </button>
            </div>
            <div class="mt-4 grid grid-cols-2 gap-2 rounded-lg border border-blue-100 bg-white/80 p-1">
              <button type="button" class="min-h-9 rounded-md text-sm font-semibold transition" :class="reportScope === 'thread' ? 'bg-white text-blue-800 shadow-sm' : 'text-slate-600 hover:bg-white/70'" @click="switchReportScope('thread')">
                当前会话
              </button>
              <button type="button" class="min-h-9 rounded-md text-sm font-semibold transition" :class="reportScope === 'library' ? 'bg-white text-blue-800 shadow-sm' : 'text-slate-600 hover:bg-white/70'" @click="switchReportScope('library')">
                报告库
              </button>
            </div>
            <div v-if="reportScope === 'library'" class="mt-3 space-y-3">
              <input
                v-model="reportKeyword"
                type="search"
                class="min-h-10 w-full rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                placeholder="搜索报告..."
                @keyup.enter="loadReports(activeThreadId)"
              />
              <label class="flex items-center gap-2 text-sm font-medium text-slate-600">
                <input v-model="favoriteOnly" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600" @change="loadReports(activeThreadId)" />
                仅看收藏
              </label>
            </div>
          </div>

          <div class="divide-y divide-slate-100">
            <div v-if="reportError" class="px-5 py-4 text-sm text-rose-700">{{ reportError }}</div>
            <button
              v-for="report in reports"
              :key="report.id"
              type="button"
              class="block w-full px-5 py-4 text-left transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600"
              :class="selectedReport?.id === report.id ? 'bg-blue-50/70' : 'bg-white'"
              @click="selectReport(report)"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="text-sm font-semibold text-slate-950">版本 {{ report.version }}</span>
                <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(report.reviewStatus)">
                  {{ statusLabel(report.reviewStatus) }}
                </span>
              </div>
              <div class="mt-2 flex items-center justify-between gap-3 text-xs text-slate-500">
                <span class="truncate">{{ report.threadId }}</span>
                <StarIcon v-if="report.favorite" class="h-4 w-4 shrink-0 fill-amber-400 text-amber-500" aria-hidden="true" />
              </div>
              <p class="mt-1 text-xs text-slate-500">{{ formatDate(report.createdAt) }}</p>
            </button>
            <div v-if="!isLoadingReports && reports.length === 0" class="px-5 py-8 text-sm text-slate-500">
              暂无报告。
            </div>
          </div>
        </div>
      </section>

      <section class="lg:col-span-8">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/70 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-blue-950">报告预览</h2>
              <p class="mt-1 text-sm text-slate-500">查看、收藏、加入知识库或导出当前报告</p>
            </div>
            <div class="flex flex-wrap gap-2">
              <RouterLink v-if="selectedReport" :to="`/reports/${selectedReport.id}`" class="flex min-h-9 items-center gap-2 rounded-lg border border-amber-300 bg-amber-50 px-3 text-sm font-semibold text-amber-900 transition hover:bg-amber-100">
                <ExternalLinkIcon class="h-4 w-4" aria-hidden="true" />
                独立研究页
              </RouterLink>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="copySelectedReport">
                <CopyIcon class="h-4 w-4" aria-hidden="true" />
                复制
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="toggleFavoriteSelectedReport">
                <StarIcon class="h-4 w-4" :class="selectedReport?.favorite ? 'fill-amber-400 text-amber-500' : ''" aria-hidden="true" />
                {{ selectedReport?.favorite ? '已收藏' : '收藏' }}
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="indexSelectedReport">
                <BookOpenIcon class="h-4 w-4" aria-hidden="true" />
                加入 RAG
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport('pdf')">
                <DownloadIcon class="h-4 w-4" aria-hidden="true" />
                PDF
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport('docx')">
                <FileTextIcon class="h-4 w-4" aria-hidden="true" />
                Word
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport('md')">
                <FileTextIcon class="h-4 w-4" aria-hidden="true" />
                MD
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-rose-200 px-3 text-sm font-semibold text-rose-700 transition hover:bg-rose-50 disabled:opacity-45" :disabled="!selectedReport" @click="deleteSelectedReport">
                <Trash2Icon class="h-4 w-4" aria-hidden="true" />
                删除
              </button>
            </div>
          </div>
          <div class="p-5 sm:p-6">
            <article v-if="selectedReport" class="research-paper report-content prose prose-slate max-w-none">
              <div v-html="md.render(selectedReport.content || '')"></div>
            </article>
            <div v-else class="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-sm text-slate-500">
              请选择一个报告版本进行预览。
            </div>
          </div>
        </div>
      </section>
    </main>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink } from 'vue-router';
import {
  BookOpenIcon,
  CopyIcon,
  DownloadIcon,
  ExternalLinkIcon,
  FileTextIcon,
  RefreshCwIcon,
  StarIcon,
  Trash2Icon
} from 'lucide-vue-next';
import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';
import {
  currentThreadId,
  deleteReport as deleteReportApi,
  exportReport,
  getReport,
  getThreadReports,
  indexReportToKnowledgeBase,
  listReports as listReportLibrary,
  updateReportFavorite
} from '../services/api';
import { formatDate, statusLabel, statusStyles } from '../modules/presentation';
import { saveReportArtifact } from '../modules/reportExport.js';

const props = defineProps({
  threadId: {
    type: String,
    required: true
  },
  refreshRevision: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['thread-change', 'warning']);

const md = new MarkdownIt({
  html: true,
  linkify: true,
  typographer: true
});
md.use(mk);

const activeThreadId = computed(() => props.threadId);
const reports = ref([]);
const selectedReport = ref(null);
const isLoadingReports = ref(false);
const reportError = ref('');
const reportScope = ref('thread');
const reportKeyword = ref('');
const favoriteOnly = ref(false);

const switchReportScope = async (scope) => {
  reportScope.value = scope;
  selectedReport.value = null;
  await loadReports(props.threadId);
};

const loadReports = async (threadId = props.threadId) => {
  isLoadingReports.value = true;
  reportError.value = '';
  try {
    reports.value = reportScope.value === 'library'
      ? await listReportLibrary({ keyword: reportKeyword.value, favoriteOnly: favoriteOnly.value })
      : await getThreadReports(threadId);
    if ((!selectedReport.value
      || !reports.value.some((report) => report.id === selectedReport.value.id))
      && reports.value.length > 0) {
      selectedReport.value = reports.value[0];
    } else if (reports.value.length === 0) {
      selectedReport.value = null;
    }
  } catch (error) {
    reportError.value = error.message;
  } finally {
    isLoadingReports.value = false;
  }
};

const selectReport = async (report) => {
  try {
    selectedReport.value = await getReport(report.id);
    emit('thread-change', selectedReport.value.threadId || currentThreadId);
  } catch (error) {
    emit('warning', error.message);
  }
};

const copySelectedReport = async () => {
  if (!selectedReport.value?.content) return;
  try {
    await navigator.clipboard.writeText(selectedReport.value.content);
  } catch (error) {
    emit('warning', error.message);
  }
};

const downloadSelectedReport = async (format = 'pdf') => {
  if (!selectedReport.value) return;
  try {
    const artifact = await exportReport(selectedReport.value.id, format);
    saveReportArtifact(artifact);
  } catch (error) {
    emit('warning', `报告导出失败：${error.message}`);
  }
};

const toggleFavoriteSelectedReport = async () => {
  if (!selectedReport.value) return;
  try {
    const updated = await updateReportFavorite(
      selectedReport.value.id,
      !selectedReport.value.favorite
    );
    selectedReport.value = updated;
    reports.value = reports.value.map((report) => report.id === updated.id
      ? { ...report, favorite: updated.favorite }
      : report);
  } catch (error) {
    emit('warning', error.message);
  }
};

const indexSelectedReport = async () => {
  if (!selectedReport.value) return;
  try {
    await indexReportToKnowledgeBase(selectedReport.value.id);
    selectedReport.value = await getReport(selectedReport.value.id);
    reports.value = reports.value.map((report) => report.id === selectedReport.value.id
      ? selectedReport.value
      : report);
  } catch (error) {
    emit('warning', error.message);
  }
};

const deleteSelectedReport = async () => {
  if (!selectedReport.value) return;
  const confirmed = window.confirm('确定要从报告库删除这份报告吗？');
  if (!confirmed) return;

  try {
    await deleteReportApi(selectedReport.value.id);
    reports.value = reports.value.filter((report) => report.id !== selectedReport.value.id);
    selectedReport.value = reports.value[0] || null;
  } catch (error) {
    emit('warning', error.message);
  }
};

watch(() => props.threadId, () => {
  if (reportScope.value === 'thread') loadReports(props.threadId);
});
watch(() => props.refreshRevision, () => loadReports(props.threadId));
onMounted(() => loadReports(props.threadId));
</script>
