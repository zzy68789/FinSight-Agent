<template>
  <div class="space-y-5">
    <div class="grid grid-cols-2 gap-3 sm:grid-cols-4">
      <QualityStat label="风险评分" :value="riskLabel" :tone="riskTone" />
      <QualityStat label="合规审查" :value="projection.compliance?.status || '-'" :tone="statusTone(projection.compliance?.status)" />
      <QualityStat label="自动评测" :value="evaluationLabel" :tone="statusTone(projection.evaluation?.status)" />
      <QualityStat label="最终路由" :value="gateRoute" :tone="statusTone(projection.qualityGateDecision?.status)" />
    </div>

    <div v-if="projection.stopReason && projection.stopReason !== 'COMPLETED'" class="rounded-lg border border-amber-200 bg-amber-50 px-4 py-3">
      <p class="text-xs font-bold text-amber-900">任务受控停止</p>
      <p class="mt-1 text-xs leading-5 text-amber-800">{{ projection.stopReason }}</p>
    </div>

    <div class="grid gap-4 lg:grid-cols-2">
      <section class="rounded-lg border border-slate-200 p-4">
        <h3 class="text-xs font-bold text-slate-900">确定性指标</h3>
        <div v-if="metrics.length" class="mt-3 divide-y divide-slate-100">
          <div v-for="(metric, index) in metrics.slice(0, 12)" :key="`${metric.metricName}-${index}`" class="flex items-center justify-between gap-3 py-2 text-xs">
            <span class="min-w-0 truncate text-slate-600">{{ metric.metricName || '-' }}</span>
            <span class="shrink-0 font-mono font-semibold" :class="metric.status === 'OK' ? 'text-emerald-700' : 'text-amber-700'">{{ metric.displayValue || metric.value || metric.status || '-' }}</span>
          </div>
        </div>
        <p v-else class="mt-3 text-xs text-slate-400">尚无指标计算结果。</p>
      </section>

      <section class="rounded-lg border border-slate-200 p-4">
        <h3 class="text-xs font-bold text-slate-900">风险维度</h3>
        <div v-if="projection.riskAssessment?.dimensions?.length" class="mt-3 divide-y divide-slate-100">
          <div v-for="dimension in projection.riskAssessment.dimensions" :key="dimension.name" class="py-2">
            <div class="flex items-center justify-between gap-3 text-xs">
              <span class="font-semibold text-slate-700">{{ dimension.name }}</span>
              <span class="font-mono text-slate-500">{{ dimension.score }}/10 · {{ dimension.weight }}%</span>
            </div>
            <p v-if="dimension.reason" class="mt-1 text-[11px] leading-5 text-slate-500">{{ dimension.reason }}</p>
          </div>
        </div>
        <p v-else class="mt-3 text-xs text-slate-400">尚无风险评分结果。</p>
      </section>
    </div>

    <section v-if="gateIssues.length" class="rounded-lg border border-amber-200 bg-amber-50/40 p-4">
      <h3 class="text-xs font-bold text-amber-950">统一质量门禁问题</h3>
      <div class="mt-3 divide-y divide-amber-100">
        <article v-for="(issue, index) in gateIssues" :key="`${issue.code}-${index}`" class="py-2 first:pt-0 last:pb-0">
          <div class="flex flex-wrap gap-2 text-[10px] font-bold text-amber-800">
            <span>{{ issue.source || issue.category || 'QUALITY_GATE' }}</span>
            <span>·</span>
            <span>{{ issue.code || issue.type || issue.severity || 'UNKNOWN' }}</span>
          </div>
          <p class="mt-1 text-xs leading-5 text-amber-900">{{ issue.detail || issue.description || issue.reason }}</p>
        </article>
      </div>
    </section>

    <section v-if="projection.evaluation?.metricScores?.length" class="rounded-lg border border-slate-200 p-4">
      <h3 class="text-xs font-bold text-slate-900">评测指标</h3>
      <div class="mt-3 overflow-x-auto">
        <table class="w-full min-w-[560px] text-left text-xs">
          <thead class="border-y border-slate-200 text-slate-400">
            <tr><th class="py-2">指标</th><th>评分</th><th>阈值</th><th>状态</th><th>门禁</th></tr>
          </thead>
          <tbody class="divide-y divide-slate-100">
            <tr v-for="score in projection.evaluation.metricScores" :key="score.metricName">
              <td class="py-2 font-medium text-slate-700">{{ score.metricName }}</td>
              <td class="font-mono">{{ score.score }}</td>
              <td class="font-mono">{{ score.threshold }}</td>
              <td :class="score.status === 'PASS' ? 'text-emerald-700' : 'text-amber-700'">{{ score.status }}</td>
              <td class="text-slate-500">{{ score.gateLevel || '-' }}</td>
            </tr>
          </tbody>
        </table>
      </div>
    </section>

    <section class="rounded-lg border border-slate-200 bg-slate-50 p-4">
      <div class="flex flex-col gap-3 sm:flex-row sm:items-end">
        <label class="min-w-0 flex-1 text-xs font-semibold text-slate-700">
          记录 Bad Case
          <input v-model="feedbackDetail" type="text" class="mt-1 min-h-10 w-full rounded-lg border border-slate-200 px-3 text-xs font-normal outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="可选：补充问题位置或说明" />
        </label>
        <button type="button" class="min-h-10 rounded-lg border border-slate-200 bg-white px-3 text-xs font-semibold text-slate-700 hover:bg-slate-100 disabled:opacity-45" :disabled="!canReplay" @click="$emit('replay')">
          加载持久化回放
        </button>
      </div>
      <div class="mt-3 flex flex-wrap gap-2">
        <button v-for="type in feedbackTypes" :key="type" type="button" class="min-h-8 rounded-md border border-slate-200 bg-white px-3 text-[11px] font-semibold text-slate-600 hover:border-blue-300 hover:text-blue-800 disabled:opacity-45" :disabled="!canReplay" @click="submitFeedback(type)">{{ type }}</button>
      </div>
    </section>
  </div>
</template>

<script setup>
import { computed, ref } from 'vue';
import QualityStat from './QualityStat.vue';

const props = defineProps({
  projection: { type: Object, required: true },
  metrics: { type: Array, default: () => [] },
  canReplay: { type: Boolean, default: false }
});

const emit = defineEmits(['feedback', 'replay']);
const feedbackDetail = ref('');
const feedbackTypes = ['数字错误', '引用错误', '逻辑错误', '信息过期'];
const riskLabel = computed(() => props.projection.riskAssessment
  ? `${props.projection.riskAssessment.finalScore ?? '-'}/10 · ${props.projection.riskAssessment.riskLevel || '-'}`
  : '-');
const riskTone = computed(() => Number(props.projection.riskAssessment?.finalScore || 0) > 6 ? 'danger' : 'neutral');
const evaluationLabel = computed(() => props.projection.evaluation
  ? `${props.projection.evaluation.status || '-'} · ${props.projection.evaluation.overallScore ?? '-'}`
  : '-');
const gateRoute = computed(() => props.projection.qualityGateDecision?.route
  || (props.projection.stopReason === 'COMPLETED' ? 'PASS' : '-'));
const gateIssues = computed(() => {
  const unified = props.projection.qualityGateDecision?.issues || [];
  const compliance = props.projection.compliance?.issues || [];
  return unified.length ? unified : compliance;
});

const statusTone = status => status === 'PASS' ? 'positive' : status === 'FAIL' ? 'danger' : 'neutral';
const submitFeedback = type => {
  emit('feedback', type, feedbackDetail.value);
  feedbackDetail.value = '';
};
</script>
