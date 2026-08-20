<template>
    <main class="workspace-main mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <section class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
        <div class="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/70 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
          <div>
            <h2 class="text-base font-semibold text-blue-950">系统状态</h2>
            <p class="mt-1 text-sm text-slate-500">来自后端连通性探测与配置判定；外部服务未配置时显示对应降级状态</p>
          </div>
          <button type="button" class="min-h-9 rounded-lg border border-blue-100 bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-blue-50 disabled:opacity-50" :disabled="isLoading" @click="loadHealth">
            {{ isLoading ? '检测中…' : '重新检测' }}
          </button>
        </div>
        <p v-if="error" class="border-b border-rose-100 bg-rose-50 px-5 py-3 text-sm text-rose-700">{{ error }}</p>
        <div class="grid grid-cols-1 divide-y divide-slate-100 md:grid-cols-2 md:divide-y-0">
          <div v-for="item in configItems" :key="item.key" class="flex gap-4 border-b border-slate-100 p-5 md:odd:border-r">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
              <component :is="item.icon" class="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <h3 class="text-sm font-semibold text-slate-950">{{ item.name }}</h3>
              <p class="mt-1 text-xs font-semibold uppercase tracking-wide" :class="statusTone(item.status)">{{ statusLabel(item.status) }}</p>
              <p class="mt-2 text-sm leading-6 text-slate-500">{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </section>
    </main>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import {
  DatabaseIcon,
  Globe2Icon,
  LayersIcon,
  SearchIcon,
  ServerIcon
} from 'lucide-vue-next';
import { getSystemHealth } from '../services/api';

const componentStatus = ref({});
const isLoading = ref(false);
const error = ref('');

const definitions = [
  {
    key: 'llm',
    name: 'LLM 服务',
    desc: '兼容 OpenAI 的对话模型，缺失时使用本地降级逻辑。',
    icon: ServerIcon
  },
  {
    key: 'tavily',
    name: 'Tavily 搜索',
    desc: '联网检索来源；未配置密钥时返回降级结果。',
    icon: Globe2Icon
  },
  {
    key: 'mysql',
    name: 'MySQL',
    desc: '持久化任务、日志、报告和检查点数据。',
    icon: DatabaseIcon
  },
  {
    key: 'redis',
    name: 'Redis',
    desc: '缓存运行中任务状态和最新 SSE 事件。',
    icon: LayersIcon
  },
  {
    key: 'chroma',
    name: 'ChromaDB',
    desc: '向量 RAG 存储；不可用时回退到本地内存实现。',
    icon: SearchIcon
  },
  {
    key: 'tushare',
    name: 'TuShare 行情',
    desc: 'A股与 ETF 市场数据源；关闭或缺少密钥时使用公开来源与缺失标记。',
    icon: Globe2Icon
  }
];

const configItems = computed(() => definitions.map(item => ({
  ...item,
  status: componentStatus.value[item.key] || 'UNKNOWN'
})));

const statusLabel = status => ({
  UP: '正常',
  CONFIGURED: '已配置',
  DISABLED: '已关闭',
  MISSING: '未配置 · 降级',
  DOWN: '不可用',
  UNKNOWN: '待检测'
})[status] || status;

const statusTone = status => {
  if (['UP', 'CONFIGURED'].includes(status)) return 'text-emerald-700';
  if (['DOWN', 'MISSING'].includes(status)) return 'text-rose-700';
  return 'text-amber-700';
};

const loadHealth = async () => {
  isLoading.value = true;
  error.value = '';
  try {
    const response = await getSystemHealth();
    componentStatus.value = response?.components || {};
  } catch (reason) {
    error.value = reason.message || '系统状态检测失败';
  } finally {
    isLoading.value = false;
  }
};

onMounted(loadHealth);
</script>
