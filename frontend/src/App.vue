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
        aria-label="Dismiss warning"
        @click="showWarning = false"
      >
        <XIcon class="h-4 w-4" aria-hidden="true" />
      </button>
    </div>
  </transition>

  <div class="min-h-screen bg-[#f6f8fb] text-slate-950 selection:bg-blue-100 selection:text-blue-950">
    <header class="sticky top-0 z-40 border-b border-slate-200/80 bg-white/95 backdrop-blur">
      <div class="mx-auto flex max-w-7xl flex-col gap-4 px-4 py-4 sm:px-6 lg:flex-row lg:items-center lg:justify-between lg:px-8">
        <div class="flex items-center gap-3">
          <div class="flex h-11 w-11 items-center justify-center rounded-lg bg-slate-950 text-white shadow-sm">
            <BotIcon class="h-5 w-5" aria-hidden="true" />
          </div>
          <div>
            <h1 class="text-xl font-semibold tracking-tight text-slate-950 sm:text-2xl">
              DRAI Research Agent
            </h1>
            <p class="text-sm text-slate-500">
              Spring Boot · LangGraph4j · RAG · SSE
            </p>
          </div>
        </div>

        <div class="flex flex-col gap-3 sm:flex-row sm:items-center">
          <nav class="flex rounded-lg border border-slate-200 bg-slate-50 p-1" aria-label="Workspace">
            <button
              v-for="tab in workspaceTabs"
              :key="tab.id"
              type="button"
              class="flex min-h-9 items-center gap-2 rounded-md px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
              :class="activeWorkspace === tab.id ? 'bg-white text-blue-800 shadow-sm' : 'text-slate-600 hover:bg-white/70 hover:text-slate-950'"
              @click="activeWorkspace = tab.id"
            >
              <component :is="tab.icon" class="h-4 w-4" aria-hidden="true" />
              <span>{{ tab.label }}</span>
            </button>
          </nav>

          <div class="hidden flex-wrap gap-2 text-xs font-medium text-slate-600 xl:flex">
            <span class="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1">Java Backend</span>
            <span class="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1">Multi-Agent Flow</span>
            <span class="rounded-md border border-slate-200 bg-slate-50 px-2.5 py-1">Live Stream</span>
          </div>
        </div>
      </div>
    </header>

    <main v-if="activeWorkspace === 'run'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <aside class="space-y-5 lg:col-span-4">
        <section class="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="border-b border-slate-100 px-5 py-4">
            <div class="flex items-center justify-between gap-3">
              <div>
                <h2 class="text-sm font-semibold text-slate-950">Knowledge Base</h2>
                <p class="mt-1 text-xs text-slate-500">PDF evidence for document mode</p>
              </div>
              <span class="rounded-md bg-slate-100 px-2 py-1 text-xs font-medium text-slate-600">Max 5 PDFs</span>
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
                aria-label="Upload PDF documents"
                @change="handleFileSelect"
              />

              <div v-if="uploadedFiles.length === 0" class="pointer-events-none flex flex-col items-center">
                <div class="mb-3 flex h-10 w-10 items-center justify-center rounded-lg bg-white text-blue-700 shadow-sm ring-1 ring-slate-200">
                  <UploadCloudIcon class="h-5 w-5" aria-hidden="true" />
                </div>
                <p class="text-sm font-medium text-slate-800">Drop PDF files here</p>
                <p class="mt-1 text-xs text-slate-500">or click to select local files</p>
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

        <section class="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <div class="mb-3 flex items-center justify-between gap-3">
            <div>
              <h2 class="text-sm font-semibold text-slate-950">Retrieval Mode</h2>
              <p class="mt-1 text-xs text-slate-500">Choose evidence source for the next run</p>
            </div>
            <SearchIcon class="h-4 w-4 text-slate-400" aria-hidden="true" />
          </div>

          <div class="grid grid-cols-2 gap-2" role="group" aria-label="Search mode">
            <button
              type="button"
              :aria-pressed="searchMode === 'document'"
              :disabled="uploadedFiles.length === 0"
              class="flex min-h-11 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-45"
              :class="searchMode === 'document' ? 'border-blue-700 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'"
              @click="setMode('document')"
            >
              <FileTextIcon class="h-4 w-4" aria-hidden="true" />
              Doc Only
            </button>

            <button
              type="button"
              :aria-pressed="searchMode === 'hybrid'"
              class="flex min-h-11 items-center justify-center gap-2 rounded-lg border px-3 text-sm font-semibold transition focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
              :class="searchMode === 'hybrid' ? 'border-blue-700 bg-blue-50 text-blue-800' : 'border-slate-200 bg-white text-slate-600 hover:border-slate-300 hover:bg-slate-50'"
              @click="setMode('hybrid')"
            >
              <Globe2Icon class="h-4 w-4" aria-hidden="true" />
              Hybrid
            </button>
          </div>
        </section>

        <section class="rounded-lg border border-slate-200 bg-white p-5 shadow-sm">
          <label for="research-query" class="text-sm font-semibold text-slate-950">Research Task</label>
          <textarea
            id="research-query"
            v-model="query"
            class="mt-3 min-h-28 w-full resize-none rounded-lg border border-slate-200 bg-white p-3 text-sm leading-6 text-slate-800 shadow-sm transition placeholder:text-slate-400 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-600/20 disabled:cursor-not-allowed disabled:bg-slate-50 disabled:text-slate-500"
            rows="4"
            placeholder="Enter a research topic or a follow-up revision request..."
            :disabled="isLoading"
          ></textarea>

          <button
            type="button"
            class="mt-4 flex min-h-11 w-full items-center justify-center gap-2 rounded-lg bg-blue-700 px-4 py-2.5 text-sm font-semibold text-white shadow-sm transition hover:bg-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2 disabled:cursor-not-allowed disabled:bg-slate-300 disabled:text-slate-500 disabled:shadow-none"
            :disabled="isLoading || !query"
            @click="startResearch"
          >
            <Loader2Icon v-if="isLoading" class="h-4 w-4 animate-spin" aria-hidden="true" />
            <SendIcon v-else class="h-4 w-4" aria-hidden="true" />
            <span>{{ isLoading ? 'Processing' : 'Run Research' }}</span>
          </button>
        </section>

        <StatusFlow :currentStep="currentStep" :completedSteps="completedSteps" />

        <section class="overflow-hidden rounded-lg border border-slate-800 bg-slate-950 shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-800 bg-slate-900 px-4 py-3">
            <div class="flex items-center gap-2">
              <TerminalIcon class="h-4 w-4 text-blue-300" aria-hidden="true" />
              <h2 class="text-xs font-semibold uppercase tracking-wide text-slate-200">Runtime Logs</h2>
            </div>
            <span class="text-xs text-slate-500">SSE</span>
          </div>
          <div
            ref="logsContainer"
            class="h-36 overflow-y-auto p-4 font-mono text-[11px] leading-5"
            aria-live="polite"
          >
            <div v-if="logs.length === 0" class="text-slate-500">System ready. Waiting for input.</div>
            <div v-for="(log, i) in logs" :key="i" class="flex gap-2 py-0.5">
              <span class="shrink-0 text-blue-300">&gt;</span>
              <span class="break-all text-slate-300">{{ log }}</span>
            </div>
            <div v-if="isLoading" class="mt-2 animate-pulse text-blue-300">_</div>
          </div>
        </section>
      </aside>

      <section class="lg:col-span-8">
        <div class="flex min-h-[calc(100vh-9rem)] flex-col rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-slate-950">Research Report</h2>
              <p class="mt-1 text-sm text-slate-500">Generated output from the agent workflow</p>
            </div>
            <div class="flex items-center gap-2 text-xs font-medium text-slate-500">
              <span class="rounded-md bg-slate-100 px-2 py-1">{{ searchMode.toUpperCase() }}</span>
              <span class="rounded-md bg-slate-100 px-2 py-1">{{ currentStep === 'idle' ? 'READY' : currentStep.toUpperCase() }}</span>
            </div>
          </div>

          <div class="flex-1 p-5 sm:p-6 lg:p-8">
            <div v-if="!displayedReport && !isLoading" class="flex min-h-[28rem] flex-col items-center justify-center rounded-lg border border-dashed border-slate-200 bg-slate-50 px-6 text-center">
              <div class="flex h-14 w-14 items-center justify-center rounded-lg bg-white text-slate-500 shadow-sm ring-1 ring-slate-200">
                <FileOutputIcon class="h-7 w-7" aria-hidden="true" />
              </div>
              <h3 class="mt-5 text-base font-semibold text-slate-950">No report generated</h3>
              <p class="mt-2 max-w-sm text-sm leading-6 text-slate-500">
                The report workspace will populate after the agent completes a research run.
              </p>
            </div>

            <div v-else-if="isLoading && !displayedReport" class="flex min-h-[28rem] flex-col justify-center rounded-lg border border-slate-200 bg-white px-6">
              <div class="mx-auto w-full max-w-xl">
                <div class="mb-6 flex items-center gap-3">
                  <div class="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-50 text-blue-700">
                    <Loader2Icon class="h-5 w-5 animate-spin" aria-hidden="true" />
                  </div>
                  <div>
                    <h3 class="text-sm font-semibold text-slate-950">Agent workflow running</h3>
                    <p class="mt-1 text-xs text-slate-500">Current step: {{ currentStep }}</p>
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
          </div>
        </div>
      </section>
    </main>

    <main v-else-if="activeWorkspace === 'tasks'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <section class="lg:col-span-5">
        <div class="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="border-b border-slate-100 px-5 py-4">
            <div class="flex flex-col gap-3 sm:flex-row sm:items-center sm:justify-between">
              <div>
                <h2 class="text-base font-semibold text-slate-950">Task History</h2>
                <p class="mt-1 text-sm text-slate-500">Recent research tasks from MySQL</p>
              </div>
              <button
                type="button"
                class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 focus:outline-none focus:ring-2 focus:ring-blue-600 focus:ring-offset-2"
                @click="loadTasks"
              >
                <RefreshCwIcon class="h-4 w-4" :class="isLoadingTasks ? 'animate-spin' : ''" aria-hidden="true" />
                Refresh
              </button>
            </div>
            <div class="mt-4 grid grid-cols-1 gap-2 sm:grid-cols-[1fr_10rem]">
              <input
                v-model="taskKeyword"
                type="search"
                class="min-h-10 rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                placeholder="Search query..."
                @keyup.enter="loadTasks"
              />
              <select
                v-model="taskStatus"
                class="min-h-10 rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                @change="loadTasks"
              >
                <option value="">All status</option>
                <option value="RUNNING">RUNNING</option>
                <option value="COMPLETED">COMPLETED</option>
                <option value="FAILED">FAILED</option>
              </select>
            </div>
          </div>

          <div class="divide-y divide-slate-100">
            <div v-if="taskError" class="px-5 py-4 text-sm text-rose-700">{{ taskError }}</div>
            <div v-else-if="isLoadingTasks" class="px-5 py-8 text-sm text-slate-500">Loading tasks...</div>
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
                  {{ task.status }}
                </span>
              </div>
              <div class="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                <span>{{ task.searchMode }}</span>
                <span>revision {{ task.revisionNumber }}</span>
                <span>{{ formatDate(task.updatedAt || task.createdAt) }}</span>
              </div>
            </button>
            <div v-if="!isLoadingTasks && tasks.length === 0" class="px-5 py-8 text-sm text-slate-500">
              No task records found.
            </div>
          </div>
        </div>
      </section>

      <section class="space-y-6 lg:col-span-7">
        <div class="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="border-b border-slate-100 px-5 py-4">
            <h2 class="text-base font-semibold text-slate-950">Task Detail</h2>
            <p class="mt-1 text-sm text-slate-500">Selected task metadata and execution timeline</p>
          </div>
          <div v-if="selectedTask" class="p-5">
            <dl class="grid grid-cols-1 gap-4 sm:grid-cols-2">
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-400">Task ID</dt>
                <dd class="mt-1 text-sm font-semibold text-slate-950">#{{ selectedTask.id }}</dd>
              </div>
              <div>
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-400">Status</dt>
                <dd class="mt-1"><span class="rounded-md px-2 py-1 text-xs font-semibold ring-1" :class="statusStyles(selectedTask.status)">{{ selectedTask.status }}</span></dd>
              </div>
              <div class="sm:col-span-2">
                <dt class="text-xs font-medium uppercase tracking-wide text-slate-400">Query</dt>
                <dd class="mt-1 text-sm leading-6 text-slate-700">{{ selectedTask.query }}</dd>
              </div>
            </dl>
          </div>
          <div v-else class="p-5 text-sm text-slate-500">Select a task to inspect details.</div>
        </div>

        <div class="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="border-b border-slate-100 px-5 py-4">
            <h2 class="text-base font-semibold text-slate-950">Agent Timeline</h2>
            <p class="mt-1 text-sm text-slate-500">What each node wrote into the step log</p>
          </div>
          <ol class="divide-y divide-slate-100">
            <li v-for="log in taskLogs" :key="log.id" class="px-5 py-4">
              <div class="flex items-start justify-between gap-4">
                <div>
                  <p class="text-sm font-semibold text-slate-950">{{ log.stepName }}</p>
                  <p class="mt-1 text-xs text-slate-500">{{ formatDate(log.createdAt) }}</p>
                </div>
                <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(log.status)">
                  {{ log.status }}
                </span>
              </div>
              <pre class="mt-3 max-h-40 overflow-auto rounded-lg bg-slate-950 p-3 text-xs leading-5 text-slate-200">{{ log.errorMessage || log.outputSnapshot }}</pre>
            </li>
            <li v-if="taskLogs.length === 0" class="px-5 py-8 text-sm text-slate-500">
              No logs for the selected task.
            </li>
          </ol>
        </div>
      </section>
    </main>

    <main v-else-if="activeWorkspace === 'reports'" class="mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
      <section class="lg:col-span-4">
        <div class="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="flex items-center justify-between border-b border-slate-100 px-5 py-4">
            <div>
              <h2 class="text-base font-semibold text-slate-950">Report Versions</h2>
              <p class="mt-1 text-sm text-slate-500">Current thread: {{ activeThreadId }}</p>
            </div>
            <button type="button" class="rounded-lg border border-slate-200 p-2 text-slate-600 transition hover:bg-slate-50" @click="loadReports(activeThreadId)" aria-label="Refresh reports">
              <RefreshCwIcon class="h-4 w-4" :class="isLoadingReports ? 'animate-spin' : ''" aria-hidden="true" />
            </button>
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
                <span class="text-sm font-semibold text-slate-950">Version {{ report.version }}</span>
                <span class="rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(report.reviewStatus)">
                  {{ report.reviewStatus }}
                </span>
              </div>
              <p class="mt-2 text-xs text-slate-500">{{ formatDate(report.createdAt) }}</p>
            </button>
            <div v-if="!isLoadingReports && reports.length === 0" class="px-5 py-8 text-sm text-slate-500">
              No report versions for this thread.
            </div>
          </div>
        </div>
      </section>

      <section class="lg:col-span-8">
        <div class="rounded-lg border border-slate-200 bg-white shadow-sm">
          <div class="flex flex-col gap-3 border-b border-slate-100 px-5 py-4 sm:flex-row sm:items-center sm:justify-between">
            <div>
              <h2 class="text-base font-semibold text-slate-950">Report Preview</h2>
              <p class="mt-1 text-sm text-slate-500">Review, export, or start a revision from this version</p>
            </div>
            <div class="flex flex-wrap gap-2">
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="copySelectedReport">
                <CopyIcon class="h-4 w-4" aria-hidden="true" />
                Copy
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg border border-slate-200 px-3 text-sm font-semibold text-slate-700 transition hover:bg-slate-50 disabled:opacity-45" :disabled="!selectedReport" @click="downloadSelectedReport">
                <DownloadIcon class="h-4 w-4" aria-hidden="true" />
                Export
              </button>
              <button type="button" class="flex min-h-9 items-center gap-2 rounded-lg bg-blue-700 px-3 text-sm font-semibold text-white transition hover:bg-blue-800 disabled:bg-slate-300" :disabled="!selectedReport" @click="reviseSelectedReport">
                <EyeIcon class="h-4 w-4" aria-hidden="true" />
                Revise
              </button>
            </div>
          </div>
          <div class="p-5 sm:p-6">
            <article v-if="selectedReport" class="report-content prose prose-slate max-w-none">
              <div v-html="md.render(selectedReport.content || '')"></div>
            </article>
            <div v-else class="rounded-lg border border-dashed border-slate-200 bg-slate-50 px-6 py-16 text-center text-sm text-slate-500">
              Select a report version to preview.
            </div>
          </div>
        </div>
      </section>
    </main>

    <main v-else class="mx-auto max-w-7xl px-4 py-6 sm:px-6 lg:px-8">
      <section class="rounded-lg border border-slate-200 bg-white shadow-sm">
        <div class="border-b border-slate-100 px-5 py-4">
          <h2 class="text-base font-semibold text-slate-950">System Configuration</h2>
          <p class="mt-1 text-sm text-slate-500">Frontend visibility for backend dependencies and fallback behavior</p>
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
    TerminalIcon,
    UploadCloudIcon,
    XIcon
} from 'lucide-vue-next';
import {
    uploadFiles,
    streamChat,
    clearContext,
    listTasks,
    getTask,
    getTaskLogs,
    getThreadReports,
    getReport,
    currentThreadId
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
const configItems = computed(() => [
    { name: 'LLM Provider', status: 'Configured by backend', desc: 'OpenAI-compatible chat model with local fallback', icon: ServerIcon },
    { name: 'Tavily Search', status: 'Optional key', desc: 'Web search source with fallback result when key is missing', icon: Globe2Icon },
    { name: 'MySQL', status: 'Required', desc: 'Task, log, report and checkpoint persistence', icon: DatabaseIcon },
    { name: 'Redis', status: 'Optional runtime cache', desc: 'Running task state and latest SSE event cache', icon: LayersIcon },
    { name: 'ChromaDB', status: 'Optional vector store', desc: 'Vector RAG store with local in-memory fallback', icon: SearchIcon }
]);

const workspaceTabs = [
    { id: 'run', label: 'Run', icon: SendIcon },
    { id: 'tasks', label: 'Tasks', icon: ClipboardListIcon },
    { id: 'reports', label: 'Reports', icon: HistoryIcon },
    { id: 'settings', label: 'Settings', icon: SettingsIcon }
];

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
        alert("Maximum 5 files allowed!");
        return;
    }

    uploadedFiles.value = Array.from(files);

    if (uploadedFiles.value.length > 0) {
        logs.value.push(`[SYSTEM] Uploading ${files.length} document(s)...`);
        try {
            const res = await uploadFiles(uploadedFiles.value);
            logs.value.push(`[SYSTEM] Knowledge base built. ${res.chunks_stored} chunks indexed.`);
        } catch (e) {
            logs.value.push(`[ERROR] Upload failed: ${e.message}`);
            alert("Upload failed: " + e.message);
            uploadedFiles.value = [];
        }
    }
};

const setMode = (mode) => {
    searchMode.value = mode;
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
    await loadReports(activeThreadId.value);
};

const loadReports = async (threadId = activeThreadId.value) => {
    isLoadingReports.value = true;
    reportError.value = '';
    try {
        reports.value = await getThreadReports(threadId);
        if (!selectedReport.value && reports.value.length > 0) {
            selectedReport.value = reports.value[0];
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
    logs.value.push('[SYSTEM] Report copied to clipboard.');
};

const downloadSelectedReport = () => {
    if (!selectedReport.value?.content) return;
    const blob = new Blob([selectedReport.value.content], { type: 'text/markdown;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `report-v${selectedReport.value.version || selectedReport.value.id}.md`;
    link.click();
    URL.revokeObjectURL(url);
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
    if (!query.value) return;

    isLoading.value = true;
    currentStep.value = 'planner';
    completedSteps.value = [];
    logs.value = [];
    logs.value.push(`[INIT] System initialized. Mode: ${searchMode.value.toUpperCase()}`);
    displayedReport.value = '';

    const actualMode = uploadedFiles.value.length === 0 ? 'hybrid' : searchMode.value;

    try {
        if (uploadedFiles.value.length > 0) {
            logs.value.push(`[SYSTEM] Uploading ${uploadedFiles.value.length} document(s)...`);
            const res = await uploadFiles(uploadedFiles.value);
            logs.value.push(`[SYSTEM] Knowledge base built. ${res.chunks_stored} chunks indexed.`);
        } else {
            logs.value.push(`[SYSTEM] Clearing previous knowledge base...`);
            await clearContext();
            logs.value.push(`[SYSTEM] Context cleared. Running in pure Web Search mode.`);
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
                        logs.value.push(`[PLANNER] Strategy: [${plan.join(', ')}]`);
                    }

                    else if (data.step === 'researcher') {
                        const results = data.data.search_results || data.data.searchResults || [];
                        const resultsStr = JSON.stringify(results);

                        if (resultsStr.includes("流程已终止")) {
                            triggerWarning("文档与问题无关，任务已强制停止");
                            logs.value.push(`[SYSTEM] Task terminated: Context irrelevant in Doc-Only mode.`);
                            currentStep.value = 'done';
                            return;
                        }

                        if (resultsStr.includes("自动切换为全网搜索")) {
                            triggerWarning("文档与问题无关，已自动切换为全网搜索");
                        } else if (resultsStr.includes("Document Only 模式")) {
                            triggerWarning("文档与问题无关，无法回答");
                        }

                        logs.value.push(`[RESEARCHER] Data acquisition complete. Items: ${results.length}`);
                    }

                    else if (data.step === 'writer') {
                        logs.value.push(`[WRITER] Drafting content...`);
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
                            logs.value.push(`[QA] Review FAILED: ${critique} -> Rerolling`);
                            currentStep.value = 'planner';
                        } else {
                            logs.value.push(`[QA] Review PASSED.`);
                        }
                    }
                    else if (data.step === 'refiner') {
                        currentStep.value = 'refiner';
                        logs.value.push(`[REFINER] Modifying report based on feedback...`);
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
                logs.value.push('[DONE] Process complete.');
                loadTasks();
                loadReports(activeThreadId.value);
                scrollToBottom();
            },
            (err) => {
                isLoading.value = false;
                logs.value.push(`[ERROR] ${err.message}`);
                scrollToBottom();
            },
            activeThreadId.value
        );
    } catch (e) {
        isLoading.value = false;
        logs.value.push(`[ERROR] Initialization failed: ${e.message}`);
        alert("System Error: " + e.message);
    }
};

onMounted(async () => {
    await loadTasks();
    await loadReports(currentThreadId);
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
}

.report-content h1 {
  @apply mb-6 border-b border-slate-200 pb-4 text-3xl font-semibold tracking-tight text-slate-950;
}

.report-content h2 {
  @apply mt-10 mb-4 border-l-4 border-blue-700 pl-3 text-xl font-semibold tracking-tight text-slate-900;
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
  @apply my-8 w-full border-collapse overflow-hidden rounded-lg border border-slate-200 text-left text-sm;
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
  font-family: Menlo, Monaco, "Courier New", monospace;
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
