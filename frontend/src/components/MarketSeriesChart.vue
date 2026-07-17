<template>
  <div class="market-chart-shell">
    <div
      v-show="hasData"
      ref="chartRoot"
      class="market-chart"
      role="img"
      :aria-label="`${symbol || '证券'}近 ${series.length} 个交易日的 K 线与成交额图`"
    ></div>
    <div v-if="!hasData" class="market-chart-empty">
      <span class="empty-rule"></span>
      <p>当前快照未包含历史行情序列</p>
      <small>ETF 启用 TuShare 后将展示最近 60 个交易日</small>
    </div>
  </div>
</template>

<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import * as echarts from 'echarts/core';
import { BarChart, CandlestickChart, LineChart } from 'echarts/charts';
import {
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent
} from 'echarts/components';
import { CanvasRenderer } from 'echarts/renderers';

echarts.use([
  BarChart,
  CandlestickChart,
  LineChart,
  DataZoomComponent,
  GridComponent,
  LegendComponent,
  MarkLineComponent,
  TitleComponent,
  TooltipComponent,
  CanvasRenderer
]);

const props = defineProps({
  series: { type: Array, default: () => [] },
  symbol: { type: String, default: '' },
  unitNav: { type: [Number, String], default: null }
});

const chartRoot = ref(null);
const hasData = computed(() => props.series.some((point) => point?.close != null));
let chart = null;
let observer = null;

const numberOrNull = (value) => {
  if (value === null || value === undefined || value === '') return null;
  const parsed = Number(value);
  return Number.isFinite(parsed) ? parsed : null;
};

const renderChart = async () => {
  if (!hasData.value) {
    chart?.clear();
    return;
  }
  await nextTick();
  if (!chartRoot.value) return;
  chart ||= echarts.init(chartRoot.value, null, { renderer: 'canvas' });
  const rows = [...props.series]
    .filter((point) => point?.tradeDate && point?.close != null)
    .sort((left, right) => String(left.tradeDate).localeCompare(String(right.tradeDate)));
  const dates = rows.map((point) => String(point.tradeDate));
  const candles = rows.map((point) => [
    numberOrNull(point.open) ?? numberOrNull(point.close),
    numberOrNull(point.close),
    numberOrNull(point.low) ?? numberOrNull(point.close),
    numberOrNull(point.high) ?? numberOrNull(point.close)
  ]);
  const close = rows.map((point) => numberOrNull(point.close));
  const amount = rows.map((point) => numberOrNull(point.amount) ?? 0);
  const nav = numberOrNull(props.unitNav);
  const reducedMotion = window.matchMedia?.('(prefers-reduced-motion: reduce)').matches;
  chart.setOption({
    animation: !reducedMotion,
    backgroundColor: 'transparent',
    textStyle: { color: '#53676e', fontFamily: 'ui-sans-serif, system-ui, sans-serif' },
    legend: {
      top: 0,
      right: 4,
      itemWidth: 16,
      itemHeight: 7,
      textStyle: { color: '#5e7076', fontSize: 11 },
      data: ['日 K', '收盘', '成交额']
    },
    tooltip: {
      trigger: 'axis',
      axisPointer: { type: 'cross', lineStyle: { color: '#78919a', type: 'dashed' } },
      borderWidth: 1,
      borderColor: '#b9c8cc',
      backgroundColor: 'rgba(249, 251, 250, 0.96)',
      textStyle: { color: '#142c36', fontSize: 12 }
    },
    axisPointer: { link: [{ xAxisIndex: 'all' }] },
    grid: [
      { left: 48, right: 18, top: 34, height: '58%' },
      { left: 48, right: 18, top: '76%', height: '14%' }
    ],
    xAxis: [
      {
        type: 'category',
        data: dates,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#c8d3d5' } },
        axisLabel: { show: false }
      },
      {
        type: 'category',
        gridIndex: 1,
        data: dates,
        boundaryGap: true,
        axisLine: { lineStyle: { color: '#c8d3d5' } },
        axisLabel: {
          color: '#708188',
          fontSize: 10,
          formatter: (value) => value.length === 8 ? `${value.slice(4, 6)}-${value.slice(6)}` : value
        }
      }
    ],
    yAxis: [
      {
        scale: true,
        splitNumber: 4,
        axisLabel: { color: '#708188', fontSize: 10 },
        splitLine: { lineStyle: { color: 'rgba(83, 103, 110, 0.12)' } }
      },
      {
        scale: true,
        gridIndex: 1,
        axisLabel: {
          color: '#708188',
          fontSize: 9,
          formatter: (value) => value >= 10000 ? `${(value / 10000).toFixed(0)}万` : value
        },
        splitLine: { show: false }
      }
    ],
    dataZoom: [
      { type: 'inside', xAxisIndex: [0, 1], start: Math.max(0, 100 - (40 / rows.length) * 100), end: 100 }
    ],
    series: [
      {
        name: '日 K',
        type: 'candlestick',
        data: candles,
        itemStyle: {
          color: '#c15b49',
          color0: '#2f7d6d',
          borderColor: '#c15b49',
          borderColor0: '#2f7d6d'
        }
      },
      {
        name: '收盘',
        type: 'line',
        data: close,
        symbol: 'none',
        lineStyle: { color: '#b28b49', width: 1.5 },
        markLine: nav == null ? undefined : {
          silent: true,
          symbol: 'none',
          label: { formatter: `单位净值 ${nav}`, color: '#6e5934', fontSize: 10 },
          lineStyle: { color: '#b28b49', type: 'dashed', opacity: 0.75 },
          data: [{ yAxis: nav }]
        }
      },
      {
        name: '成交额',
        type: 'bar',
        xAxisIndex: 1,
        yAxisIndex: 1,
        data: amount,
        itemStyle: { color: 'rgba(59, 108, 121, 0.55)' },
        barMaxWidth: 8
      }
    ]
  }, true);
};

onMounted(() => {
  renderChart();
  observer = new ResizeObserver(() => chart?.resize());
  if (chartRoot.value) observer.observe(chartRoot.value);
});

watch(() => [props.series, props.unitNav], renderChart, { deep: true });

onBeforeUnmount(() => {
  observer?.disconnect();
  chart?.dispose();
});
</script>

<style scoped>
.market-chart-shell { min-height: 300px; }
.market-chart { width: 100%; height: 300px; }
.market-chart-empty {
  min-height: 300px;
  display: grid;
  place-content: center;
  text-align: center;
  color: #64767c;
}
.market-chart-empty p { margin: 12px 0 4px; color: #28424b; font-weight: 650; }
.market-chart-empty small { font-size: 12px; }
.empty-rule { width: 84px; height: 1px; margin: auto; background: linear-gradient(90deg, transparent, #b28b49, transparent); }
</style>
