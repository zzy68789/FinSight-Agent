<template>
  <details class="group rounded-lg border border-slate-200 bg-slate-50/70">
    <summary class="flex min-h-11 cursor-pointer list-none items-center justify-between gap-3 px-3 py-2 text-sm font-semibold text-slate-700 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600">
      <span>
        高级研究范围
        <span class="ml-1 text-xs font-normal text-slate-400">截止日、深度与证据</span>
      </span>
      <ChevronDownIcon class="h-4 w-4 text-slate-400 transition group-open:rotate-180" aria-hidden="true" />
    </summary>

    <div class="space-y-4 border-t border-slate-200 p-3">
      <div class="grid grid-cols-2 gap-3">
        <label class="text-xs font-medium text-slate-600">
          研究截止日
          <input
            :value="asOfDate"
            type="date"
            class="mt-1 min-h-10 w-full rounded-lg border border-slate-200 px-2.5 text-sm text-slate-700 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
            :disabled="disabled"
            @input="$emit('update:asOfDate', $event.target.value)"
          />
        </label>
        <label class="text-xs font-medium text-slate-600">
          时间范围
          <select
            :value="timeHorizon"
            class="mt-1 min-h-10 w-full rounded-lg border border-slate-200 px-2.5 text-sm text-slate-700 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
            :disabled="disabled"
            @change="$emit('update:timeHorizon', $event.target.value)"
          >
            <option value="6M">近 6 个月</option>
            <option value="1Y">近 1 年</option>
            <option value="2Y">近 2 年</option>
            <option value="5Y">近 5 年</option>
          </select>
        </label>
        <label class="text-xs font-medium text-slate-600">
          研究深度
          <select
            :value="researchDepth"
            class="mt-1 min-h-10 w-full rounded-lg border border-slate-200 px-2.5 text-sm text-slate-700 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
            :disabled="disabled"
            @change="$emit('update:researchDepth', $event.target.value)"
          >
            <option value="quick">快速</option>
            <option value="standard">标准</option>
            <option value="deep">深度</option>
          </select>
        </label>
        <label class="text-xs font-medium text-slate-600">
          证据范围
          <select
            :value="searchMode"
            class="mt-1 min-h-10 w-full rounded-lg border border-slate-200 px-2.5 text-sm text-slate-700 outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
            :disabled="disabled"
            @change="$emit('update:searchMode', $event.target.value)"
          >
            <option value="hybrid">混合检索</option>
            <option value="document">仅上传文档</option>
          </select>
        </label>
      </div>

      <div>
        <div class="mb-2 flex items-center justify-between gap-3">
          <div>
            <p class="text-xs font-semibold text-slate-700">补充研究文档</p>
            <p class="mt-0.5 text-[11px] text-slate-500">可选，最多 5 个 PDF</p>
          </div>
          <span v-if="uploadedFiles.length" class="flex items-center gap-1 text-[11px] font-semibold" :class="isDocumentReady ? 'text-emerald-700' : 'text-amber-700'">
            <CheckCircle2Icon v-if="isDocumentReady" class="h-3.5 w-3.5" aria-hidden="true" />
            <Loader2Icon v-else class="h-3.5 w-3.5 animate-spin" aria-hidden="true" />
            {{ isDocumentReady ? '已解析' : '解析中' }}
          </span>
        </div>
        <label
          for="research-pdf-upload"
          class="relative flex min-h-20 cursor-pointer items-center gap-3 rounded-lg border border-dashed px-3 py-3 transition focus-within:ring-2 focus-within:ring-blue-600 focus-within:ring-offset-2"
          :class="disabled || !isDocumentReady ? 'cursor-not-allowed border-slate-200 bg-slate-100 opacity-70' : isDragging ? 'border-blue-500 bg-blue-50' : 'border-slate-300 bg-white hover:border-blue-400 hover:bg-blue-50/50'"
          @dragover.prevent="$emit('drag-state-change', true)"
          @dragleave.prevent="$emit('drag-state-change', false)"
          @drop.prevent="handleDrop"
        >
          <input
            id="research-pdf-upload"
            type="file"
            multiple
            accept=".pdf,application/pdf"
            class="absolute inset-0 cursor-pointer opacity-0 disabled:cursor-not-allowed"
            :disabled="disabled || !isDocumentReady"
            aria-label="上传补充研究 PDF"
            @change="handleFileSelect"
          />
          <span class="flex h-9 w-9 shrink-0 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
            <UploadCloudIcon class="h-4 w-4" aria-hidden="true" />
          </span>
          <span class="min-w-0">
            <span class="block text-xs font-semibold text-slate-700">
              {{ uploadedFiles.length ? `${uploadedFiles.length} 个文档已选择` : '点击或拖拽上传 PDF' }}
            </span>
            <span v-if="uploadedFiles.length" class="mt-1 block truncate text-[11px] text-slate-500">
              {{ uploadedFiles.map(file => file.name).join('、') }}
            </span>
            <span v-else class="mt-1 block text-[11px] text-slate-500">为 Agent 增加私有证据范围</span>
          </span>
        </label>
      </div>
    </div>
  </details>
</template>

<script setup>
import {
  CheckCircle2Icon,
  ChevronDownIcon,
  Loader2Icon,
  UploadCloudIcon
} from 'lucide-vue-next';

const props = defineProps({
  asOfDate: { type: String, required: true },
  timeHorizon: { type: String, required: true },
  researchDepth: { type: String, required: true },
  searchMode: { type: String, required: true },
  uploadedFiles: { type: Array, default: () => [] },
  isDragging: { type: Boolean, default: false },
  isDocumentReady: { type: Boolean, default: true },
  disabled: { type: Boolean, default: false }
});

const emit = defineEmits([
  'update:asOfDate',
  'update:timeHorizon',
  'update:researchDepth',
  'update:searchMode',
  'files-selected',
  'drag-state-change'
]);

const handleFileSelect = event => {
  if (props.disabled || !props.isDocumentReady) return;
  emit('files-selected', Array.from(event.target.files || []));
  event.target.value = '';
};

const handleDrop = event => {
  emit('drag-state-change', false);
  if (props.disabled || !props.isDocumentReady) return;
  emit('files-selected', Array.from(event.dataTransfer?.files || []));
};
</script>
