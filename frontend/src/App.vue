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
    <header class="sticky top-0 z-40 border-b border-slate-800/80 bg-slate-950/95 shadow-sm shadow-slate-950/20 backdrop-blur">
      <div class="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
        <div class="flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-lg border border-blue-300/30 bg-blue-500/15 text-blue-100 shadow-sm shadow-blue-950/30">
            <BotIcon class="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <p class="text-xs font-semibold uppercase text-blue-300">research agent workspace</p>
            <h1 class="font-display text-xl font-semibold text-white sm:text-2xl">
              DRAI 深度研究助手
            </h1>
          </div>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
          <nav v-if="authUser" class="flex rounded-lg border border-slate-700 bg-slate-900 p-1" aria-label="工作区">
            <button
              v-for="tab in workspaceTabs"
              :key="tab.id"
              type="button"
              class="flex min-h-9 items-center gap-2 rounded-md px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-300 focus:ring-offset-2 focus:ring-offset-slate-950"
              :class="activeWorkspace === tab.id ? 'bg-blue-100 text-blue-950 shadow-sm' : 'text-slate-300 hover:bg-slate-800 hover:text-white'"
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

    <main v-if="!authUser" class="mx-auto grid min-h-[calc(100vh-5rem)] max-w-7xl grid-cols-1 items-center gap-8 px-4 py-12 sm:px-6 lg:grid-cols-[1.1fr_28rem] lg:px-8">
      <section class="max-w-2xl">
        <p class="text-sm font-semibold text-blue-700">DRAI research agent</p>
        <h2 class="mt-3 font-display text-4xl font-semibold leading-tight text-slate-950 sm:text-5xl">
          把检索、撰写、质检收束到一条研究轨迹里。
        </h2>
        <p class="mt-5 max-w-xl text-base leading-7 text-slate-600">
          上传 PDF 或使用混合检索，DRAI 会按 Planner、Researcher、Writer、Reviewer 的节点生成可追踪报告。
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
            <p class="mt-1 text-xs leading-5 text-slate-500">版本、收藏、导出和再次修订保留在同一工作区。</p>
          </div>
        </div>
      </section>

      <section class="w-full overflow-hidden rounded-lg border border-slate-200 bg-white shadow-sm shadow-slate-200/70">
        <div class="h-1 bg-blue-700"></div>
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

    <main v-else-if="activeWorkspace === 'run'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <aside class="space-y-5 lg:col-span-4">
        <section class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="text-sm font-semibold text-blue-950">知识库</h2>
                <p class="mt-1 text-xs text-slate-500">上传 PDF 后可使用文档检索模式</p>
              </div>
              <span class="rounded-md bg-white px-2 py-1 text-xs font-medium text-blue-700 ring-1 ring-blue-100">最多 5 个 PDF</span>
            </div>
          </div>

          <div class="p-5">
            <label
              for="pdf-upload"
              class="group relative flex min-h-32 cursor-pointer flex-col items-center justify-center rounded-lg border border-dashed p-4 text-center transition focus-within:ring-2 focus-within:ring-blue-600 focus-within:ring-offset-2"
              :class="isDragging ? 'border-blue-500 bg-blue-50' : 'border-slate-300 bg-slate-50 hover:border-blue-400 hover:bg-blue-50/40'"
              @dragover.prevent="isDragging = true"
              @dragleave.prevent="isDragging = false"
              @drop.prevent="handleDrop"
            >
              <input
                id="pdf-upload"
                type="file"
                multiple
                accept=".pdf"
                class="absolute inset-0 cursor-pointer opacity-0"
                aria-label="上传 PDF 文档"
                @change="handleFileSelect"
              />

              <div v-if="uploadedFiles.length === 0" class="pointer-events-none flex flex-col items-center">
                <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-white text-blue-700 shadow-sm ring-1 ring-slate-200">
                  <UploadCloudIcon class="h-5 w-5" aria-hidden="true" />
                </div>
                <p class="text-sm font-medium text-slate-800">拖拽 PDF 文件到这里</p>
                <p class="mt-1 text-xs text-slate-500">也可以点击选择本地文件</p>
              </div>

              <div v-else class="pointer-events-none z-10 w-full space-y-2">
                <div
                  v-for="(file, i) in uploadedFiles"
                  :key="i"
                  class="flex min-h-10 items-center justify-between gap-3 rounded-md border border-slate-200 bg-white px-3 py-2 text-left text-xs shadow-sm"
                >
                  <div class="flex min-w-0 items-center gap-2">
                    <FileTextIcon class="h-4 w-4 shrink-0 text-blue-700" aria-hidden="true" />
                    <span class="truncate font-medium text-slate-700">{{ file.name }}</span>
                  </div>
                  <CheckCircle2Icon class="h-4 w-4 shrink-0 text-emerald-600" aria-hidden="true" />
                </div>
              </div>
            </label>
          </div>
        </section>

        <section class="rounded-lg border border-blue-100 bg-white p-5 shadow-sm shadow-blue-100/50">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-blue-950">任务类型</h2>
              <p class="mt-1 text-xs text-slate-500">选择通用调研或证券研究报告链路</p>
            </div>
            <BotIcon class="h-4 w-4 text-slate-400" aria-hidden="true" />
          </div>

          <div class="grid grid-cols-2 gap-2" role="group" aria-label="任务类型">
            <button
              type="button"
              :aria-pressed="runMode === 'research'"
              class="flex min-h-11 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
              :class="runMode === 'research' ? 'border-blue-700 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'"
              @click="runMode = 'research'"
            >
              <SearchIcon class="h-4 w-4" aria-hidden="true" />
              通用研究
            </button>

            <button
              type="button"
              :aria-pressed="runMode === 'stock'"
              class="flex min-h-11 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
              :class="runMode === 'stock' ? 'border-blue-700 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'"
              @click="runMode = 'stock'"
            >
              <FileOutputIcon class="h-4 w-4" aria-hidden="true" />
              证券代码分析
            </button>
          </div>
        </section>

        <section class="rounded-lg border border-blue-100 bg-white p-5 shadow-sm shadow-blue-100/50">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-blue-950">检索模式</h2>
              <p class="mt-1 text-xs text-slate-500">选择本次研究使用的证据来源</p>
            </div>
            <SearchIcon class="h-4 w-4 text-slate-400" aria-hidden="true" />
          </div>

          <div class="grid grid-cols-2 gap-2" role="group" aria-label="检索模式">
            <button
              type="button"
              :aria-pressed="searchMode === 'document'"
              :disabled="uploadedFiles.length === 0"
              class="flex min-h-11 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-45"
              :class="searchMode === 'document' ? 'border-blue-700 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'"
              @click="setMode('document')"
            >
              <FileTextIcon class="h-4 w-4" aria-hidden="true" />
              仅文档
            </button>

            <button
              type="button"
              :aria-pressed="searchMode === 'hybrid'"
              class="flex min-h-11 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
              :class="searchMode === 'hybrid' ? 'border-blue-700 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'"
              @click="setMode('hybrid')"
            >
              <Globe2Icon class="h-4 w-4" aria-hidden="true" />
              混合检索
            </button>
          </div>
        </section>

        <section class="rounded-lg border border-blue-100 bg-white p-5 shadow-sm shadow-blue-100/50">
          <label :for="runMode === 'stock' ? 'stock-ticker' : 'research-query'" class="text-sm font-semibold text-blue-950">
            {{ runMode === 'stock' ? '证券代码' : '研究任务' }}
          </label>
          <div v-if="runMode === 'stock'" class="mt-3 grid grid-cols-1 gap-3 sm:grid-cols-[1fr_8rem]">
            <input
              id="stock-ticker"
              v-model="stockTicker"
              type="text"
              inputmode="text"
              class="min-h-11 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold uppercase tracking-wide text-slate-900 shadow-sm transition placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500"
              placeholder="600519 或 588200"
              :disabled="isLoading"
            />
            <select
              v-model="stockReportPeriod"
              class="min-h-11 rounded-lg border border-slate-200 bg-white px-3 text-sm font-semibold text-slate-700 shadow-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
              :disabled="isLoading"
            >
              <option value="latest">最新期</option>
              <option value="annual">年报</option>
              <option value="quarterly">季报</option>
            </select>
          </div>
          <textarea
            v-else
            id="research-query"
            v-model="query"
            class="mt-3 min-h-28 w-full resize-none rounded-lg border border-slate-200 bg-white p-3 text-sm leading-6 text-slate-800 shadow-sm transition placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500"
            rows="4"
            placeholder="请输入研究主题，或输入对上一版报告的修订要求..."
            :disabled="isLoading"
          ></textarea>

          <button
            type="button"
            class="mt-4 flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none"
            :disabled="isLoading || !canStartRun"
            @click="startResearch"
          >
            <Loader2Icon v-if="isLoading" class="h-4 w-4 animate-spin" aria-hidden="true" />
            <SendIcon v-else class="h-4 w-4" aria-hidden="true" />
            <span>{{ isLoading ? '运行中' : (runMode === 'stock' ? '生成证券报告' : '开始研究') }}</span>
          </button>
        </section>

        <StatusFlow :currentStep="currentStep" :completedSteps="completedSteps" :flowType="runMode" />

        <section v-if="runMode === 'stock'" class="rounded-lg border border-blue-100 bg-white p-5 shadow-sm shadow-blue-100/50">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-blue-950">证券报告质检</h2>
              <p class="mt-1 text-xs text-slate-500">指标、证据和 Bad Case 回放</p>
            </div>
            <ShieldCheckIcon class="h-4 w-4 text-slate-400" aria-hidden="true" />
          </div>

          <div class="grid grid-cols-2 gap-2 text-xs text-slate-600">
            <div class="flex min-h-12 flex-col justify-center rounded-md bg-slate-50 px-3 py-2">
              <span>风险评分</span>
              <span class="mt-1 font-semibold text-slate-900">{{ financialRiskAssessment?.finalScore ?? '-' }}/10 · {{ financialRiskAssessment?.riskLevel || '-' }}</span>
            </div>
            <div class="flex min-h-12 flex-col justify-center rounded-md bg-slate-50 px-3 py-2">
              <span>合规审查</span>
              <span class="mt-1 font-semibold" :class="financialCompliance?.status === 'PASS' ? 'text-emerald-700' : 'text-amber-700'">
                {{ financialCompliance?.status || '-' }} · {{ financialCompliance?.score ?? '-' }}
              </span>
            </div>
            <div class="flex min-h-12 flex-col justify-center rounded-md bg-slate-50 px-3 py-2">
              <span>评测门控</span>
              <span class="mt-1 font-semibold" :class="financialEvaluation?.status === 'PASS' ? 'text-emerald-700' : 'text-amber-700'">
                {{ financialEvaluation?.status || '-' }} · {{ financialEvaluation?.overallScore ?? '-' }}
              </span>
            </div>
            <div class="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
              <span>证据条目</span>
              <span class="font-semibold text-slate-900">{{ financialSnapshotSummary?.evidenceCount ?? financialEvidence.length }}</span>
            </div>
            <div class="flex items-center justify-between rounded-md bg-slate-50 px-3 py-2">
              <span>缺失项</span>
              <span class="font-semibold text-slate-900">{{ financialSnapshotSummary?.missingCount ?? 0 }}</span>
            </div>
          </div>

          <div v-if="financialRiskAssessment" class="mt-4">
            <div class="h-2 overflow-hidden rounded-full bg-slate-100">
              <div class="h-full rounded-full transition-all" :class="riskScorePercent > 60 ? 'bg-rose-500' : 'bg-blue-700'" :style="{ width: `${riskScorePercent}%` }"></div>
            </div>
            <div class="mt-3 space-y-2">
              <div v-for="dimension in financialRiskAssessment.dimensions" :key="dimension.name" class="rounded-md border border-slate-100 px-3 py-2 text-xs">
                <div class="flex items-center justify-between gap-3">
                  <span class="font-semibold text-slate-800">{{ dimension.name }}</span>
                  <span class="font-mono text-slate-600">{{ dimension.score }}/10 · {{ dimension.weight }}%</span>
                </div>
                <p class="mt-1 leading-5 text-slate-500">{{ dimension.reason }}</p>
              </div>
            </div>
          </div>

          <div v-if="financialMetrics.length > 0" class="mt-4 space-y-2">
            <div v-for="metric in financialMetrics" :key="metric.metricName" class="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2 text-xs">
              <span class="min-w-0 truncate font-medium text-slate-700">{{ metric.metricName }}</span>
              <span class="shrink-0 font-semibold" :class="metric.status === 'OK' ? 'text-emerald-700' : 'text-amber-700'">{{ metric.displayValue }}</span>
            </div>
          </div>

          <div v-if="financialProviderStages.length > 0" class="mt-4 space-y-2">
            <p class="text-xs font-semibold text-slate-500">数据源执行</p>
            <div v-for="stage in financialProviderStages" :key="stage.stageName" class="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2 text-xs">
              <span class="min-w-0 truncate font-medium text-slate-700">{{ stage.stageName }}</span>
              <span class="shrink-0 font-mono" :class="stage.status === 'SUCCESS' ? 'text-emerald-700' : 'text-rose-700'">{{ stage.status }} · {{ stage.durationMs }}ms</span>
            </div>
          </div>

          <div v-if="evidenceBreakdown.length > 0" class="mt-4 flex flex-wrap gap-2">
            <span v-for="item in evidenceBreakdown" :key="item.sourceType" class="rounded-md bg-blue-50 px-2 py-1 text-xs font-semibold text-blue-800 ring-1 ring-blue-100">
              {{ item.sourceType }} · {{ item.count }}
            </span>
          </div>

          <div v-if="financialCompliance?.issues?.length" class="mt-4 space-y-2">
            <p class="text-xs font-semibold text-amber-700">合规问题</p>
            <div v-for="issue in financialCompliance.issues" :key="`${issue.category}-${issue.description}`" class="rounded-md border border-amber-100 bg-amber-50 px-3 py-2 text-xs text-amber-900">
              <div class="font-semibold">{{ issue.category }} · {{ issue.severity }}</div>
              <p class="mt-1 leading-5">{{ issue.description }}</p>
            </div>
          </div>

          <div v-if="financialEvaluation?.metricScores?.length" class="mt-4 space-y-2">
            <p class="text-xs font-semibold text-slate-500">评测指标</p>
            <div v-for="metric in financialEvaluation.metricScores" :key="metric.metricName" class="flex items-center justify-between gap-3 rounded-md border border-slate-100 px-3 py-2 text-xs">
              <span class="min-w-0 truncate font-medium text-slate-700">{{ metric.metricName }}</span>
              <span class="shrink-0 font-mono" :class="metric.status === 'PASS' ? 'text-emerald-700' : 'text-rose-700'">{{ metric.status }} · {{ metric.score }}</span>
            </div>
            <div v-if="financialEvaluation.failedReasons?.length" class="rounded-md border border-amber-100 bg-amber-50 px-3 py-2 text-xs leading-5 text-amber-900">
              {{ financialEvaluation.failedReasons.join('；') }}
            </div>
          </div>

          <div class="mt-4 grid grid-cols-2 gap-2">
            <button
              v-for="type in ['数字错', '引用错', '逻辑错', '信息过期']"
              :key="type"
              type="button"
              class="min-h-9 rounded-lg border border-slate-200 px-2 text-xs font-semibold text-slate-700 transition hover:bg-slate-50 disabled:cursor-not-allowed disabled:opacity-45"
              :disabled="!latestStockTaskId"
              @click="submitStockFeedback(type)"
            >
              {{ type }}
            </button>
          </div>
          <input
            v-model="stockFeedbackDetail"
            type="text"
            class="mt-2 min-h-9 w-full rounded-lg border border-slate-200 px-3 text-xs outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
            placeholder="可选：补充反馈说明"
          />
          <button
            type="button"
            class="mt-3 flex min-h-9 w-full items-center justify-center gap-2 rounded-lg bg-slate-950 px-3 text-xs font-semibold text-white transition hover:bg-slate-800 disabled:cursor-not-allowed disabled:bg-slate-300"
            :disabled="!latestStockTaskId"
            @click="loadStockReplay"
          >
            <EyeIcon class="h-3.5 w-3.5" aria-hidden="true" />
            回放本次快照
          </button>
        </section>

        <section class="overflow-hidden rounded-lg border border-slate-800 bg-slate-950 shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 py-3">
            <div class="flex items-center gap-2">
              <TerminalIcon class="h-4 w-4 text-blue-300" aria-hidden="true" />
              <h2 class="text-xs font-semibold tracking-wide text-slate-200">运行日志</h2>
            </div>
            <span class="text-xs text-slate-500">SSE</span>
          </div>
          <div
            ref="logsContainer"
            class="h-36 overflow-y-auto p-4 font-mono text-[11px] leading-5"
            aria-live="polite"
          >
            <div v-if="logs.length === 0" class="text-slate-500">系统已就绪，等待输入。</div>
            <div v-for="(log, i) in logs" :key="i" class="flex gap-2 py-0.5">
              <span class="shrink-0 text-blue-300">&gt;</span>
              <span class="break-all text-slate-300">{{ log }}</span>
            </div>
            <div v-if="isLoading" class="mt-2 animate-pulse text-blue-300">_</div>
          </div>
        </section>
      </aside>

      <section class="lg:col-span-8">
        <div class="flex min-h-[calc(100vh-9rem)] flex-col overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/70 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-blue-950">研究报告</h2>
              <p class="mt-1 text-sm text-slate-500">智能体工作流生成的报告内容</p>
            </div>
            <div class="flex items-center gap-2 text-xs font-medium text-slate-500">
              <span class="rounded-md bg-white px-2 py-1 text-blue-700 ring-1 ring-blue-100">{{ runModeLabel }}</span>
              <span class="rounded-md bg-white px-2 py-1 text-blue-700 ring-1 ring-blue-100">{{ searchModeLabel(searchMode) }}</span>
              <span class="rounded-md bg-white px-2 py-1 text-blue-700 ring-1 ring-blue-100">{{ currentStepLabel(currentStep) }}</span>
            </div>
          </div>

          <div class="flex-1 p-5 sm:p-6 lg:p-8">
            <div v-if="!displayedReport && !isLoading" class="flex min-h-[28rem] flex-col items-center justify-center rounded-lg border border-dashed border-blue-200 bg-blue-50/50 px-6 text-center">
              <div class="flex h-14 w-14 items-center justify-center rounded-lg bg-white text-blue-500 shadow-sm ring-1 ring-blue-100">
                <FileOutputIcon class="h-7 w-7" aria-hidden="true" />
              </div>
              <h3 class="mt-5 text-base font-semibold text-blue-950">暂无研究报告</h3>
              <p class="mt-2 max-w-sm text-sm leading-6 text-slate-500">
                完成一次研究后，报告会显示在这里。
              </p>
            </div>

            <div v-else-if="isLoading && !displayedReport" class="flex min-h-[28rem] flex-col justify-center rounded-lg border border-blue-100 bg-white px-6">
              <div class="mx-auto w-full max-w-xl">
                <div class="mb-6 flex items-center gap-3">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                    <Loader2Icon class="h-5 w-5 animate-spin" aria-hidden="true" />
                  </div>
                  <div>
                    <h3 class="text-sm font-semibold text-blue-950">研究流程运行中</h3>
                    <p class="mt-1 text-xs text-slate-500">当前步骤：{{ currentStepLabel(currentStep) }}</p>
                  </div>
                </div>

                <div class="space-y-3">
                  <div class="h-3 w-5/6 animate-pulse rounded bg-slate-200"></div>
                  <div class="h-3 w-full animate-pulse rounded bg-slate-200"></div>
                  <div class="h-3 w-4/5 animate-pulse rounded bg-slate-200"></div>
                  <div class="mt-5 h-24 animate-pulse rounded-lg bg-slate-100"></div>
                </div>
              </div>
            </div>

            <article v-else class="report-content prose prose-slate max-w-none">
              <div v-html="renderedReport"></div>
              <span v-if="isTyping" class="ml-1 inline-block h-5 w-2 animate-pulse bg-blue-700 align-middle"></span>
            </article>

            <section v-if="stockReplay" class="mt-6 rounded-lg border border-slate-200 bg-slate-50 p-4">
              <div class="mb-3 flex items-center justify-between gap-3">
                <h3 class="text-sm font-semibold text-slate-950">本次报告回放快照</h3>
                <button type="button" class="rounded-md p-1 text-slate-500 transition hover:bg-white hover:text-slate-800" aria-label="关闭回放" @click="stockReplay = null">
                  <XIcon class="h-4 w-4" aria-hidden="true" />
                </button>
              </div>
              <pre class="max-h-72 overflow-auto rounded-lg bg-slate-950 p-3 text-xs leading-5 text-slate-100">{{ stockReplay }}</pre>
            </section>
          </div>
        </div>
      </section>
    </main>

    <main v-else-if="activeWorkspace === 'tasks'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <section class="lg:col-span-5">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="text-base font-semibold text-blue-950">任务历史</h2>
                <p class="mt-1 text-sm text-slate-500">最近创建的研究任务</p>
              </div>
              <button
                type="button"
                class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
                @click="loadTasks"
              >
                <RefreshCwIcon class="h-4 w-4" :class="isLoadingTasks ? 'animate-spin' : ''" aria-hidden="true" />
                刷新
              </button>
            </div>
            <div class="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-[1fr_10rem]">
              <input
                v-model="taskKeyword"
                type="search"
                class="min-h-10 rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                placeholder="搜索研究问题..."
                @keyup.enter="loadTasks"
              />
              <select
                v-model="taskStatus"
                class="min-h-10 rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                @change="loadTasks"
              >
                <option value="">全部状态</option>
                <option value="RUNNING">运行中</option>
                <option value="COMPLETED">已完成</option>
                <option value="FAILED">失败</option>
              </select>
            </div>
          </div>

          <div class="divide-y divide-slate-100">
            <div v-if="taskError" class="px-5 py-4 text-sm text-rose-700">{{ taskError }}</div>
            <div v-else-if="isLoadingTasks" class="px-5 py-8 text-sm text-slate-500">正在加载任务...</div>
            <button
              v-for="task in tasks"
              :key="task.id"
              type="button"
              class="block w-full px-5 py-4 text-left transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600"
              :class="selectedTask?.id === task.id ? 'bg-blue-50/70' : 'bg-white'"
              @click="selectTask(task)"
            >
              <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-slate-950">{{ task.query }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ task.threadId }}</p>
                </div>
                <span class="shrink-0 rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(task.status)">
                  {{ statusLabel(task.status) }}
                </span>
              </div>
              <div class="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                <span>{{ searchModeLabel(task.searchMode) }}</span>
                <span>第 {{ task.revisionNumber }} 版</span>
                <span>{{ formatDate(task.updatedAt || task.createdAt) }}</span>
              </div>
            </button>
            <div v-if="!isLoadingTasks && tasks.length === 0" class="px-5 py-8 text-sm text-slate-500">
              暂无任务记录。
            </div>
          </div>
        </div>
      </section>

      <section class="space-y-6 lg:col-span-7">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <h2 class="text-base font-semibold text-blue-950">任务详情</h2>
            <p class="mt-1 text-sm text-slate-500">查看所选任务的基础信息与执行过程</p>
          </div>
          <div v-if="selectedTask" class="p-5">
            <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <dt class="text-xs font-medium tracking-wide text-slate-400">任务 ID</dt>
                <dd class="mt-1 text-sm font-semibold text-slate-950">#{{ selectedTask.id }}</dd>
              </div>
              <div>
                <dt class="text-xs font-medium tracking-wide text-slate-400">状态</dt>
                <dd class="mt-1"><span class="rounded-md px-2 py-1 text-xs font-semibold ring-1" :class="statusStyles(selectedTask.status)">{{ statusLabel(selectedTask.status) }}</span></dd>
              </div>
              <div class="sm:col-span-2">
                <dt class="text-xs font-medium tracking-wide text-slate-400">研究问题</dt>
                <dd class="mt-1 text-sm leading-6 text-slate-700">{{ selectedTask.query }}</dd>
              </div>
            </dl>
          </div>
          <div v-else class="p-5 text-sm text-slate-500">请选择一个任务查看详情。</div>
        </div>

        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <h2 class="text-base font-semibold text-blue-950">智能体时间线</h2>
            <p class="mt-1 text-sm text-slate-500">各节点写入的执行日志</p>
          </div>
          <ol class="divide-y divide-slate-100">
            <li v-for="log in taskLogs" :key="log.id" class="px-5 py-4">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-sm font-semibold text-slate-950">{{ stepNameLabel(log.stepName) }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ formatDate(log.createdAt) }}</p>
                </div>
                <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(log.status)">
                  {{ statusLabel(log.status) }}
                </span>
              </div>
              <pre class="mt-3 max-h-40 overflow-auto rounded-lg bg-slate-950 p-3 text-xs leading-5 text-slate-200">{{ log.errorMessage || log.outputSnapshot }}</pre>
            </li>
            <li v-if="taskLogs.length === 0" class="px-5 py-8 text-sm text-slate-500">
              当前任务暂无日志。
            </li>
          </ol>
        </div>
      </section>
    </main>

    <main v-else-if="activeWorkspace === 'reports'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <section class="lg:col-span-4">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="text-base font-semibold text-blue-950">{{ reportScope === 'library' ? '报告库' : '报告版本' }}</h2>
                <p class="mt-1 text-sm text-slate-500">{{ reportScope === 'library' ? '当前用户保存的全部报告' : `当前会话：${activeThreadId}` }}</p>
              </div>
              <button type="button" class="rounded-lg border border-blue-100 bg-white p-2 text-slate-600 transition hover:bg-blue-50 hover:text-blue-700" @click="loadReports(activeThreadId)" aria-label="刷新报告">
                <RefreshCwIcon class="h-4 w-4" :class="isLoadingReports ? 'animate-spin' : ''" aria-hidden="true" />
              </button>
            </div>
            <div class="mt-4 grid grid-cols-2 gap-2 rounded-lg border border-blue-100 bg-white/80 p-1">
              <button type="button" class="min-h-9 rounded-md text-sm font-semibold transition" :class="reportScope === 'thread' ? 'bg-white text-blue-800 shadow-sm' : 'text-slate-600 hover:bg-white/70'" @click="switchReportScope('thread')">
                当前会话
              </button>
              <button type="button" class="min-h-9 rounded-md text-sm font-semibold transition" :class="reportScope === 'library' ? 'bg-white text-blue-800 shadow-sm' : 'text-slate-600 hover:bg-white/70'" @click="switchReportScope('library')">
                报告库
              </button>
            </div>
            <div v-if="reportScope === 'library'" class="mt-3 space-y-3">
              <input
                v-model="reportKeyword"
                type="search"
                class="min-h-10 w-full rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                placeholder="搜索报告..."
                @keyup.enter="loadReports(activeThreadId)"
              />
              <label class="flex items-center gap-2 text-sm font-medium text-slate-600">
                <input v-model="favoriteOnly" type="checkbox" class="h-4 w-4 rounded border-slate-300 text-blue-700 focus:ring-blue-600" @change="loadReports(activeThreadId)" />
                仅看收藏
              </label>
            </div>
          </div>

          <div class="divide-y divide-slate-100">
            <div v-if="reportError" class="px-5 py-4 text-sm text-rose-700">{{ reportError }}</div>
            <button
              v-for="report in reports"
              :key="report.id"
              type="button"
              class="block w-full px-5 py-4 text-left transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-inset focus:ring-blue-600"
              :class="selectedReport?.id === report.id ? 'bg-blue-50/70' : 'bg-white'"
              @click="selectReport(report)"
            >
              <div class="flex items-center justify-between gap-3">
                <span class="text-sm font-semibold text-slate-950">版本 {{ report.version }}</span>
                <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(report.reviewStatus)">
                  {{ statusLabel(report.reviewStatus) }}
                </span>
              </div>
              <div class="mt-2 flex items-center justify-between gap-3 text-xs text-slate-500">
                <span class="truncate">{{ report.threadId }}</span>
                <StarIcon v-if="report.favorite" class="h-4 w-4 shrink-0 fill-amber-400 text-amber-500" aria-hidden="true" />
              </div>
              <p class="mt-1 text-xs text-slate-500">{{ formatDate(report.createdAt) }}</p>
            </button>
            <div v-if="!isLoadingReports && reports.length === 0" class="px-5 py-8 text-sm text-slate-500">
              暂无报告。
            </div>
          </div>
        </div>
      </section>

      <section class="lg:col-span-8">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex flex-col gap-3 border-b border-blue-100 bg-blue-50/70 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-blue-950">报告预览</h2>
              <p class="mt-1 text-sm text-slate-500">查看、导出，或基于当前版本继续修订</p>
            </div>
            <div class="flex flex-wrap gap-2">
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="copySelectedReport">
                <CopyIcon class="h-4 w-4" aria-hidden="true" />
                复制
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="toggleFavoriteSelectedReport">
                <StarIcon class="h-4 w-4" :class="selectedReport?.favorite ? 'fill-amber-400 text-amber-500' : ''" aria-hidden="true" />
                {{ selectedReport?.favorite ? '已收藏' : '收藏' }}
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="indexSelectedReport">
                <BookOpenIcon class="h-4 w-4" aria-hidden="true" />
                加入 RAG
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport('pdf')">
                <DownloadIcon class="h-4 w-4" aria-hidden="true" />
                PDF
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport('docx')">
                <FileTextIcon class="h-4 w-4" aria-hidden="true" />
                Word
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport('md')">
                <FileTextIcon class="h-4 w-4" aria-hidden="true" />
                MD
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-rose-200 px-3 text-sm font-semibold text-rose-700 transition hover:bg-rose-50 disabled:opacity-45" :disabled="!selectedReport" @click="deleteSelectedReport">
                <Trash2Icon class="h-4 w-4" aria-hidden="true" />
                删除
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg bg-blue-700 px-3 text-sm font-semibold text-white transition hover:bg-blue-800 disabled:bg-slate-300" :disabled="!selectedReport" @click="reviseSelectedReport">
                <EyeIcon class="h-4 w-4" aria-hidden="true" />
                修订
              </button>
            </div>
          </div>
          <div class="p-5 sm:p-6">
            <article v-if="selectedReport" class="report-content prose prose-slate max-w-none">
              <div v-html="md.render(selectedReport.content || '')"></div>
            </article>
            <div v-else class="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-sm text-slate-500">
              请选择一个报告版本进行预览。
            </div>
          </div>
        </div>
      </section>
    </main>

    <main v-else-if="activeWorkspace === 'admin'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <section class="space-y-6 lg:col-span-4">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex items-center justify-between border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div>
              <h2 class="text-base font-semibold text-blue-950">管理控制台</h2>
              <p class="mt-1 text-sm text-slate-500">跨用户管理与系统状态</p>
            </div>
            <button type="button" class="rounded-lg border border-blue-100 bg-white p-2 text-slate-600 transition hover:bg-blue-50 hover:text-blue-700" @click="loadAdminDashboard" aria-label="刷新管理控制台">
              <RefreshCwIcon class="h-4 w-4" :class="isLoadingAdmin ? 'animate-spin' : ''" aria-hidden="true" />
            </button>
          </div>
          <div v-if="adminError" class="border-b border-rose-100 bg-rose-50 px-5 py-3 text-sm text-rose-700">{{ adminError }}</div>
          <div class="grid grid-cols-1 divide-y divide-slate-100">
            <div v-for="(status, name) in adminHealth" :key="name" class="flex items-center justify-between px-5 py-3">
              <span class="text-sm font-medium capitalize text-slate-700">{{ name }}</span>
              <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(status)">{{ statusLabel(status) }}</span>
            </div>
          </div>
        </div>

        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <h2 class="text-base font-semibold text-blue-950">用户管理</h2>
            <div class="mt-3 flex gap-2">
              <input v-model="adminUserKeyword" type="search" class="min-h-10 min-w-0 flex-1 rounded-lg border border-blue-100 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="搜索用户..." @keyup.enter="loadAdminUsers" />
              <button type="button" class="rounded-lg border border-blue-100 bg-white px-3 text-sm font-semibold text-slate-700 hover:bg-blue-50 hover:text-blue-700" @click="loadAdminUsers">搜索</button>
            </div>
          </div>
          <div class="divide-y divide-slate-100">
            <div v-for="user in adminUsers" :key="user.id" class="px-5 py-4">
              <div class="flex items-start justify-between gap-3">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-slate-950">{{ user.username }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ user.email || '-' }}</p>
                </div>
                <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(user.status)">{{ statusLabel(user.status) }}</span>
              </div>
              <div class="mt-3 grid grid-cols-2 gap-2">
                <select :value="user.role" class="min-h-9 rounded-lg border border-slate-200 px-2 text-sm" @change="changeAdminUserRole(user, $event.target.value)">
                  <option value="USER">普通用户</option>
                  <option value="ADMIN">管理员</option>
                </select>
                <select :value="user.status" class="min-h-9 rounded-lg border border-slate-200 px-2 text-sm" @change="changeAdminUserStatus(user, $event.target.value)">
                  <option value="ACTIVE">启用</option>
                  <option value="DISABLED">停用</option>
                </select>
              </div>
            </div>
            <div v-if="adminUsers.length === 0" class="px-5 py-8 text-sm text-slate-500">暂无用户。</div>
          </div>
        </div>
      </section>

      <section class="space-y-6 lg:col-span-8">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="text-base font-semibold text-blue-950">全局任务</h2>
                <p class="mt-1 text-sm text-slate-500">跨用户任务监控</p>
              </div>
              <button type="button" class="rounded-lg border border-blue-100 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-blue-50 hover:text-blue-700" @click="loadAdminTasks">刷新</button>
            </div>
            <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-3">
              <input v-model="adminTaskKeyword" type="search" class="min-h-10 rounded-lg border border-blue-100 px-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="关键词" @keyup.enter="loadAdminTasks" />
              <input v-model="adminTaskOwnerId" type="number" class="min-h-10 rounded-lg border border-blue-100 px-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="用户 ID" @keyup.enter="loadAdminTasks" />
              <select v-model="adminTaskStatus" class="min-h-10 rounded-lg border border-blue-100 px-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" @change="loadAdminTasks">
                <option value="">全部状态</option>
                <option value="RUNNING">运行中</option>
                <option value="COMPLETED">已完成</option>
                <option value="FAILED">失败</option>
              </select>
            </div>
          </div>
          <div class="overflow-x-auto">
            <table class="w-full min-w-[760px] text-left text-sm">
              <thead class="border-b border-blue-100 bg-blue-50 text-xs text-slate-500">
                <tr>
                  <th class="px-5 py-3">任务</th>
                  <th class="px-5 py-3">用户</th>
                  <th class="px-5 py-3">状态</th>
                  <th class="px-5 py-3">更新时间</th>
                  <th class="px-5 py-3">日志</th>
                </tr>
              </thead>
              <tbody class="divide-y divide-slate-100">
                <tr v-for="task in adminTasks" :key="task.id" class="hover:bg-slate-50">
                  <td class="px-5 py-4">
                    <p class="max-w-md truncate font-semibold text-slate-950">{{ task.query }}</p>
                    <p class="mt-1 text-xs text-slate-500">{{ task.threadId }}</p>
                  </td>
                  <td class="px-5 py-4 text-slate-600">{{ task.ownerUsername }} #{{ task.ownerId }}</td>
                  <td class="px-5 py-4"><span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(task.status)">{{ statusLabel(task.status) }}</span></td>
                  <td class="px-5 py-4 text-slate-500">{{ formatDate(task.updatedAt) }}</td>
                  <td class="px-5 py-4"><button type="button" class="text-sm font-semibold text-blue-700 hover:text-blue-900" @click="loadAdminTaskLogs(task)">查看</button></td>
                </tr>
                <tr v-if="adminTasks.length === 0"><td colspan="5" class="px-5 py-8 text-sm text-slate-500">暂无任务。</td></tr>
              </tbody>
            </table>
          </div>
          <div v-if="adminSelectedTask" class="border-t border-slate-100 px-5 py-4">
            <h3 class="text-sm font-semibold text-blue-950">任务 #{{ adminSelectedTask.id }} 的日志</h3>
            <div class="mt-3 max-h-56 space-y-2 overflow-auto">
              <pre v-for="log in adminTaskLogs" :key="log.id" class="rounded-lg bg-slate-950 p-3 text-xs leading-5 text-slate-200">{{ stepNameLabel(log.stepName) }} · {{ statusLabel(log.status) }}\n{{ log.errorMessage || log.outputSnapshot }}</pre>
            </div>
          </div>
        </div>

        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="text-base font-semibold text-blue-950">全局报告</h2>
                <p class="mt-1 text-sm text-slate-500">跨用户报告管理</p>
              </div>
              <button type="button" class="rounded-lg border border-blue-100 bg-white px-3 py-2 text-sm font-semibold text-slate-700 hover:bg-blue-50 hover:text-blue-700" @click="loadAdminReports">刷新</button>
            </div>
            <div class="mt-3 grid grid-cols-1 gap-2 sm:grid-cols-2">
              <input v-model="adminReportKeyword" type="search" class="min-h-10 rounded-lg border border-blue-100 px-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="关键词" @keyup.enter="loadAdminReports" />
              <input v-model="adminReportOwnerId" type="number" class="min-h-10 rounded-lg border border-blue-100 px-3 text-sm outline-none focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20" placeholder="用户 ID" @keyup.enter="loadAdminReports" />
            </div>
          </div>
          <div class="divide-y divide-slate-100">
            <div v-for="report in adminReports" :key="report.id" class="px-5 py-4">
              <div class="flex items-start justify-between gap-4">
                <div class="min-w-0">
                  <p class="truncate text-sm font-semibold text-slate-950">{{ report.threadId }} · 版本 {{ report.version }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ report.ownerUsername }} #{{ report.ownerId }} · {{ formatDate(report.createdAt) }}</p>
                </div>
                <button type="button" class="rounded-lg border border-rose-200 px-3 py-2 text-sm font-semibold text-rose-700 hover:bg-rose-50" @click="deleteAdminReport(report)">删除</button>
              </div>
              <p class="mt-3 line-clamp-2 text-sm leading-6 text-slate-600">{{ report.content }}</p>
            </div>
            <div v-if="adminReports.length === 0" class="px-5 py-8 text-sm text-slate-500">暂无报告。</div>
          </div>
        </div>
      </section>
    </main>

    <main v-else class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
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
  </div>
</template>

<script setup>
import { ref, computed, nextTick, onMounted } from 'vue';
import {
    AlertTriangleIcon,
    BotIcon,
    CheckCircle2Icon,
    ClipboardListIcon,
    CopyIcon,
    DatabaseIcon,
    DownloadIcon,
    EyeIcon,
    BookOpenIcon,
    FileOutputIcon,
    FileTextIcon,
    Globe2Icon,
    HistoryIcon,
    LayersIcon,
    Loader2Icon,
    RefreshCwIcon,
    SearchIcon,
    SendIcon,
    ServerIcon,
    SettingsIcon,
    ShieldCheckIcon,
    StarIcon,
    TerminalIcon,
    Trash2Icon,
    UploadCloudIcon,
    XIcon
} from 'lucide-vue-next';
import {
    uploadFiles,
    streamChat,
    streamStockReport,
    saveStockFeedback,
    getStockReplay,
    clearContext,
    listTasks,
    getTask,
    getTaskLogs,
    getThreadReports,
    listReports as listReportLibrary,
    getReport,
    exportReport,
    updateReportFavorite,
    deleteReport as deleteReportApi,
    indexReportToKnowledgeBase,
    adminListUsers,
    adminUpdateUserRole,
    adminUpdateUserStatus,
    adminListTasks,
    adminGetTaskLogs,
    adminListReports,
    adminDeleteReport,
    adminSystemHealth,
    currentThreadId,
    login as authLogin,
    register as authRegister,
    getCurrentUser,
    setAuthToken
} from './services/api';
import StatusFlow from './components/StatusFlow.vue';
import MarkdownIt from 'markdown-it';
import mk from 'markdown-it-katex';

const md = new MarkdownIt({
    html: true,
    linkify: true,
    typographer: true
});
md.use(mk);

const showWarning = ref(false);
const warningMessage = ref('');
const triggerWarning = (msg) => {
    warningMessage.value = msg;
    showWarning.value = true;
    setTimeout(() => {
        showWarning.value = false;
    }, 5000);
};

const query = ref('');
const runMode = ref('research');
const stockTicker = ref('600519');
const stockReportPeriod = ref('latest');
const latestStockTaskId = ref(null);
const financialMetrics = ref([]);
const financialEvidence = ref([]);
const financialSnapshotSummary = ref(null);
const financialRiskAssessment = ref(null);
const financialCompliance = ref(null);
const financialEvaluation = ref(null);
const financialProviderStages = ref([]);
const stockFeedbackDetail = ref('');
const stockReplay = ref(null);
const isLoading = ref(false);
const currentStep = ref('idle');
const completedSteps = ref([]);
const logs = ref([]);
const logsContainer = ref(null);
const uploadedFiles = ref([]);
const isDragging = ref(false);
const searchMode = ref('hybrid');
const activeWorkspace = ref('run');
const activeThreadId = ref(currentThreadId);

const displayedReport = ref('');
const isTyping = ref(false);
const authUser = ref(null);
const authMode = ref('login');
const authForm = ref({ username: '', email: '', password: '' });
const authError = ref('');
const isAuthLoading = ref(false);

const tasks = ref([]);
const selectedTask = ref(null);
const taskLogs = ref([]);
const taskPage = ref({ page: 1, size: 10, total: 0 });
const taskKeyword = ref('');
const taskStatus = ref('');
const isLoadingTasks = ref(false);
const taskError = ref('');

const reports = ref([]);
const selectedReport = ref(null);
const isLoadingReports = ref(false);
const reportError = ref('');
const reportScope = ref('thread');
const reportKeyword = ref('');
const favoriteOnly = ref(false);

const isLoadingAdmin = ref(false);
const adminError = ref('');
const adminHealth = ref({});
const adminUsers = ref([]);
const adminTasks = ref([]);
const adminReports = ref([]);
const adminTaskLogs = ref([]);
const adminSelectedTask = ref(null);
const adminUserKeyword = ref('');
const adminTaskKeyword = ref('');
const adminTaskOwnerId = ref('');
const adminTaskStatus = ref('');
const adminReportKeyword = ref('');
const adminReportOwnerId = ref('');

const configItems = computed(() => [
    { name: 'LLM 服务', status: '后端配置', desc: '兼容 OpenAI 的对话模型，缺失时使用本地降级逻辑。', icon: ServerIcon },
    { name: 'Tavily 搜索', status: '可选密钥', desc: '联网检索来源；未配置密钥时返回降级结果。', icon: Globe2Icon },
    { name: 'MySQL', status: '必需', desc: '持久化任务、日志、报告和检查点数据。', icon: DatabaseIcon },
    { name: 'Redis', status: '可选缓存', desc: '缓存运行中任务状态和最新 SSE 事件。', icon: LayersIcon },
    { name: 'ChromaDB', status: '可选向量库', desc: '向量 RAG 存储；不可用时回退到本地内存实现。', icon: SearchIcon }
]);

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

const canStartRun = computed(() => {
    if (runMode.value === 'stock') {
        return /^\d{6}(\.(SH|SZ))?$/i.test(stockTicker.value.trim());
    }
    return Boolean(query.value.trim());
});

const runModeLabel = computed(() => runMode.value === 'stock' ? '证券代码分析' : '通用研究');

const riskScorePercent = computed(() => {
    const score = Number(financialRiskAssessment.value?.finalScore || 0);
    return Math.max(0, Math.min(100, score * 10));
});

const evidenceBreakdown = computed(() => {
    const counts = financialEvidence.value.reduce((acc, item) => {
        const key = item.sourceType || 'UNKNOWN';
        acc[key] = (acc[key] || 0) + 1;
        return acc;
    }, {});
    return Object.entries(counts).map(([sourceType, count]) => ({ sourceType, count }));
});

const initializeWorkspace = async () => {
    await loadTasks();
    await loadReports(currentThreadId);
};

const setWorkspace = async (workspace) => {
    activeWorkspace.value = workspace;
    if (workspace === 'admin' && authUser.value?.role === 'ADMIN') {
        await loadAdminDashboard();
    }
};

const submitAuth = async () => {
    authError.value = '';
    isAuthLoading.value = true;
    try {
        authUser.value = authMode.value === 'login'
            ? await authLogin(authForm.value.username, authForm.value.password)
            : await authRegister(authForm.value.username, authForm.value.email, authForm.value.password);
        await initializeWorkspace();
    } catch (error) {
        authError.value = error.message;
    } finally {
        isAuthLoading.value = false;
    }
};

const logout = () => {
    setAuthToken('');
    authUser.value = null;
    tasks.value = [];
    reports.value = [];
    adminUsers.value = [];
    adminTasks.value = [];
    adminReports.value = [];
    adminTaskLogs.value = [];
    adminHealth.value = {};
    selectedTask.value = null;
    selectedReport.value = null;
    adminSelectedTask.value = null;
    activeWorkspace.value = 'run';
};

const renderedReport = computed(() => {
    let raw = displayedReport.value || '';

    raw = raw.replace(/\\\[/g, () => '$$').replace(/\\\]/g, () => '$$');
    raw = raw.replace(/\\\(/g, '$').replace(/\\\)/g, '$');
    raw = raw.replace(/\[\s*(\\text|\\frac|\\sum|\\int)/g, '$$$$ $1');

    return md.render(raw);
});

const scrollToBottom = async () => {
    await nextTick();
    if (logsContainer.value) logsContainer.value.scrollTop = logsContainer.value.scrollHeight;
};

const handleFileSelect = async (event) => {
    processFiles(event.target.files);
};

const handleDrop = async (event) => {
    isDragging.value = false;
    processFiles(event.dataTransfer.files);
};

const processFiles = async (files) => {
    if (files.length > 5) {
        alert('最多只能上传 5 个文件。');
        return;
    }

    uploadedFiles.value = Array.from(files);

    if (uploadedFiles.value.length > 0) {
        logs.value.push(`[系统] 正在上传 ${files.length} 个文档...`);
        try {
            const res = await uploadFiles(uploadedFiles.value);
            logs.value.push(`[系统] 知识库已构建，已索引 ${res.chunks_stored} 个文本块。`);
        } catch (e) {
            logs.value.push(`[错误] 上传失败：${e.message}`);
            alert(`上传失败：${e.message}`);
            uploadedFiles.value = [];
        }
    }
};

const setMode = (mode) => {
    searchMode.value = mode;
};

const statusLabel = (status) => {
    const value = (status || '').toUpperCase();
    const labels = {
        RUNNING: '运行中',
        COMPLETED: '已完成',
        FAILED: '失败',
        SUCCESS: '成功',
        PASS: '通过',
        FAIL: '未通过',
        ACTIVE: '启用',
        DISABLED: '停用',
        READY: '就绪',
        OK: '正常'
    };
    return labels[value] || status || '-';
};

const searchModeLabel = (mode) => {
    const value = (mode || '').toLowerCase();
    if (value === 'document') return '仅文档';
    if (value === 'hybrid') return '混合检索';
    if (value === 'stock-hybrid') return '股票混合';
    if (value === 'stock-document') return '股票文档';
    if (value === 'stock-web') return '股票联网';
    return mode || '-';
};

const currentStepLabel = (step) => {
    const labels = {
        idle: '就绪',
        planner: '规划中',
        researcher: '检索中',
        stock_resolve: '股票解析',
        data_snapshot: '数据快照',
        metric_engine: '指标计算',
        risk_assessment: '风险评分',
        evidence_collect: '证据账本',
        writer: '撰写中',
        reviewer: '质检中',
        evaluation: '评测中',
        refiner: '修订中',
        done: '已完成'
    };
    return labels[step] || step || '-';
};

const stepNameLabel = (step) => {
    const value = (step || '').toLowerCase();
    const labels = {
        planner: '规划',
        researcher: '检索',
        stock_resolve: '股票解析',
        data_snapshot: '数据快照',
        metric_engine: '指标计算',
        risk_assessment: '风险评分',
        evidence_collect: '证据账本',
        writer: '撰写',
        reviewer: '质检',
        evaluation: '评测',
        refiner: '修订'
    };
    return labels[value] || step || '-';
};

const formatDate = (value) => {
    if (!value) return '-';
    return new Intl.DateTimeFormat('zh-CN', {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit'
    }).format(new Date(value));
};

const statusStyles = (status) => {
    const value = (status || '').toUpperCase();
    if (value === 'COMPLETED' || value === 'SUCCESS' || value === 'PASS') return 'bg-emerald-50 text-emerald-700 ring-emerald-200';
    if (value === 'FAILED' || value === 'FAIL') return 'bg-rose-50 text-rose-700 ring-rose-200';
    if (value === 'RUNNING') return 'bg-blue-50 text-blue-700 ring-blue-200';
    return 'bg-slate-100 text-slate-600 ring-slate-200';
};

const loadTasks = async () => {
    isLoadingTasks.value = true;
    taskError.value = '';
    try {
        const page = await listTasks({
            page: taskPage.value.page,
            size: taskPage.value.size,
            status: taskStatus.value,
            keyword: taskKeyword.value
        });
        tasks.value = page.items || [];
        taskPage.value = { page: page.page, size: page.size, total: page.total };
        if (!selectedTask.value && tasks.value.length > 0) {
            await selectTask(tasks.value[0]);
        }
    } catch (error) {
        taskError.value = error.message;
    } finally {
        isLoadingTasks.value = false;
    }
};

const selectTask = async (task) => {
    selectedTask.value = await getTask(task.id);
    taskLogs.value = await getTaskLogs(task.id);
    activeThreadId.value = selectedTask.value.threadId || currentThreadId;
    reportScope.value = 'thread';
    await loadReports(activeThreadId.value);
};

const switchReportScope = async (scope) => {
    reportScope.value = scope;
    selectedReport.value = null;
    await loadReports(activeThreadId.value);
};

const loadReports = async (threadId = activeThreadId.value) => {
    isLoadingReports.value = true;
    reportError.value = '';
    try {
        reports.value = reportScope.value === 'library'
            ? await listReportLibrary({ keyword: reportKeyword.value, favoriteOnly: favoriteOnly.value })
            : await getThreadReports(threadId);
        if ((!selectedReport.value || !reports.value.some((report) => report.id === selectedReport.value.id)) && reports.value.length > 0) {
            selectedReport.value = reports.value[0];
        } else if (reports.value.length === 0) {
            selectedReport.value = null;
        }
    } catch (error) {
        reportError.value = error.message;
    } finally {
        isLoadingReports.value = false;
    }
};

const selectReport = async (report) => {
    selectedReport.value = await getReport(report.id);
    activeThreadId.value = selectedReport.value.threadId || currentThreadId;
    displayedReport.value = selectedReport.value.content || '';
};

const reviseSelectedReport = () => {
    if (!selectedReport.value) return;
    activeThreadId.value = selectedReport.value.threadId || currentThreadId;
    query.value = '修改上一版报告：请补充关键结论，并让内容更适合简历项目展示。';
    displayedReport.value = selectedReport.value.content || '';
    activeWorkspace.value = 'run';
};

const copySelectedReport = async () => {
    if (!selectedReport.value?.content) return;
    await navigator.clipboard.writeText(selectedReport.value.content);
    logs.value.push('[系统] 报告已复制到剪贴板。');
};

const downloadSelectedReport = async (format = 'pdf') => {
    if (!selectedReport.value) return;
    try {
        const result = await exportReport(selectedReport.value.id, format);
        const url = URL.createObjectURL(result.blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = result.filename;
        link.click();
        URL.revokeObjectURL(url);
        logs.value.push(`[系统] 报告已导出为 ${format.toUpperCase()}。`);
    } catch (error) {
        triggerWarning(error.message);
    }
};

const toggleFavoriteSelectedReport = async () => {
    if (!selectedReport.value) return;
    try {
        const updated = await updateReportFavorite(selectedReport.value.id, !selectedReport.value.favorite);
        selectedReport.value = updated;
        reports.value = reports.value.map((report) => report.id === updated.id ? { ...report, favorite: updated.favorite } : report);
    } catch (error) {
        triggerWarning(error.message);
    }
};

const indexSelectedReport = async () => {
    if (!selectedReport.value) return;
    try {
        const result = await indexReportToKnowledgeBase(selectedReport.value.id);
        logs.value.push(`[系统] 报告已加入 RAG 知识库，文本块数量：${result.chunksStored}。`);
        selectedReport.value = await getReport(selectedReport.value.id);
        reports.value = reports.value.map((report) => report.id === selectedReport.value.id ? selectedReport.value : report);
    } catch (error) {
        triggerWarning(error.message);
    }
};

const deleteSelectedReport = async () => {
    if (!selectedReport.value) return;
    const confirmed = window.confirm('确定要从报告库删除这份报告吗？');
    if (!confirmed) return;
    try {
        await deleteReportApi(selectedReport.value.id);
        reports.value = reports.value.filter((report) => report.id !== selectedReport.value.id);
        selectedReport.value = reports.value[0] || null;
        displayedReport.value = selectedReport.value?.content || '';
    } catch (error) {
        triggerWarning(error.message);
    }
};

const loadAdminDashboard = async () => {
    isLoadingAdmin.value = true;
    adminError.value = '';
    try {
        await Promise.all([
            loadAdminHealth(),
            loadAdminUsers(),
            loadAdminTasks(),
            loadAdminReports()
        ]);
    } catch (error) {
        adminError.value = error.message;
    } finally {
        isLoadingAdmin.value = false;
    }
};

const loadAdminHealth = async () => {
    const health = await adminSystemHealth();
    adminHealth.value = health.components || {};
};

const loadAdminUsers = async () => {
    adminUsers.value = await adminListUsers({ keyword: adminUserKeyword.value });
};

const changeAdminUserRole = async (user, role) => {
    try {
        const updated = await adminUpdateUserRole(user.id, role);
        adminUsers.value = adminUsers.value.map((item) => item.id === updated.id ? updated : item);
    } catch (error) {
        triggerWarning(error.message);
        await loadAdminUsers();
    }
};

const changeAdminUserStatus = async (user, status) => {
    try {
        const updated = await adminUpdateUserStatus(user.id, status);
        adminUsers.value = adminUsers.value.map((item) => item.id === updated.id ? updated : item);
    } catch (error) {
        triggerWarning(error.message);
        await loadAdminUsers();
    }
};

const loadAdminTasks = async () => {
    adminTasks.value = await adminListTasks({
        status: adminTaskStatus.value,
        ownerId: adminTaskOwnerId.value,
        keyword: adminTaskKeyword.value
    });
};

const loadAdminTaskLogs = async (task) => {
    adminSelectedTask.value = task;
    adminTaskLogs.value = await adminGetTaskLogs(task.id);
};

const loadAdminReports = async () => {
    adminReports.value = await adminListReports({
        ownerId: adminReportOwnerId.value,
        keyword: adminReportKeyword.value
    });
};

const deleteAdminReport = async (report) => {
    const confirmed = window.confirm(`确定要删除报告 #${report.id} 吗？`);
    if (!confirmed) return;
    try {
        await adminDeleteReport(report.id);
        adminReports.value = adminReports.value.filter((item) => item.id !== report.id);
    } catch (error) {
        triggerWarning(error.message);
    }
};

let typingInterval = null;
const typeWriterEffect = (text) => {
    isTyping.value = true;

    if (typingInterval) {
        clearInterval(typingInterval);
    }

    let index = 0;
    typingInterval = setInterval(() => {
        if (index < text.length) {
            displayedReport.value += text.slice(index, index + 3);
            index += 3;
        } else {
            clearInterval(typingInterval);
            typingInterval = null;
            isTyping.value = false;
        }
    }, 10);
};

const startResearch = async () => {
    if (runMode.value === 'stock') {
        await startStockResearch();
        return;
    }
    if (!query.value) return;

    isLoading.value = true;
    currentStep.value = 'planner';
    completedSteps.value = [];
    logs.value = [];
    logs.value.push(`[初始化] 系统已就绪，检索模式：${searchModeLabel(searchMode.value)}`);
    displayedReport.value = '';

    const actualMode = uploadedFiles.value.length === 0 ? 'hybrid' : searchMode.value;

    try {
        if (uploadedFiles.value.length > 0) {
            logs.value.push(`[系统] 正在上传 ${uploadedFiles.value.length} 个文档...`);
            const res = await uploadFiles(uploadedFiles.value);
            logs.value.push(`[系统] 知识库已构建，已索引 ${res.chunks_stored} 个文本块。`);
        } else {
            logs.value.push('[系统] 正在清理上一轮知识库上下文...');
            await clearContext();
            logs.value.push('[系统] 上下文已清理，将使用联网混合检索。');
        }

        streamChat(
            query.value,
            actualMode,
            (data) => {
                    if (data.step) {
                        currentStep.value = data.step;
                        if (!completedSteps.value.includes(data.step)) {
                            completedSteps.value = [...completedSteps.value, data.step];
                        }
                    }

                    if (data.step === 'planner') {
                        const plan = data.data.plan || [];
                        logs.value.push(`[规划] 检索策略：[${plan.join(', ')}]`);
                    }

                    else if (data.step === 'researcher') {
                        const results = data.data.search_results || data.data.searchResults || [];
                        const resultsStr = JSON.stringify(results);

                        if (resultsStr.includes("流程已终止")) {
                            triggerWarning("文档与问题无关，任务已强制停止");
                            logs.value.push('[系统] 任务已停止：仅文档模式下上下文与问题无关。');
                            currentStep.value = 'done';
                            return;
                        }

                        if (resultsStr.includes("自动切换为全网搜索")) {
                            triggerWarning("文档与问题无关，已自动切换为全网搜索");
                        } else if (resultsStr.includes("Document Only 模式")) {
                            triggerWarning("文档与问题无关，无法回答");
                        }

                        logs.value.push(`[检索] 证据收集完成，条目数：${results.length}`);
                    }

                    else if (data.step === 'writer') {
                        logs.value.push('[撰写] 正在生成报告内容...');
                        const finalReport = data.data.final_report || data.data.finalReport;
                        if (finalReport) {
                            displayedReport.value = '';
                            typeWriterEffect(finalReport);
                        }
                    }

                    else if (data.step === 'reviewer') {
                        const reviewStatus = data.data.review_status || data.data.reviewStatus;
                        const critique = data.data.critique || '';
                        if (reviewStatus === 'FAIL') {
                            logs.value.push(`[质检] 未通过：${critique}，正在重新规划。`);
                            currentStep.value = 'planner';
                        } else {
                            logs.value.push('[质检] 已通过。');
                        }
                    }
                    else if (data.step === 'refiner') {
                        currentStep.value = 'refiner';
                        logs.value.push('[修订] 正在根据反馈调整报告...');
                        const finalReport = data.data.final_report || data.data.finalReport;
                        if (finalReport) {
                            displayedReport.value = '';
                            typeWriterEffect(finalReport);
                        }
                    }

                    scrollToBottom();
                },
            () => {
                isLoading.value = false;
                currentStep.value = 'done';
                logs.value.push('[完成] 研究流程已结束。');
                loadTasks();
                loadReports(activeThreadId.value);
                scrollToBottom();
            },
            (err) => {
                isLoading.value = false;
                logs.value.push(`[错误] ${err.message}`);
                scrollToBottom();
            },
            activeThreadId.value
        );
    } catch (e) {
        isLoading.value = false;
        logs.value.push(`[错误] 初始化失败：${e.message}`);
        alert(`系统错误：${e.message}`);
    }
};

const startStockResearch = async () => {
    if (!canStartRun.value) return;

    isLoading.value = true;
    currentStep.value = 'stock_resolve';
    completedSteps.value = [];
    logs.value = [];
    displayedReport.value = '';
    latestStockTaskId.value = null;
    financialMetrics.value = [];
    financialEvidence.value = [];
    financialSnapshotSummary.value = null;
    financialRiskAssessment.value = null;
    financialCompliance.value = null;
    financialEvaluation.value = null;
    financialProviderStages.value = [];
    stockReplay.value = null;
    logs.value.push(`[初始化] 证券代码分析：${stockTicker.value.trim().toUpperCase()}，检索模式：${searchModeLabel(searchMode.value)}`);

    const actualMode = uploadedFiles.value.length === 0 ? 'hybrid' : searchMode.value;

    try {
        if (uploadedFiles.value.length > 0) {
            logs.value.push(`[系统] 正在上传 ${uploadedFiles.value.length} 个研究资料...`);
            const res = await uploadFiles(uploadedFiles.value);
            logs.value.push(`[系统] 证券研究知识库已构建，已索引 ${res.chunks_stored} 个文本块。`);
        } else {
            logs.value.push('[系统] 正在清理上一轮知识库上下文...');
            await clearContext();
            logs.value.push('[系统] 上下文已清理，将使用公开数据源与降级缺失标记。');
        }

        streamStockReport(
            stockTicker.value.trim().toUpperCase(),
            actualMode,
            stockReportPeriod.value,
            handleStockEvent,
            () => {
                isLoading.value = false;
                currentStep.value = 'done';
                logs.value.push('[完成] 证券报告流程已结束。');
                loadTasks();
                loadReports(activeThreadId.value);
                scrollToBottom();
            },
            (err) => {
                isLoading.value = false;
                logs.value.push(`[错误] ${err.message}`);
                scrollToBottom();
            },
            activeThreadId.value
        );
    } catch (e) {
        isLoading.value = false;
        logs.value.push(`[错误] 初始化失败：${e.message}`);
        alert(`系统错误：${e.message}`);
    }
};

const handleStockEvent = (event) => {
    if (event.step) {
        currentStep.value = event.step;
        if (!completedSteps.value.includes(event.step)) {
            completedSteps.value = [...completedSteps.value, event.step];
        }
    }
    const payload = event.data || {};
    if (event.step === 'stock_resolve') {
        const subject = payload.subject || {};
        const assetLabel = subject.assetType === 'ETF' ? 'ETF解析' : '股票解析';
        logs.value.push(`[${assetLabel}] ${subject.fullCode || stockTicker.value}，${subject.companyName || '待识别上市公司'}`);
    } else if (event.step === 'data_snapshot') {
        financialSnapshotSummary.value = {
            evidenceCount: payload.evidenceCount || 0,
            missingCount: payload.missingCount || 0
        };
        logs.value.push(`[数据快照] 证据 ${financialSnapshotSummary.value.evidenceCount} 条，缺失标记 ${financialSnapshotSummary.value.missingCount} 条。`);
    } else if (event.step === 'metric_engine') {
        financialMetrics.value = payload.metrics || [];
        logs.value.push(`[指标计算] 已计算 ${financialMetrics.value.length} 个财务指标。`);
    } else if (event.step === 'risk_assessment') {
        financialRiskAssessment.value = payload.riskAssessment || payload.risk_assessment || null;
        const score = financialRiskAssessment.value?.finalScore ?? '-';
        const level = financialRiskAssessment.value?.riskLevel || '-';
        logs.value.push(`[风险评分] 综合风险 ${score}/10，等级：${level}。`);
    } else if (event.step === 'evidence_collect') {
        financialEvidence.value = payload.evidence || [];
        financialProviderStages.value = payload.stageResults || payload.stage_results || [];
        logs.value.push(`[证据账本] 有效证据 ${payload.effectiveCount || 0} 条。`);
    } else if (event.step === 'writer') {
        logs.value.push(`[撰写] 正在生成第 ${payload.attempt || 1} 版证券研究报告...`);
        const finalReport = payload.final_report || payload.finalReport;
        if (finalReport) {
            displayedReport.value = '';
            typeWriterEffect(finalReport);
        }
    } else if (event.step === 'reviewer') {
        const reviewStatus = payload.review_status || payload.reviewStatus;
        const critique = payload.critique || '';
        financialCompliance.value = payload.compliance || null;
        logs.value.push(reviewStatus === 'PASS' ? '[引用审查] 已通过。' : `[引用审查] 未通过：${critique}`);
    } else if (event.step === 'evaluation') {
        financialEvaluation.value = payload.evaluation || null;
        const status = financialEvaluation.value?.status || '-';
        const score = financialEvaluation.value?.overallScore ?? '-';
        logs.value.push(`[自动评测] ${status}，综合分：${score}。`);
    } else if (event.step === 'done') {
        latestStockTaskId.value = payload.taskId || null;
        logs.value.push(`[完成] 任务 #${latestStockTaskId.value || '-'} 已写入报告库。`);
    }
    scrollToBottom();
};

const submitStockFeedback = async (feedbackType) => {
    if (!latestStockTaskId.value) return;
    try {
        await saveStockFeedback(latestStockTaskId.value, feedbackType, stockFeedbackDetail.value);
        logs.value.push(`[反馈] 已记录 Bad Case：${feedbackType}`);
        stockFeedbackDetail.value = '';
        scrollToBottom();
    } catch (error) {
        triggerWarning(error.message);
    }
};

const loadStockReplay = async () => {
    if (!latestStockTaskId.value) return;
    try {
        const replay = await getStockReplay(latestStockTaskId.value);
        stockReplay.value = JSON.stringify(replay, null, 2);
        logs.value.push('[回放] 已加载本次 snapshot + evidence + metric。');
        scrollToBottom();
    } catch (error) {
        triggerWarning(error.message);
    }
};

onMounted(async () => {
    try {
        authUser.value = await getCurrentUser();
        await initializeWorkspace();
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
  @apply mb-6 border-b border-slate-200 pb-4 font-display text-3xl font-semibold text-slate-950;
}

.report-content h2 {
  @apply mt-10 mb-4 border-l-4 border-blue-700 pl-3 font-display text-xl font-semibold text-slate-900;
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
  @apply my-6 rounded-lg border-l-4 border-blue-700 bg-blue-50 px-5 py-4 text-slate-700;
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
