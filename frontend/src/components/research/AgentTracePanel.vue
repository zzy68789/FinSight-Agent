<template>
  <div class="space-y-5">
    <div v-if="trace" class="grid grid-cols-2 border-y border-slate-200 text-xs sm:grid-cols-4">
      <div class="px-3 py-3 first:pl-0">
        <p class="text-[10px] text-slate-400">任务状态</p>
        <p class="mt-1 font-bold text-slate-900">{{ trace.status || '-' }}</p>
      </div>
      <div class="border-l border-slate-200 px-3 py-3">
        <p class="text-[10px] text-slate-400">报告版本</p>
        <p class="mt-1 font-bold text-slate-900">v{{ trace.reportVersion || '-' }}</p>
      </div>
      <div class="border-l border-slate-200 px-3 py-3">
        <p class="text-[10px] text-slate-400">证据有效率</p>
        <p class="mt-1 font-bold text-slate-900">{{ trace.evidenceEffective || 0 }}/{{ trace.evidenceTotal || 0 }}</p>
      </div>
      <div class="border-l border-slate-200 px-3 py-3 last:pr-0">
        <p class="text-[10px] text-slate-400">生成方式</p>
        <p class="mt-1 font-bold" :class="trace.cacheHit ? 'text-emerald-700' : 'text-blue-700'">{{ trace.cacheHit ? '缓存复用' : '实时生成' }}</p>
      </div>
    </div>

    <section>
      <div class="flex items-end justify-between gap-3">
        <div>
          <h3 class="text-xs font-bold text-slate-900">Agent 事件轨迹</h3>
          <p class="mt-1 text-[11px] text-slate-500">实时 SSE 与历史 Trace 共用同一个事件投影。</p>
        </div>
        <span v-if="projection.taskId" class="font-mono text-[10px] text-slate-400">Task #{{ projection.taskId }}</span>
      </div>
      <ol v-if="events.length" class="mt-3 border-l border-slate-200 pl-4">
        <li v-for="event in events" :key="event.id" class="relative pb-4 last:pb-0">
          <span class="absolute -left-[1.17rem] top-1.5 h-2 w-2 rounded-full border-2 border-white" :class="eventDotClass(event.status)"></span>
          <div class="flex flex-wrap items-center justify-between gap-2">
            <p class="text-xs font-bold text-slate-800">{{ event.toolName || eventTypeLabel(event.type) }}</p>
            <span class="font-mono text-[10px] text-slate-400">Turn {{ event.turnNo || 0 }}<template v-if="event.sequence"> · #{{ event.sequence }}</template></span>
          </div>
          <p v-if="event.summary || event.reason" class="mt-1 text-xs leading-5 text-slate-500">{{ event.summary || event.reason }}</p>
          <p v-if="event.durationMs" class="mt-1 font-mono text-[10px] text-slate-400">{{ event.durationMs }} ms</p>
        </li>
      </ol>
      <p v-else class="mt-3 rounded-lg border border-dashed border-slate-200 bg-slate-50 px-5 py-8 text-center text-xs text-slate-400">尚无已提交的 Agent 事件。</p>
    </section>

    <section v-if="trace" class="border-t border-slate-200 pt-5">
      <h3 class="text-xs font-bold text-slate-900">研究配置快照</h3>
      <div class="mt-3 grid gap-3 sm:grid-cols-2">
        <div class="min-w-0 rounded-md bg-slate-50 p-3">
          <span class="text-[10px] font-semibold text-slate-400">Data Snapshot Hash</span>
          <p class="mt-1 truncate font-mono text-[10px] text-slate-700" :title="trace.dataSnapshotHash">{{ trace.dataSnapshotHash || '-' }}</p>
        </div>
        <div class="min-w-0 rounded-md bg-slate-50 p-3">
          <span class="text-[10px] font-semibold text-slate-400">Generation Context Hash</span>
          <p class="mt-1 truncate font-mono text-[10px] text-slate-700" :title="trace.generationContextHash">{{ trace.generationContextHash || '-' }}</p>
        </div>
      </div>
    </section>

    <section v-if="trace?.retrievalResults?.length" class="border-t border-slate-200 pt-5">
      <h3 class="text-xs font-bold text-slate-900">混合检索轨迹</h3>
      <article v-for="(retrieval, index) in trace.retrievalResults" :key="`${retrieval.query}-${index}`" class="mt-3 rounded-lg border border-slate-200 p-3">
        <p class="line-clamp-2 text-xs font-semibold text-slate-800">{{ retrieval.query || '未记录检索问题' }}</p>
        <div class="mt-2 flex flex-wrap gap-3 text-[10px] text-slate-500">
          <span>候选 {{ retrieval.candidateCount || 0 }}</span>
          <span>接受 {{ retrieval.acceptedCount || 0 }}</span>
          <span>过滤 {{ retrieval.filteredCount || 0 }}</span>
          <span class="font-mono">{{ retrieval.durationMs || 0 }} ms</span>
        </div>
        <div v-if="retrieval.traceEntries?.length" class="mt-3 divide-y divide-slate-100 border-t border-slate-100">
          <div v-for="entry in retrieval.traceEntries" :key="`${entry.rank}-${entry.source}`" class="flex items-center justify-between gap-3 py-2 text-[11px]">
            <span class="min-w-0 truncate text-slate-700">#{{ entry.rank }} {{ entry.source }}</span>
            <span class="shrink-0 font-mono text-slate-400">{{ (entry.channels || []).join('+') }} · {{ entry.fusionScore }}</span>
          </div>
        </div>
      </article>
    </section>

    <section v-if="trace?.stages?.length" class="border-t border-slate-200 pt-5">
      <h3 class="text-xs font-bold text-slate-900">持久化阶段记录</h3>
      <div class="mt-3 overflow-x-auto">
        <table class="w-full min-w-[520px] text-left text-xs">
          <thead class="border-y border-slate-200 text-slate-400">
            <tr><th class="py-2">阶段</th><th>尝试</th><th>状态</th><th>耗时</th></tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="stage in trace.stages" :key="`${stage.stage}-${stage.attemptNo}-${stage.createdAt}`">
              <td class="py-2 font-medium text-slate-800">{{ stage.stage }}</td>
              <td>{{ stage.attemptNo }}</td>
              <td>{{ stage.status }}</td>
              <td class="font-mono text-slate-500">{{ stage.durationMs || 0 }} ms</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed } from 'vue';
import { agentEventTypeLabel } from '../../modules/agentEventProjection.js';

const props = defineProps({
  projection: { type: Object, required: true },
  trace: { type: Object, default: null }
});

const events = computed(() => [...(props.projection.events || [])]);
const eventTypeLabel = type => agentEventTypeLabel(type);
const eventDotClass = status => ['SUCCESS', 'PASS', 'COMPLETED'].includes(status)
  ? 'bg-emerald-500'
  : ['FAILED', 'ERROR'].includes(status) ? 'bg-rose-500' : 'bg-blue-500';
</script>
