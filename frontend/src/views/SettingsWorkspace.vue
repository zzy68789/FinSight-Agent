<template>
    <main class="workspace-main mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <section class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
        <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
          <h2 class="text-base font-semibold text-blue-950">系统配置</h2>
          <p class="mt-1 text-sm text-slate-500">展示后端依赖与降级策略的当前状态</p>
        </div>
        <div class="grid grid-cols-1 divide-y divide-slate-100 lg:grid-cols-2 lg:divide-x lg:divide-y-0">
          <div v-for="item in configItems" :key="item.name" class="flex gap-4 p-5">
            <div class="flex h-10 w-10 shrink-0 items-center justify-center rounded-lg bg-slate-100 text-slate-700">
              <component :is="item.icon" class="h-5 w-5" aria-hidden="true" />
            </div>
            <div>
              <h3 class="text-sm font-semibold text-slate-950">{{ item.name }}</h3>
              <p class="mt-1 text-xs font-semibold uppercase tracking-wide text-blue-700">{{ item.status }}</p>
              <p class="mt-2 text-sm leading-6 text-slate-500">{{ item.desc }}</p>
            </div>
          </div>
        </div>
      </section>
    </main>
</template>

<script setup>
import {
  DatabaseIcon,
  Globe2Icon,
  LayersIcon,
  SearchIcon,
  ServerIcon
} from 'lucide-vue-next';

const configItems = [
  {
    name: 'LLM 服务',
    status: '后端配置',
    desc: '兼容 OpenAI 的对话模型，缺失时使用本地降级逻辑。',
    icon: ServerIcon
  },
  {
    name: 'Tavily 搜索',
    status: '可选密钥',
    desc: '联网检索来源；未配置密钥时返回降级结果。',
    icon: Globe2Icon
  },
  {
    name: 'MySQL',
    status: '必需',
    desc: '持久化任务、日志、报告和检查点数据。',
    icon: DatabaseIcon
  },
  {
    name: 'Redis',
    status: '可选缓存',
    desc: '缓存运行中任务状态和最新 SSE 事件。',
    icon: LayersIcon
  },
  {
    name: 'ChromaDB',
    status: '可选向量库',
    desc: '向量 RAG 存储；不可用时回退到本地内存实现。',
    icon: SearchIcon
  }
];
</script>


