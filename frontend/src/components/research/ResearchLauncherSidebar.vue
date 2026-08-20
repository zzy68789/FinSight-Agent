<template>
  <section class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
    <header class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
      <div class="flex items-start justify-between gap-4">
        <div>
          <p class="text-[10px] font-bold uppercase tracking-[0.16em] text-amber-700">Research Launcher</p>
          <h2 class="mt-1 text-base font-semibold text-blue-950">定义本次研究任务</h2>
          <p class="mt-1 text-xs leading-5 text-slate-500">确认标的与问题后，由 Planner 自主选择工具和研究路径。</p>
        </div>
        <span class="rounded-md bg-white px-2 py-1 text-[10px] font-bold text-blue-700 ring-1 ring-blue-100">AGENT RUN</span>
      </div>
    </header>

    <div class="space-y-5 p-5 pb-24 lg:pb-5">
      <ResearchReadinessStepper
        :current-step="readiness.step"
        :subject-ready="Boolean(selectedSecurity)"
        :question-ready="Boolean(selectedSecurity && researchQuestion.trim())"
        :run-ready="readiness.canSubmit"
      />

      <div class="border-t border-slate-100 pt-5">
        <label for="security-subject-query" class="text-sm font-semibold text-slate-900">1. 搜索并确认证券</label>
        <p class="mt-1 text-xs text-slate-500">支持六位代码、交易所后缀或本地主档证券名称</p>
        <div class="relative mt-3">
          <SearchIcon class="pointer-events-none absolute left-3 top-3.5 h-4 w-4 text-slate-400" aria-hidden="true" />
          <input
            id="security-subject-query"
            :value="subjectQuery"
            type="text"
            role="combobox"
            :aria-expanded="candidates.length > 0 && !selectedSecurity"
            aria-controls="security-candidate-panel"
            autocomplete="off"
            class="min-h-11 w-full rounded-lg border border-slate-200 bg-white pl-9 pr-10 text-sm font-semibold text-slate-900 shadow-sm transition placeholder:font-normal placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20 disabled:cursor-not-allowed disabled:bg-slate-50"
            placeholder="例如：600519、贵州茅台"
            :disabled="isSubmitting"
            @input="$emit('update:subjectQuery', $event.target.value)"
          />
          <Loader2Icon v-if="securityState === 'resolving'" class="absolute right-3 top-3.5 h-4 w-4 animate-spin text-blue-600" aria-hidden="true" />
        </div>
        <p v-if="securityState === 'error'" class="mt-2 text-xs leading-5 text-rose-700">{{ securityError }}</p>
        <div id="security-candidate-panel" class="mt-3">
          <SecuritySubjectPreview
            :selected-security="selectedSecurity"
            :candidates="selectedSecurity ? [] : candidates"
            @select="$emit('select-security', $event)"
          />
        </div>
      </div>

      <fieldset :disabled="isSubmitting || !selectedSecurity" class="border-t border-slate-100 pt-5 disabled:opacity-55">
        <legend class="text-sm font-semibold text-slate-900">2. 选择研究意图</legend>
        <p class="mt-1 text-xs text-slate-500">意图只为 Planner 提供目标提示，不会写死执行流程</p>
        <div class="mt-3 grid grid-cols-2 gap-2">
          <button
            v-for="intent in intentOptions"
            :key="intent.value"
            type="button"
            class="min-h-20 rounded-lg border p-3 text-left transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
            :class="researchIntent === intent.value ? 'border-blue-700 bg-blue-50 text-blue-950' : 'border-slate-200 bg-white text-slate-700 hover:border-slate-300 hover:bg-slate-50'"
            :aria-pressed="researchIntent === intent.value"
            @click="$emit('update:researchIntent', intent.value)"
          >
            <span class="block text-xs font-bold">{{ intent.label }}</span>
            <span class="mt-1 block text-[11px] leading-4 text-slate-500">{{ intent.description }}</span>
          </button>
        </div>
      </fieldset>

      <div class="border-t border-slate-100 pt-5" :class="!selectedSecurity || isSubmitting ? 'opacity-55' : ''">
        <label for="research-question" class="text-sm font-semibold text-slate-900">3. 明确研究问题</label>
        <div v-if="suggestions.length" class="mt-3 flex gap-2 overflow-x-auto pb-1" aria-label="建议研究问题">
          <button
            v-for="suggestion in suggestions"
            :key="suggestion"
            type="button"
            class="max-w-[15rem] shrink-0 rounded-full border border-slate-200 bg-white px-3 py-1.5 text-left text-[11px] leading-4 text-slate-600 transition hover:border-blue-300 hover:bg-blue-50 hover:text-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600"
            :disabled="!selectedSecurity || isSubmitting"
            :title="suggestion"
            @click="$emit('update:researchQuestion', suggestion)"
          >
            {{ suggestion }}
          </button>
        </div>
        <textarea
          id="research-question"
          :value="researchQuestion"
          rows="5"
          maxlength="500"
          class="mt-3 w-full resize-y rounded-lg border border-slate-200 bg-white px-3 py-2.5 text-sm leading-6 text-slate-900 shadow-sm transition placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20 disabled:cursor-not-allowed disabled:bg-slate-50"
          placeholder="例如：最近两个季度毛利率变化的主要原因是什么？"
          :disabled="!selectedSecurity || isSubmitting"
          @input="$emit('update:researchQuestion', $event.target.value)"
        ></textarea>
        <div class="mt-1 flex items-center justify-between gap-3 text-[11px] text-slate-400">
          <span>用中性问题描述要解释的现象、期间或风险</span>
          <span>{{ researchQuestion.length }}/500</span>
        </div>
      </div>

      <ResearchScopeOptions
        :as-of-date="asOfDate"
        :time-horizon="timeHorizon"
        :research-depth="researchDepth"
        :search-mode="searchMode"
        :primary-security="selectedSecurity"
        :comparison-securities="comparisonSecurities"
        :uploaded-files="uploadedFiles"
        :is-dragging="isDragging"
        :is-document-ready="isDocumentReady"
        :disabled="isSubmitting"
        @update:as-of-date="$emit('update:asOfDate', $event)"
        @update:time-horizon="$emit('update:timeHorizon', $event)"
        @update:research-depth="$emit('update:researchDepth', $event)"
        @update:search-mode="$emit('update:searchMode', $event)"
        @update:comparison-securities="$emit('update:comparisonSecurities', $event)"
        @files-selected="$emit('files-selected', $event)"
        @clear-knowledge-base="$emit('clear-knowledge-base')"
        @drag-state-change="$emit('drag-state-change', $event)"
      />

      <ResearchSubmitBar
        :readiness="readiness"
        :is-submitting="isSubmitting"
        @submit="$emit('submit')"
      />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue';
import { Loader2Icon, SearchIcon } from 'lucide-vue-next';
import { researchIntentOptions, suggestedResearchQuestions } from '../../modules/researchLauncher.js';
import ResearchReadinessStepper from './ResearchReadinessStepper.vue';
import ResearchScopeOptions from './ResearchScopeOptions.vue';
import ResearchSubmitBar from './ResearchSubmitBar.vue';
import SecuritySubjectPreview from './SecuritySubjectPreview.vue';

const props = defineProps({
  subjectQuery: { type: String, required: true },
  candidates: { type: Array, default: () => [] },
  selectedSecurity: { type: Object, default: null },
  securityState: { type: String, default: 'idle' },
  securityError: { type: String, default: '' },
  researchIntent: { type: String, required: true },
  researchQuestion: { type: String, required: true },
  asOfDate: { type: String, required: true },
  timeHorizon: { type: String, required: true },
  researchDepth: { type: String, required: true },
  searchMode: { type: String, required: true },
  comparisonSecurities: { type: Array, default: () => [] },
  uploadedFiles: { type: Array, default: () => [] },
  isDragging: { type: Boolean, default: false },
  isDocumentReady: { type: Boolean, default: true },
  isSubmitting: { type: Boolean, default: false },
  readiness: { type: Object, required: true }
});

defineEmits([
  'update:subjectQuery',
  'update:researchIntent',
  'update:researchQuestion',
  'update:asOfDate',
  'update:timeHorizon',
  'update:researchDepth',
  'update:searchMode',
  'update:comparisonSecurities',
  'select-security',
  'files-selected',
  'clear-knowledge-base',
  'drag-state-change',
  'submit'
]);

const intentOptions = computed(() => researchIntentOptions(props.selectedSecurity?.assetType));
const suggestions = computed(() => suggestedResearchQuestions(props.selectedSecurity, props.researchIntent));
</script>
