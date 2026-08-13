<template>
  <transition name="slide-down">
    <div
      v-if="showWarning"
      role="status"
      aria-live="polite"
      class="fixed left-1/2 top-20 z-50 flex w-[calc(100%-2rem)] max-w-xl -translate-x-1/2 items-start gap-3 rounded-lg border border-amber-200 bg-amber-50 px-4 py-3 text-amber-900 shadow-lg"
    >
      <AlertTriangleIcon class="mt-0.5 h-5 w-5 shrink-0 text-amber-600" aria-hidden="true" />
      <span class="min-w-0 flex-1 text-sm font-medium leading-6">{{ warningMessage }}</span>
      <button
        type="button"
        class="rounded-md p-1 text-amber-700 transition hover:bg-amber-100 hover:text-amber-900 focus:outline-none focus:ring-2 focus:ring-amber-500 focus:ring-offset-2"
        aria-label="关闭提示"
        @click="showWarning = false"
      >
        <XIcon class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  </transition>

  <div class="app-shell min-h-screen text-slate-950 selection:bg-blue-100 selection:text-blue-950">
    <header class="app-header sticky top-0 z-40 border-b border-slate-800/80 shadow-sm shadow-slate-950/20 backdrop-blur">
      <div class="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
        <div class="flex items-center gap-3">
          <div class="brand-mark h-11 w-11 shrink-0 rounded-lg shadow-sm shadow-slate-950/30">
            <img src="/finsight.svg" alt="" class="block h-full w-full" aria-hidden="true" />
          </div>
          <div>
            <p class="font-mono text-[11px] font-semibold uppercase tracking-[0.16em] text-amber-200/90">A-share intelligence desk</p>
            <h1 class="font-display text-xl font-semibold tracking-tight text-white sm:text-2xl">
              FinSight 深度研究助手
            </h1>
          </div>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
          <nav v-if="authUser" class="workspace-nav flex rounded-lg border border-slate-700 bg-slate-900/80 p-1" aria-label="工作区">
            <button
              v-for="tab in workspaceTabs"
              :key="tab.id"
              type="button"
              class="flex min-h-9 items-center gap-2 rounded-md px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-300 focus:ring-offset-2 focus:ring-offset-slate-950"
              :class="activeWorkspace === tab.id ? 'bg-amber-200 text-slate-950 shadow-sm' : 'text-slate-300 hover:bg-slate-800 hover:text-white'"
              @click="setWorkspace(tab.id)"
            >
              <component :is="tab.icon" class="h-4 w-4" aria-hidden="true" />
              <span>{{ tab.label }}</span>
            </button>
          </nav>

          <div v-if="authUser" class="flex items-center gap-2">
            <span class="rounded-md bg-slate-800 px-2.5 py-1 text-xs font-semibold text-blue-100">{{ authUser.username }}</span>
            <button type="button" class="rounded-md border border-slate-700 bg-slate-900 px-2.5 py-1 text-xs font-semibold text-slate-300 transition hover:bg-slate-800 hover:text-white" @click="logout">
              退出
            </button>
          </div>
        </div>
      </div>
    </header>

    <main v-if="!authUser" class="auth-stage mx-auto grid min-h-[calc(100vh-5rem)] max-w-7xl grid-cols-1 items-center gap-8 px-4 py-12 sm:px-6 lg:grid-cols-[1.1fr_28rem] lg:px-8">
      <section class="max-w-2xl">
        <p class="text-sm font-semibold text-blue-700">FinSight 金融投研 Agent</p>
        <h2 class="mt-3 font-display text-4xl font-semibold leading-tight text-slate-950 sm:text-5xl">
          从证券代码到可信报告，收束到一条金融研究轨迹里。
        </h2>
        <p class="mt-5 max-w-xl text-base leading-7 text-slate-600">
          输入 A股或 ETF 代码，FinSight 会完成数据快照、指标计算、证据检索、报告撰写与质量门控。
        </p>
        <div class="mt-8 grid max-w-xl grid-cols-1 gap-3 sm:grid-cols-3">
          <div class="rounded-lg border border-slate-200 bg-white/80 p-4 shadow-sm">
            <p class="font-mono text-xs font-semibold text-blue-700">01</p>
            <p class="mt-2 text-sm font-semibold text-slate-950">证据输入</p>
            <p class="mt-1 text-xs leading-5 text-slate-500">PDF 与联网资料统一进入任务上下文。</p>
          </div>
          <div class="rounded-lg border border-slate-200 bg-white/80 p-4 shadow-sm">
            <p class="font-mono text-xs font-semibold text-blue-700">02</p>
            <p class="mt-2 text-sm font-semibold text-slate-950">节点执行</p>
            <p class="mt-1 text-xs leading-5 text-slate-500">每一步都有状态、日志和回放线索。</p>
          </div>
          <div class="rounded-lg border border-slate-200 bg-white/80 p-4 shadow-sm">
            <p class="font-mono text-xs font-semibold text-blue-700">03</p>
            <p class="mt-2 text-sm font-semibold text-slate-950">报告沉淀</p>
            <p class="mt-1 text-xs leading-5 text-slate-500">报告、收藏、导出和证据回放保留在同一工作区。</p>
          </div>
        </div>
      </section>

      <section class="auth-card w-full overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm shadow-slate-200/70">
        <div class="h-1 bg-gradient-to-r from-amber-500 via-amber-300 to-blue-700"></div>
        <div class="p-6">
          <div class="mb-6">
            <h2 class="text-lg font-semibold text-blue-950">{{ authMode === 'login' ? '登录工作区' : '创建账号' }}</h2>
            <p class="mt-1 text-sm text-slate-500">登录后隔离研究任务、报告版本和管理权限。</p>
          </div>

          <div class="space-y-4">
            <label class="block">
              <span class="text-sm font-medium text-slate-700">用户名</span>
              <input v-model="authForm.username" type="text" class="mt-1 min-h-10 w-full rounded-lg border border-blue-100 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" />
            </label>
            <label v-if="authMode === 'register'" class="block">
              <span class="text-sm font-medium text-slate-700">邮箱</span>
              <input v-model="authForm.email" type="email" class="mt-1 min-h-10 w-full rounded-lg border border-blue-100 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" />
            </label>
            <label class="block">
              <span class="text-sm font-medium text-slate-700">密码</span>
              <input v-model="authForm.password" type="password" class="mt-1 min-h-10 w-full rounded-lg border border-blue-100 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" />
            </label>
          </div>

          <p v-if="authError" class="mt-4 rounded-lg bg-rose-50 px-3 py-2 text-sm text-rose-700">{{ authError }}</p>

          <button type="button" class="mt-5 flex min-h-11 w-full items-center justify-center rounded-lg bg-blue-700 px-4 text-sm font-semibold text-white transition hover:bg-blue-800 disabled:bg-slate-300" :disabled="isAuthLoading" @click="submitAuth">
            {{ isAuthLoading ? '处理中' : (authMode === 'login' ? '登录' : '注册') }}
          </button>

          <button type="button" class="mt-4 w-full text-center text-sm font-semibold text-blue-700 hover:text-blue-900" @click="authMode = authMode === 'login' ? 'register' : 'login'">
            {{ authMode === 'login' ? '创建新账号' : '已有账号，去登录' }}
          </button>
        </div>
      </section>
    </main>

    <KeepAlive v-else>
      <ResearchWorkspace
        v-if="activeWorkspace === 'run'"
        :thread-id="activeThreadId"
        @completed="handleResearchCompleted"
        @warning="triggerWarning"
      />
      <TaskWorkspace
        v-else-if="activeWorkspace === 'tasks'"
        :refresh-revision="dataRevision"
        @thread-change="setActiveThread"
      />
      <ReportLibraryWorkspace
        v-else-if="activeWorkspace === 'reports'"
        :thread-id="activeThreadId"
        :refresh-revision="dataRevision"
        @thread-change="setActiveThread"
        @warning="triggerWarning"
      />
      <AdminWorkspace
        v-else-if="activeWorkspace === 'admin'"
        @warning="triggerWarning"
      />
      <SettingsWorkspace v-else />
    </KeepAlive>
  </div>
</template>

<script setup>
import { computed, onMounted, ref } from 'vue';
import {
  AlertTriangleIcon,
  ClipboardListIcon,
  HistoryIcon,
  SendIcon,
  SettingsIcon,
  ShieldCheckIcon,
  XIcon
} from 'lucide-vue-next';
import AdminWorkspace from './views/AdminWorkspace.vue';
import ReportLibraryWorkspace from './views/ReportLibraryWorkspace.vue';
import ResearchWorkspace from './views/ResearchWorkspace.vue';
import SettingsWorkspace from './views/SettingsWorkspace.vue';
import TaskWorkspace from './views/TaskWorkspace.vue';
import {
  currentThreadId,
  getCurrentUser,
  login as authLogin,
  register as authRegister,
  setAuthToken
} from './services/api';

const showWarning = ref(false);
const warningMessage = ref('');
const activeWorkspace = ref('run');
const activeThreadId = ref(currentThreadId);
const dataRevision = ref(0);

const authUser = ref(null);
const authMode = ref('login');
const authForm = ref({ username: '', email: '', password: '' });
const authError = ref('');
const isAuthLoading = ref(false);

let warningTimer;

const workspaceTabs = computed(() => {
  const tabs = [
    { id: 'run', label: '运行', icon: SendIcon },
    { id: 'tasks', label: '任务', icon: ClipboardListIcon },
    { id: 'reports', label: '报告', icon: HistoryIcon },
    { id: 'settings', label: '设置', icon: SettingsIcon }
  ];
  if (authUser.value?.role === 'ADMIN') {
    tabs.splice(3, 0, { id: 'admin', label: '管理', icon: ShieldCheckIcon });
  }
  return tabs;
});

const triggerWarning = (message) => {
  warningMessage.value = message;
  showWarning.value = true;
  window.clearTimeout(warningTimer);
  warningTimer = window.setTimeout(() => {
    showWarning.value = false;
  }, 5000);
};

const setWorkspace = (workspace) => {
  if (workspace === 'admin' && authUser.value?.role !== 'ADMIN') return;
  activeWorkspace.value = workspace;
};

const setActiveThread = (threadId) => {
  activeThreadId.value = threadId || currentThreadId;
};

const handleResearchCompleted = () => {
  dataRevision.value += 1;
};

const submitAuth = async () => {
  authError.value = '';
  isAuthLoading.value = true;
  try {
    authUser.value = authMode.value === 'login'
      ? await authLogin(authForm.value.username, authForm.value.password)
      : await authRegister(
        authForm.value.username,
        authForm.value.email,
        authForm.value.password
      );
  } catch (error) {
    authError.value = error.message;
  } finally {
    isAuthLoading.value = false;
  }
};

const logout = () => {
  setAuthToken('');
  authUser.value = null;
  activeWorkspace.value = 'run';
  activeThreadId.value = currentThreadId;
  dataRevision.value = 0;
};

onMounted(async () => {
  try {
    authUser.value = await getCurrentUser();
  } catch {
    setAuthToken('');
    authUser.value = null;
  }
});
</script>

<style>
.slide-down-enter-active,
.slide-down-leave-active {
  transition: opacity 180ms ease, transform 180ms ease;
}

.slide-down-enter-from,
.slide-down-leave-to {
  transform: translate(-50%, -0.75rem);
  opacity: 0;
}

.report-content {
  color: #334155;
  font-size: 1rem;
  line-height: 1.75;
  font-variant-numeric: tabular-nums;
}

.report-content h1 {
  @apply mb-6 border-b border-slate-200 pb-4 font-display text-3xl font-semibold tracking-tight text-blue-950;
  border-bottom-color: rgba(178, 139, 73, 0.38);
}

.report-content h2 {
  @apply mt-10 mb-4 border-l-4 pl-3 font-display text-xl font-semibold text-blue-900;
  border-left-color: #b28b49;
}

.report-content h3 {
  @apply mt-8 mb-3 text-lg font-semibold text-slate-900;
}

.report-content p {
  @apply mb-5 text-slate-700;
}

.report-content strong {
  @apply font-semibold text-slate-950;
}

.report-content a {
  @apply font-medium text-blue-700 underline decoration-blue-200 underline-offset-4 hover:text-blue-900;
}

.report-content blockquote {
  font-style: normal;
  @apply my-6 rounded-lg border-l-4 bg-blue-50 px-5 py-4 text-slate-700;
  border-left-color: #b28b49;
}

.report-content ul {
  @apply mb-6 ml-6 list-disc space-y-2 text-slate-700;
}

.report-content ol {
  @apply mb-6 ml-6 list-decimal space-y-2 text-slate-700;
}

.report-content table {
  @apply my-8 w-full border-collapse overflow-hidden rounded-lg border border-slate-200 text-left text-sm shadow-sm;
}

.report-content thead {
  @apply bg-slate-50;
}

.report-content th {
  @apply border-b border-slate-200 px-4 py-3 font-semibold text-slate-950;
}

.report-content td {
  @apply border-b border-slate-100 px-4 py-3 text-slate-700;
}

.report-content pre {
  @apply my-6 overflow-x-auto rounded-lg bg-slate-950 p-5 text-slate-100 shadow-sm;
  font-family: Menlo, Monaco, "Courier New", monospace;
  font-size: 0.9em;
}

.report-content code {
  @apply rounded bg-blue-50 px-1.5 py-0.5 text-sm font-medium text-blue-800;
  font-family: theme('fontFamily.mono');
}

.report-content pre code {
  @apply bg-transparent p-0 text-xs text-slate-100;
}

.katex * {
  box-sizing: content-box !important;
}

.katex-display {
  overflow-x: auto;
  overflow-y: hidden;
  padding: 0.5em 0;
  margin: 1em 0 !important;
}

.katex {
  font-size: 1.08em;
  font-family: "Times New Roman", serif;
}

@media (prefers-reduced-motion: reduce) {
  *,
  *::before,
  *::after {
    animation-duration: 0.01ms !important;
    animation-iteration-count: 1 !important;
    scroll-behavior: auto !important;
    transition-duration: 0.01ms !important;
  }
}
</style>
