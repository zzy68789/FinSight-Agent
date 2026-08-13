<template>
  <section class="overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm shadow-slate-200/70" aria-labelledby="research-report-title">
    <header class="relative border-b border-slate-200 bg-[#f7f9f8] px-5 py-5 sm:px-7">
      <div class="absolute inset-y-0 left-0 w-1 bg-amber-600"></div>
      <div class="flex flex-col gap-4 sm:flex-row sm:items-start sm:justify-between">
        <div>
          <p class="text-[10px] font-bold uppercase tracking-[0.2em] text-amber-700">FinSight Research Folio</p>
          <h2 id="research-report-title" class="mt-1 font-serif text-xl font-semibold text-slate-950">研究报告</h2>
          <p class="mt-1 text-xs text-slate-500">通过引用、合规与评测门禁后发布的权威正文</p>
        </div>
        <div class="flex flex-col items-start gap-3 sm:items-end">
          <div class="flex flex-wrap gap-2 text-[10px] font-semibold">
            <span v-if="runSnapshot?.ticker" class="rounded border border-slate-200 bg-white px-2 py-1 font-mono text-slate-700">{{ runSnapshot.ticker }}</span>
            <span v-if="runSnapshot?.comparisonTickers?.length" class="rounded border border-blue-200 bg-blue-50 px-2 py-1 text-blue-700">对比 {{ runSnapshot.comparisonTickers.length }}</span>
            <span class="rounded border border-slate-200 bg-white px-2 py-1 text-slate-600">{{ searchModeLabel }}</span>
            <span class="rounded border px-2 py-1" :class="isRunning ? 'border-amber-200 bg-amber-50 text-amber-800' : 'border-slate-200 bg-white text-slate-600'">{{ currentStepLabel }}</span>
          </div>
          <ReportExportMenu
            v-if="reportId && reportContent && !isRunning"
            :report-id="reportId"
            label="导出已发布报告"
            @error="$emit('export-error', $event)"
          />
        </div>
      </div>
      <p v-if="runSnapshot?.researchQuestion" class="mt-4 max-w-3xl border-l-2 border-slate-300 pl-3 text-xs leading-5 text-slate-600">
        {{ runSnapshot.researchQuestion }}
      </p>
    </header>

    <div class="min-h-[34rem] p-5 sm:p-7 lg:p-9">
      <div v-if="!reportContent && !isRunning" class="flex min-h-[29rem] flex-col justify-center">
        <div class="mx-auto w-full max-w-3xl text-center">
          <div class="mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-slate-200 bg-slate-50 text-slate-600">
            <BookOpenTextIcon class="h-6 w-6" aria-hidden="true" />
          </div>
          <h3 class="mt-5 font-serif text-xl font-semibold text-slate-950">从一个可验证的研究问题开始</h3>
          <p class="mx-auto mt-2 max-w-xl text-sm leading-6 text-slate-500">FinSight 会把财务事实、估值线索和风险证据组织为可复盘的研究报告。</p>
        </div>

        <div class="mx-auto mt-8 grid w-full max-w-3xl gap-px overflow-hidden rounded-lg border border-slate-200 bg-slate-200 md:grid-cols-3">
          <article v-for="capability in capabilities" :key="capability.title" class="bg-white p-4">
            <component :is="capability.icon" class="h-5 w-5 text-blue-700" aria-hidden="true" />
            <h4 class="mt-3 text-xs font-bold text-slate-900">{{ capability.title }}</h4>
            <p class="mt-1 text-xs leading-5 text-slate-500">{{ capability.description }}</p>
          </article>
        </div>

        <div class="mx-auto mt-6 w-full max-w-3xl border-t border-slate-200 pt-5 text-left">
          <p class="text-[10px] font-bold uppercase tracking-[0.16em] text-slate-400">研究问题示例</p>
          <div class="mt-3 flex flex-wrap gap-2">
            <button v-for="question in sampleQuestions" :key="question" type="button" class="min-h-9 rounded-md border border-slate-200 bg-white px-3 text-left text-xs text-slate-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600/30" @click="$emit('select-example', question)">
              {{ question }}
            </button>
          </div>
          <p class="mt-3 text-[11px] text-slate-400">点击示例只会填入左侧研究问题，不会直接创建任务。</p>
        </div>
      </div>

      <div v-else-if="isRunning && !reportContent" class="flex min-h-[29rem] items-center justify-center">
        <div class="w-full max-w-xl">
          <div class="flex items-center gap-3 border-b border-slate-200 pb-5">
            <div class="flex h-10 w-10 items-center justify-center rounded-full bg-amber-50 text-amber-700">
              <Loader2Icon class="h-5 w-5 animate-spin" aria-hidden="true" />
            </div>
            <div>
              <h3 class="text-sm font-semibold text-slate-950">Research Agent 正在形成报告</h3>
              <p class="mt-1 text-xs text-slate-500">当前权威事件：{{ currentStepLabel }}</p>
            </div>
          </div>
          <div class="mt-6 space-y-3" aria-hidden="true">
            <div class="h-2.5 w-1/3 animate-pulse rounded bg-slate-200"></div>
            <div class="h-2.5 w-full animate-pulse rounded bg-slate-100"></div>
            <div class="h-2.5 w-5/6 animate-pulse rounded bg-slate-100"></div>
            <div class="mt-6 h-32 animate-pulse rounded bg-slate-50"></div>
          </div>
        </div>
      </div>

      <article v-else class="research-paper report-content prose prose-slate mx-auto max-w-none lg:max-w-3xl">
        <div v-html="renderedReport"></div>
        <span v-if="isTyping" class="ml-1 inline-block h-5 w-2 animate-pulse bg-blue-700 align-middle"></span>
      </article>
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import {
  BookOpenTextIcon,
  ChartNoAxesCombinedIcon,
  Loader2Icon,
  ScaleIcon,
  ShieldCheckIcon
} from 'lucide-vue-next';
import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';
import ReportExportMenu from '../report/ReportExportMenu.vue';
import { agentEventTypeLabel } from '../../modules/agentEventProjection.js';

const props = defineProps({
  reportContent: { type: String, default: '' },
  isRunning: { type: Boolean, default: false },
  isTyping: { type: Boolean, default: false },
  currentStep: { type: String, default: 'idle' },
  searchMode: { type: String, default: 'hybrid' },
  runSnapshot: { type: Object, default: null },
  reportId: { type: Number, default: null }
});

defineEmits(['select-example', 'export-error']);

const md = new MarkdownIt({ html: true, linkify: true, typographer: true });
md.use(mk);

const capabilities = [
  { title: '财务表现', description: '追踪盈利质量、现金流和偿债能力。', icon: ChartNoAxesCombinedIcon },
  { title: '估值观察', description: '把估值结论放回基本面与行业语境。', icon: ScaleIcon },
  { title: '风险与证据审查', description: '检查引用覆盖、数据缺失和合规风险。', icon: ShieldCheckIcon }
];
const sampleQuestions = [
  '近三年盈利质量和现金流是否匹配？',
  '当前估值与基本面是否存在明显背离？',
  '近期最值得持续跟踪的经营风险是什么？'
];

const renderedReport = computed(() => {
  let raw = props.reportContent || '';
  raw = raw.replace(/\\\[/g, () => '$$').replace(/\\\]/g, () => '$$');
  raw = raw.replace(/\\\(/g, '$').replace(/\\\)/g, '$');
  raw = raw.replace(/\[\s*(\\text|\\frac|\\sum|\\int)/g, '$$$$ $1');
  return md.render(raw);
});
const currentStepLabel = computed(() => agentEventTypeLabel(props.currentStep));
const searchModeLabel = computed(() => props.searchMode === 'document' ? '仅文档' : '混合检索');
</script>
