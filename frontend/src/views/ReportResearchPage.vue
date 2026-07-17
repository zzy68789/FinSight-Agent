<template>
  <div class="archive-page">
    <header class="archive-header">
      <div class="archive-header-inner">
        <RouterLink to="/" class="back-link" aria-label="返回研究工作台">
          <ArrowLeftIcon :size="16" aria-hidden="true" />
          研究工作台
        </RouterLink>
        <div class="archive-brand" aria-label="FinSight 报告档案">
          <span class="brand-seal">FS</span>
          <span><b>FinSight</b><small>REPORT ARCHIVE</small></span>
        </div>
        <div v-if="report" class="header-status">
          <span :class="['status-dot', report.reviewStatus === 'PASS' ? 'is-pass' : 'is-fail']"></span>
          {{ report.reviewStatus || 'UNKNOWN' }} · V{{ report.version }}
        </div>
      </div>
    </header>

    <main v-if="isLoading" class="state-panel">
      <LoaderCircleIcon class="spin" :size="24" aria-hidden="true" />
      <p>正在装订报告、证据与行情快照…</p>
    </main>

    <main v-else-if="error" class="state-panel is-error">
      <ShieldAlertIcon :size="28" aria-hidden="true" />
      <h1>报告档案暂不可用</h1>
      <p>{{ error }}</p>
      <RouterLink to="/" class="primary-link">返回登录或报告库</RouterLink>
    </main>

    <main v-else-if="report" class="archive-main">
      <section class="identity-strip" aria-labelledby="report-title">
        <div class="identity-primary">
          <p class="eyebrow">VERIFIED RESEARCH RECORD · #{{ report.id }}</p>
          <h1 id="report-title">{{ subject.fullCode || report.threadId }}</h1>
          <p>{{ subject.companyName || '证券研究报告' }} · {{ assetLabel }}</p>
        </div>
        <dl class="identity-metrics">
          <div><dt>证据</dt><dd>{{ evidence.length }}</dd></div>
          <div><dt>有效</dt><dd>{{ effectiveEvidenceCount }}</dd></div>
          <div><dt>版本</dt><dd>V{{ report.version }}</dd></div>
          <div><dt>生成</dt><dd>{{ formatShortDate(report.createdAt) }}</dd></div>
        </dl>
      </section>

      <section class="signal-band">
        <div class="section-kicker">
          <div>
            <span>MARKET TAPE</span>
            <h2>行情与净值轨迹</h2>
          </div>
          <p v-if="etfDeepData?.navDate">净值日 {{ etfDeepData.navDate }} · 折溢价 {{ formatPercent(etfDeepData.premiumDiscountRate) }}</p>
          <p v-else>行情来自本次冻结快照，不请求实时数据</p>
        </div>
        <MarketSeriesChart
          :series="marketSeries"
          :symbol="subject.fullCode"
          :unit-nav="etfDeepData?.unitNav"
        />
        <dl v-if="etfDeepData" class="etf-ledger">
          <div><dt>基金简称</dt><dd>{{ etfDeepData.fundName || '—' }}</dd></div>
          <div><dt>管理人</dt><dd>{{ etfDeepData.management || '—' }}</dd></div>
          <div><dt>单位净值</dt><dd>{{ formatNumber(etfDeepData.unitNav, 4) }}</dd></div>
          <div><dt>资产净值</dt><dd>{{ formatCompact(etfDeepData.totalNetAsset) }}</dd></div>
          <div class="benchmark"><dt>业绩比较基准</dt><dd>{{ etfDeepData.benchmark || '数据缺失' }}</dd></div>
        </dl>
      </section>

      <section v-if="bullBearResearch" class="debate-ledger" aria-labelledby="debate-title">
        <div class="section-kicker debate-heading">
          <div>
            <span>EVIDENCE-BOUND AGENTS</span>
            <h2 id="debate-title">多空研究 Agent</h2>
          </div>
          <p>{{ bullBearResearch.synthesis }}</p>
        </div>
        <div class="debate-columns">
          <article class="debate-side bull-side">
            <header><TrendingUpIcon :size="18" aria-hidden="true" /><b>多头角色</b><small>BULL CASE</small></header>
            <ol>
              <li v-for="(claim, index) in bullBearResearch.bullCases" :key="`bull-${index}`">
                <strong>{{ claim.title }}</strong>
                <p>{{ claim.statement }}</p>
                <div class="claim-meta">
                  <span>{{ claim.strength }}</span>
                  <a v-for="ref in claim.evidenceRefs" :key="ref" :href="`#evidence-${ref.slice(1)}`">[{{ ref }}]</a>
                </div>
              </li>
            </ol>
          </article>
          <article class="debate-side bear-side">
            <header><TrendingDownIcon :size="18" aria-hidden="true" /><b>空头角色</b><small>BEAR CASE</small></header>
            <ol>
              <li v-for="(claim, index) in bullBearResearch.bearCases" :key="`bear-${index}`">
                <strong>{{ claim.title }}</strong>
                <p>{{ claim.statement }}</p>
                <div class="claim-meta">
                  <span>{{ claim.strength }}</span>
                  <a v-for="ref in claim.evidenceRefs" :key="ref" :href="`#evidence-${ref.slice(1)}`">[{{ ref }}]</a>
                </div>
              </li>
            </ol>
          </article>
        </div>
      </section>

      <div class="archive-grid">
        <aside class="version-rail" aria-label="报告版本">
          <div class="rail-heading">
            <span>VERSION TAPE</span>
            <h2>报告版本</h2>
          </div>
          <nav>
            <button
              v-for="version in versions"
              :key="version.id"
              type="button"
              :class="['version-ticket', { active: version.id === report.id }]"
              @click="openVersion(version.id)"
            >
              <span>V{{ version.version }}</span>
              <b>{{ version.reviewStatus }}</b>
              <small>{{ formatShortDate(version.createdAt) }}</small>
            </button>
          </nav>
          <label class="compare-select">
            <span>对比基线</span>
            <select v-model="compareReportId">
              <option value="">选择其他版本</option>
              <option v-for="version in comparableVersions" :key="version.id" :value="String(version.id)">
                V{{ version.version }} · {{ formatShortDate(version.createdAt) }}
              </option>
            </select>
          </label>
          <button class="compare-toggle" type="button" :disabled="!compareReportId" @click="showComparison = !showComparison">
            <GitCompareArrowsIcon :size="15" aria-hidden="true" />
            {{ showComparison ? '返回正文' : '版本对比' }}
          </button>
        </aside>

        <section class="paper-column">
          <header class="paper-toolbar">
            <div>
              <span>{{ showComparison ? 'LINE-BY-LINE REVIEW' : 'APPROVED NARRATIVE' }}</span>
              <h2>{{ showComparison ? `V${report.version} 与 ${comparisonLabel}` : '报告正文' }}</h2>
            </div>
            <div v-if="showComparison" class="diff-stats">
              <span class="removed">−{{ diffStats.removed }}</span>
              <span class="added">+{{ diffStats.added }}</span>
            </div>
          </header>
          <article v-if="!showComparison" class="archive-paper report-content" v-html="renderedReport"></article>
          <div v-else class="diff-view" role="table" aria-label="报告版本逐行对比">
            <div class="diff-head" role="row">
              <span>当前 V{{ report.version }}</span>
              <span>{{ comparisonLabel }}</span>
            </div>
            <div v-for="(row, index) in diffRows" :key="index" :class="['diff-row', row.kind]" role="row">
              <pre>{{ row.left || ' ' }}</pre>
              <pre>{{ row.right || ' ' }}</pre>
            </div>
          </div>
        </section>

        <aside class="evidence-rail" aria-labelledby="evidence-title">
          <div class="rail-heading">
            <span>SOURCE LEDGER</span>
            <h2 id="evidence-title">证据详情</h2>
          </div>
          <label class="evidence-search">
            <SearchIcon :size="14" aria-hidden="true" />
            <input v-model="evidenceKeyword" type="search" placeholder="来源、指标或摘要" />
          </label>
          <div class="source-filters" aria-label="证据来源筛选">
            <button type="button" :class="{ active: activeSource === '' }" @click="activeSource = ''">全部</button>
            <button
              v-for="source in evidenceSources"
              :key="source"
              type="button"
              :class="{ active: activeSource === source }"
              @click="activeSource = source"
            >{{ sourceLabel(source) }}</button>
          </div>
          <ol class="evidence-list">
            <li
              v-for="entry in filteredEvidence"
              :id="`evidence-${entry.index}`"
              :key="entry.index"
              :class="{ invalid: entry.item.issueCode }"
            >
              <div class="evidence-id">
                <b>E{{ entry.index }}</b>
                <span>{{ entry.item.issueCode || 'VERIFIED' }}</span>
              </div>
              <strong>{{ entry.item.metricName || '证据条目' }}</strong>
              <p>{{ entry.item.excerpt || '无摘要' }}</p>
              <dl>
                <div><dt>来源</dt><dd>{{ entry.item.sourceName || '—' }}</dd></div>
                <div><dt>期间</dt><dd>{{ entry.item.reportPeriod || '—' }}</dd></div>
                <div><dt>置信</dt><dd>{{ formatConfidence(entry.item.confidence) }}</dd></div>
              </dl>
              <a v-if="entry.item.url" :href="entry.item.url" target="_blank" rel="noopener noreferrer">
                查看原始来源 <ExternalLinkIcon :size="12" aria-hidden="true" />
              </a>
            </li>
          </ol>
          <p v-if="filteredEvidence.length === 0" class="empty-evidence">没有匹配的证据。</p>
        </aside>
      </div>
    </main>
  </div>
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { RouterLink, useRoute, useRouter } from 'vue-router';
import {
  ArrowLeftIcon,
  ExternalLinkIcon,
  GitCompareArrowsIcon,
  LoaderCircleIcon,
  SearchIcon,
  ShieldAlertIcon,
  TrendingDownIcon,
  TrendingUpIcon
} from 'lucide-vue-next';
import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';
import MarketSeriesChart from '../components/MarketSeriesChart.vue';
import {
  getCurrentUser,
  getReport,
  getStockReplay,
  getTaskLogs,
  getThreadReports
} from '../services/api';

const route = useRoute();
const router = useRouter();
const report = ref(null);
const versions = ref([]);
const replay = ref(null);
const taskLogs = ref([]);
const isLoading = ref(true);
const error = ref('');
const compareReportId = ref('');
const showComparison = ref(false);
const evidenceKeyword = ref('');
const activeSource = ref('');

const md = new MarkdownIt({ html: false, linkify: true, typographer: true });
md.use(mk);
const defaultLinkOpen = md.renderer.rules.link_open || ((tokens, idx, options, env, self) => self.renderToken(tokens, idx, options));
md.renderer.rules.link_open = (tokens, idx, options, env, self) => {
  const href = tokens[idx].attrGet('href') || '';
  if (!href.startsWith('#')) {
    tokens[idx].attrSet('target', '_blank');
    tokens[idx].attrSet('rel', 'noopener noreferrer');
  }
  return defaultLinkOpen(tokens, idx, options, env, self);
};

const safeJson = (value, fallback = null) => {
  if (value && typeof value === 'object') return value;
  if (typeof value !== 'string' || !value.trim()) return fallback;
  try { return JSON.parse(value); } catch { return fallback; }
};

const snapshot = computed(() => safeJson(replay.value?.snapshotJson, {}) || {});
const subject = computed(() => snapshot.value.subject || {});
const marketSeries = computed(() => snapshot.value.marketSeries || []);
const etfDeepData = computed(() => snapshot.value.etfDeepData || null);
const assetLabel = computed(() => subject.value.assetType === 'ETF' ? 'ETF 深度研究' : 'A 股公司研究');
const evidence = computed(() => (replay.value?.evidenceJson || []).map((item) => safeJson(item, {})));
const effectiveEvidenceCount = computed(() => evidence.value.filter((item) => !item.issueCode).length);
const evidenceSources = computed(() => [...new Set(evidence.value.map((item) => item.sourceType || 'UNKNOWN'))]);
const comparableVersions = computed(() => versions.value.filter((item) => item.id !== report.value?.id));
const comparisonReport = computed(() => versions.value.find((item) => String(item.id) === compareReportId.value) || null);
const comparisonLabel = computed(() => comparisonReport.value ? `V${comparisonReport.value.version}` : '基线');

const bullBearResearch = computed(() => {
  const log = [...taskLogs.value].reverse().find((item) => String(item.stepName).toLowerCase() === 'bull_bear_research');
  const output = safeJson(log?.outputSnapshot, {});
  return output?.research || output?.data?.research || null;
});

const renderedReport = computed(() => {
  const raw = (report.value?.content || '')
    .replace(/<!--\s*FinSight\s+(?:generation-mode|fallback-reason):.*?-->\s*/g, '');
  const linkedEvidence = raw.replace(/\[E(\d+)\]/g, '[E$1](#evidence-$1)');
  return md.render(linkedEvidence);
});

const filteredEvidence = computed(() => {
  const keyword = evidenceKeyword.value.trim().toLowerCase();
  return evidence.value
    .map((item, index) => ({ item, index: index + 1 }))
    .filter(({ item }) => !activeSource.value || (item.sourceType || 'UNKNOWN') === activeSource.value)
    .filter(({ item }) => !keyword || [item.sourceName, item.metricName, item.excerpt, item.reportPeriod]
      .some((value) => String(value || '').toLowerCase().includes(keyword)));
});

const buildDiff = (leftText, rightText) => {
  const left = String(leftText || '').split('\n').slice(0, 500);
  const right = String(rightText || '').split('\n').slice(0, 500);
  const table = Array.from({ length: left.length + 1 }, () => new Uint16Array(right.length + 1));
  for (let i = 1; i <= left.length; i += 1) {
    for (let j = 1; j <= right.length; j += 1) {
      table[i][j] = left[i - 1] === right[j - 1]
        ? table[i - 1][j - 1] + 1
        : Math.max(table[i - 1][j], table[i][j - 1]);
    }
  }
  const rows = [];
  let i = left.length;
  let j = right.length;
  while (i > 0 || j > 0) {
    if (i > 0 && j > 0 && left[i - 1] === right[j - 1]) {
      rows.push({ left: left[i - 1], right: right[j - 1], kind: 'same' }); i -= 1; j -= 1;
    } else if (j > 0 && (i === 0 || table[i][j - 1] >= table[i - 1][j])) {
      rows.push({ left: '', right: right[j - 1], kind: 'added' }); j -= 1;
    } else {
      rows.push({ left: left[i - 1], right: '', kind: 'removed' }); i -= 1;
    }
  }
  return rows.reverse();
};

const diffRows = computed(() => buildDiff(report.value?.content, comparisonReport.value?.content));
const diffStats = computed(() => diffRows.value.reduce((result, row) => {
  if (row.kind === 'added') result.added += 1;
  if (row.kind === 'removed') result.removed += 1;
  return result;
}, { added: 0, removed: 0 }));

const loadReportArchive = async () => {
  isLoading.value = true;
  error.value = '';
  compareReportId.value = '';
  showComparison.value = false;
  try {
    await getCurrentUser();
    report.value = await getReport(route.params.reportId);
    versions.value = await getThreadReports(report.value.threadId);
    if (report.value.taskId) {
      [replay.value, taskLogs.value] = await Promise.all([
        getStockReplay(report.value.taskId),
        getTaskLogs(report.value.taskId)
      ]);
    } else {
      replay.value = null;
      taskLogs.value = [];
    }
  } catch (reason) {
    error.value = reason?.message || '无法读取报告，请确认登录状态和报告权限。';
  } finally {
    isLoading.value = false;
  }
};

const openVersion = (reportId) => {
  if (Number(reportId) !== Number(report.value?.id)) router.push(`/reports/${reportId}`);
};

const formatShortDate = (value) => {
  if (!value) return '—';
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? String(value).slice(0, 10) : date.toLocaleDateString('zh-CN');
};
const formatNumber = (value, digits = 2) => value == null ? '—' : Number(value).toFixed(digits).replace(/\.0+$/, '');
const formatCompact = (value) => {
  if (value == null) return '—';
  return new Intl.NumberFormat('zh-CN', { notation: 'compact', maximumFractionDigits: 2 }).format(Number(value));
};
const formatPercent = (value) => value == null ? '—' : `${Number(value).toFixed(2)}%`;
const formatConfidence = (value) => value == null ? '—' : `${Math.round(Number(value) * 100)}%`;
const sourceLabel = (value) => ({
  AUTHORIZED_MARKET: '行情',
  UPLOADED_REPORT: '上传资料',
  PUBLIC_MARKET: '公开网页',
  DATA_PROVIDER: '数据源'
}[value] || value);

watch(() => route.params.reportId, loadReportArchive);
watch(compareReportId, (value) => { if (!value) showComparison.value = false; });
onMounted(loadReportArchive);
</script>

<style scoped>
.archive-page {
  min-height: 100vh;
  color: #18313a;
  background:
    linear-gradient(90deg, rgba(20, 44, 54, 0.035) 1px, transparent 1px),
    linear-gradient(rgba(20, 44, 54, 0.035) 1px, transparent 1px),
    #eef2f1;
  background-size: 36px 36px;
}
.archive-header { position: sticky; top: 0; z-index: 20; color: #edf5f4; background: #091a22; border-top: 2px solid #b28b49; box-shadow: 0 10px 30px rgba(9, 26, 34, .2); }
.archive-header-inner { max-width: 1580px; min-height: 58px; margin: auto; padding: 0 24px; display: grid; grid-template-columns: 1fr auto 1fr; align-items: center; }
.back-link { color: #b8c9cc; text-decoration: none; display: inline-flex; align-items: center; gap: 8px; font-size: 12px; letter-spacing: .04em; }
.back-link:hover { color: white; }
.archive-brand { display: flex; align-items: center; gap: 10px; }
.brand-seal { display: grid; place-content: center; width: 29px; height: 29px; border: 1px solid #b28b49; color: #dec79d; font-family: ui-serif, Georgia, serif; font-size: 11px; }
.archive-brand b, .archive-brand small { display: block; }
.archive-brand b { font-family: ui-serif, Georgia, serif; font-size: 15px; letter-spacing: .05em; }
.archive-brand small { color: #78919a; font-size: 8px; letter-spacing: .2em; }
.header-status { justify-self: end; display: flex; align-items: center; gap: 7px; color: #b8c9cc; font: 600 11px ui-monospace, monospace; letter-spacing: .08em; }
.status-dot { width: 7px; height: 7px; border-radius: 50%; background: #c15b49; box-shadow: 0 0 0 4px rgba(193, 91, 73, .12); }
.status-dot.is-pass { background: #64a996; box-shadow: 0 0 0 4px rgba(100, 169, 150, .12); }
.archive-main { max-width: 1580px; margin: auto; padding: 28px 24px 64px; }
.identity-strip { display: flex; align-items: end; justify-content: space-between; gap: 36px; padding: 14px 0 22px; border-bottom: 1px solid #9aadb2; }
.eyebrow, .section-kicker span, .rail-heading span, .paper-toolbar span { margin: 0 0 8px; color: #7a6640; font: 700 10px ui-monospace, monospace; letter-spacing: .15em; }
.identity-primary h1 { margin: 0; color: #102831; font: 500 clamp(34px, 5vw, 66px)/.95 ui-serif, Georgia, serif; letter-spacing: -.03em; }
.identity-primary > p:last-child { margin: 10px 0 0; color: #687b81; font-size: 14px; }
.identity-metrics { min-width: 420px; margin: 0; display: grid; grid-template-columns: repeat(4, 1fr); }
.identity-metrics div { padding: 0 18px; border-left: 1px solid #b9c6c9; }
.identity-metrics dt { color: #75878c; font-size: 10px; letter-spacing: .08em; }
.identity-metrics dd { margin: 6px 0 0; color: #18313a; font: 600 18px ui-monospace, monospace; }
.signal-band, .debate-ledger { margin-top: 24px; padding: 20px 22px; background: rgba(249, 251, 250, .92); border: 1px solid #bcc9cc; box-shadow: 0 12px 36px rgba(9, 26, 34, .05); }
.section-kicker { display: flex; align-items: end; justify-content: space-between; gap: 20px; padding-bottom: 12px; border-bottom: 1px solid #d2dcde; }
.section-kicker h2, .rail-heading h2, .paper-toolbar h2 { margin: 0; color: #18313a; font: 500 20px ui-serif, Georgia, serif; }
.section-kicker p { max-width: 620px; margin: 0; color: #718287; font-size: 11px; text-align: right; }
.etf-ledger { margin: 4px 0 0; padding-top: 14px; display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)) 2fr; gap: 1px; background: #cad4d6; border: 1px solid #cad4d6; }
.etf-ledger div { min-width: 0; padding: 10px 12px; background: #f7f9f8; }
.etf-ledger dt { color: #7b8b90; font-size: 9px; letter-spacing: .08em; }
.etf-ledger dd { margin: 5px 0 0; overflow: hidden; color: #233d46; font-size: 12px; font-weight: 650; text-overflow: ellipsis; white-space: nowrap; }
.debate-heading { align-items: center; }
.debate-columns { display: grid; grid-template-columns: 1fr 1fr; gap: 1px; margin-top: 16px; background: #c7d2d4; border: 1px solid #c7d2d4; }
.debate-side { padding: 18px; background: #f8faf9; }
.debate-side header { display: grid; grid-template-columns: auto 1fr auto; align-items: center; gap: 8px; padding-bottom: 12px; border-bottom: 2px solid #c15b49; }
.bear-side header { border-color: #2f7d6d; }
.debate-side header b { font-family: ui-serif, Georgia, serif; }
.debate-side header small { color: #7c8c91; font: 700 9px ui-monospace, monospace; letter-spacing: .14em; }
.debate-side ol { margin: 0; padding: 0; list-style: none; }
.debate-side li { padding: 13px 0 10px; border-bottom: 1px solid #dbe2e3; }
.debate-side strong { color: #29434c; font-size: 12px; }
.debate-side p { margin: 5px 0 8px; color: #566b72; font-size: 12px; line-height: 1.65; }
.claim-meta { display: flex; gap: 6px; }
.claim-meta span, .claim-meta a { color: #826d43; font: 700 9px ui-monospace, monospace; text-decoration: none; }
.archive-grid { display: grid; grid-template-columns: 190px minmax(0, 1fr) 320px; gap: 18px; align-items: start; margin-top: 24px; }
.version-rail, .evidence-rail, .paper-column { min-width: 0; background: rgba(249, 251, 250, .94); border: 1px solid #bcc9cc; }
.version-rail, .evidence-rail { position: sticky; top: 82px; max-height: calc(100vh - 104px); overflow: auto; }
.rail-heading, .paper-toolbar { padding: 16px; border-bottom: 1px solid #cdd7d9; }
.rail-heading h2 { font-size: 17px; }
.version-rail nav { padding: 8px; }
.version-ticket { width: 100%; padding: 11px 10px; display: grid; grid-template-columns: auto 1fr; gap: 4px 8px; text-align: left; border: 0; border-left: 2px solid transparent; background: transparent; color: #687c82; cursor: pointer; }
.version-ticket:hover { background: #edf3f3; }
.version-ticket.active { border-left-color: #b28b49; color: #17313a; background: #edf3f3; }
.version-ticket span { font: 700 14px ui-monospace, monospace; }
.version-ticket b { justify-self: end; color: #4d756b; font-size: 9px; letter-spacing: .06em; }
.version-ticket small { grid-column: 1 / -1; font-size: 10px; }
.compare-select { display: block; margin: 8px 12px; padding-top: 12px; border-top: 1px solid #d1dadd; }
.compare-select span { display: block; margin-bottom: 6px; color: #71858a; font-size: 10px; }
.compare-select select { width: 100%; min-height: 34px; border: 1px solid #bdcacc; background: #fff; color: #29434c; font-size: 11px; }
.compare-toggle { width: calc(100% - 24px); min-height: 35px; margin: 6px 12px 14px; display: flex; align-items: center; justify-content: center; gap: 7px; border: 1px solid #6f8a91; background: #18313a; color: #eff5f4; font-size: 11px; cursor: pointer; }
.compare-toggle:disabled { opacity: .38; cursor: not-allowed; }
.paper-toolbar { display: flex; align-items: center; justify-content: space-between; background: linear-gradient(90deg, rgba(178, 139, 73, .08), transparent 42%); }
.paper-toolbar h2 { font-size: 21px; }
.diff-stats { display: flex; gap: 8px; font: 700 11px ui-monospace, monospace; }
.diff-stats .removed { color: #a24c40; }.diff-stats .added { color: #287363; }
.archive-paper { min-height: 720px; padding: clamp(28px, 5vw, 64px); color: #2c4148; background: linear-gradient(90deg, rgba(178, 139, 73, .045), transparent 6rem), #fff; }
.archive-paper :deep(h1) { margin: 0 0 24px; color: #102831; font: 500 32px/1.2 ui-serif, Georgia, serif; }
.archive-paper :deep(h2) { margin: 36px 0 14px; padding-bottom: 8px; color: #17313a; font: 600 20px ui-serif, Georgia, serif; border-bottom: 1px solid #cdd8da; }
.archive-paper :deep(h3) { margin-top: 26px; color: #785f34; font: 600 16px ui-serif, Georgia, serif; }
.archive-paper :deep(p), .archive-paper :deep(li) { font-size: 14px; line-height: 1.9; }
.archive-paper :deep(blockquote) { margin: 18px 0; padding: 10px 16px; color: #5c6f75; border-left: 2px solid #b28b49; background: #f5f7f6; }
.archive-paper :deep(a) { color: #2e6f7c; text-decoration-color: #a9c3c8; text-underline-offset: 3px; }
.diff-view { max-height: 76vh; overflow: auto; background: #fff; }
.diff-head, .diff-row { display: grid; grid-template-columns: 1fr 1fr; }
.diff-head { position: sticky; top: 0; z-index: 2; color: #dce8e7; background: #18313a; font: 700 10px ui-monospace, monospace; letter-spacing: .08em; }
.diff-head span, .diff-row pre { min-width: 0; margin: 0; padding: 8px 12px; border-right: 1px solid #d8e0e2; }
.diff-row pre { overflow-wrap: anywhere; color: #4b6067; font: 11px/1.55 ui-monospace, monospace; white-space: pre-wrap; }
.diff-row.removed pre:first-child { background: #fff0ed; color: #914439; }.diff-row.removed pre:last-child { background: #fafafa; }
.diff-row.added pre:last-child { background: #eaf6f2; color: #246756; }.diff-row.added pre:first-child { background: #fafafa; }
.evidence-search { margin: 12px; padding: 0 10px; min-height: 36px; display: flex; align-items: center; gap: 7px; border: 1px solid #c3cfd1; background: white; }
.evidence-search input { min-width: 0; width: 100%; border: 0; outline: 0; color: #314a52; font-size: 11px; }
.source-filters { display: flex; flex-wrap: wrap; gap: 5px; padding: 0 12px 10px; }
.source-filters button { padding: 4px 7px; border: 1px solid #c7d1d3; background: transparent; color: #687b81; font-size: 9px; cursor: pointer; }
.source-filters button.active { border-color: #826d43; color: #6d572f; background: #f5eddf; }
.evidence-list { margin: 0; padding: 0 12px 16px; list-style: none; }
.evidence-list li { scroll-margin-top: 90px; margin-top: 8px; padding: 12px; background: #fff; border: 1px solid #d2dcde; border-top: 2px solid #6f9299; }
.evidence-list li:target { outline: 2px solid rgba(178, 139, 73, .55); outline-offset: 2px; }
.evidence-list li.invalid { border-top-color: #c15b49; }
.evidence-id { display: flex; align-items: center; justify-content: space-between; }
.evidence-id b { color: #8a6d38; font: 700 11px ui-monospace, monospace; }.evidence-id span { color: #648078; font: 700 8px ui-monospace, monospace; }
.evidence-list > li > strong { display: block; margin-top: 9px; color: #243e47; font-size: 11px; }
.evidence-list p { margin: 5px 0 10px; color: #607279; font-size: 11px; line-height: 1.55; }
.evidence-list dl { margin: 0; padding-top: 7px; border-top: 1px solid #e0e6e7; }
.evidence-list dl div { display: grid; grid-template-columns: 42px 1fr; gap: 6px; margin-top: 4px; font-size: 9px; }
.evidence-list dt { color: #89979a; }.evidence-list dd { margin: 0; color: #4f646b; overflow-wrap: anywhere; }
.evidence-list a { margin-top: 10px; display: inline-flex; align-items: center; gap: 5px; color: #346f7b; font-size: 9px; text-decoration: none; }
.empty-evidence { padding: 24px; color: #7d8e92; font-size: 11px; text-align: center; }
.state-panel { min-height: 70vh; display: grid; place-content: center; justify-items: center; color: #60757b; text-align: center; }
.state-panel h1 { margin: 12px 0 4px; color: #243d46; font-family: ui-serif, Georgia, serif; }.state-panel p { max-width: 480px; }
.state-panel.is-error svg { color: #b65343; }.primary-link { margin-top: 10px; padding: 9px 14px; color: white; background: #18313a; text-decoration: none; font-size: 12px; }
.spin { animation: spin 1s linear infinite; } @keyframes spin { to { transform: rotate(360deg); } }
@media (max-width: 1180px) {
  .archive-grid { grid-template-columns: 170px minmax(0, 1fr); }
  .evidence-rail { position: static; grid-column: 1 / -1; max-height: none; }
  .evidence-list { display: grid; grid-template-columns: repeat(3, 1fr); gap: 8px; }
  .evidence-list li { margin: 0; }
}
@media (max-width: 820px) {
  .archive-header-inner { grid-template-columns: 1fr auto; padding: 0 14px; }.archive-brand { display: none; }
  .archive-main { padding: 18px 12px 42px; }.identity-strip { align-items: start; flex-direction: column; }
  .identity-metrics { width: 100%; min-width: 0; }.identity-metrics div { padding: 0 10px; }
  .section-kicker { align-items: start; flex-direction: column; }.section-kicker p { text-align: left; }
  .etf-ledger { grid-template-columns: 1fr 1fr; }.etf-ledger .benchmark { grid-column: 1 / -1; }
  .debate-columns { grid-template-columns: 1fr; }.archive-grid { grid-template-columns: 1fr; }
  .version-rail, .evidence-rail { position: static; max-height: none; }.version-rail nav { display: flex; overflow-x: auto; }
  .version-ticket { min-width: 130px; }.evidence-list { grid-template-columns: 1fr; }
  .archive-paper { padding: 24px 18px; }.diff-row pre { font-size: 9px; padding: 7px; }
}
@media (prefers-reduced-motion: reduce) { .spin { animation: none; } }
</style>
