<template>
    <main class="workspace-main mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
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
                @keyup.enter="searchTasks"
              />
              <select
                v-model="taskStatus"
                class="min-h-10 rounded-lg border border-slate-200 px-3 text-sm outline-none transition focus:border-blue-600 focus:ring-2 focus:ring-blue-600/20"
                @change="filterTasks"
              >
                <option value="">全部状态</option>
                <option value="CREATED">待执行</option>
                <option value="RUNNING">运行中</option>
                <option value="COMPLETED">已完成</option>
                <option value="INSUFFICIENT_EVIDENCE">证据不足</option>
                <option value="FAILED">失败</option>
                <option value="CANCELLED">已取消</option>
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
                  <p class="mt-1 text-xs text-slate-500">任务 #{{ task.id }} · 会话 {{ shortThreadId(task.threadId) }}</p>
                </div>
                <span class="shrink-0 rounded-md px-2 py-1 text-[11px] font-semibold ring-1" :class="statusStyles(task.status)">
                  {{ statusLabel(task.status) }}
                </span>
              </div>
              <div class="mt-3 flex flex-wrap gap-2 text-xs text-slate-500">
                <span>{{ searchModeLabel(task.searchMode) }}</span>
                <span>{{ formatDate(task.updatedAt || task.createdAt) }}</span>
              </div>
            </button>
            <div v-if="!isLoadingTasks && tasks.length === 0" class="px-5 py-8 text-sm text-slate-500">
              暂无任务记录。
            </div>
          </div>
          <div v-if="taskPage.total > 0" class="flex items-center justify-between gap-3 border-t border-slate-100 px-5 py-3 text-xs text-slate-500">
            <span>第 {{ taskPage.page }} / {{ totalPages }} 页 · 共 {{ taskPage.total }} 条</span>
            <div class="flex gap-2">
              <button type="button" class="min-h-8 rounded-md border border-slate-200 px-3 font-semibold text-slate-700 disabled:opacity-40" :disabled="isLoadingTasks || taskPage.page <= 1" @click="changePage(taskPage.page - 1)">上一页</button>
              <button type="button" class="min-h-8 rounded-md border border-slate-200 px-3 font-semibold text-slate-700 disabled:opacity-40" :disabled="isLoadingTasks || taskPage.page >= totalPages" @click="changePage(taskPage.page + 1)">下一页</button>
            </div>
          </div>
        </div>
      </section>

      <section class="space-y-6 lg:col-span-7">
        <div class="overflow-hidden rounded-lg border border-blue-100 bg-white shadow-sm shadow-blue-100/50">
          <div class="flex items-start justify-between gap-4 border-b border-blue-100 bg-blue-50/70 px-5 py-4">
            <div>
              <h2 class="text-base font-semibold text-blue-950">任务详情</h2>
              <p class="mt-1 text-sm text-slate-500">查看所选任务的基础信息与执行过程</p>
            </div>
            <button v-if="canCancelSelected" type="button" class="min-h-9 shrink-0 rounded-lg border border-rose-200 bg-white px-3 text-sm font-semibold text-rose-700 hover:bg-rose-50 disabled:opacity-50" :disabled="isCancelling" @click="cancelSelectedTask">
              {{ isCancelling ? '取消中…' : '取消任务' }}
            </button>
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
                <dt class="text-xs font-medium tracking-wide text-slate-400">证券研究任务</dt>
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
</template>

<script setup>
import { computed, onMounted, ref, watch } from 'vue';
import { RefreshCwIcon } from 'lucide-vue-next';
import { cancelResearchRun, currentThreadId, getTask, getTaskLogs, listTasks } from '../services/api';
import {
  formatDate,
  searchModeLabel,
  statusLabel,
  statusStyles,
  stepNameLabel
} from '../modules/presentation';

const props = defineProps({
  refreshRevision: {
    type: Number,
    default: 0
  }
});

const emit = defineEmits(['thread-change']);

const tasks = ref([]);
const selectedTask = ref(null);
const taskLogs = ref([]);
const taskPage = ref({ page: 1, size: 10, total: 0 });
const taskKeyword = ref('');
const taskStatus = ref('');
const isLoadingTasks = ref(false);
const isCancelling = ref(false);
const taskError = ref('');
const totalPages = computed(() => Math.max(1, Math.ceil(taskPage.value.total / taskPage.value.size)));
const canCancelSelected = computed(() => ['CREATED', 'RUNNING', 'RETRYING'].includes(selectedTask.value?.status));

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
    const current = tasks.value.find(task => task.id === selectedTask.value?.id);
    if (current) {
      await selectTask(current);
    } else if (tasks.value.length > 0) {
      await selectTask(tasks.value[0]);
    } else if (tasks.value.length === 0) {
      selectedTask.value = null;
      taskLogs.value = [];
    }
  } catch (error) {
    taskError.value = error.message;
  } finally {
    isLoadingTasks.value = false;
  }
};

const searchTasks = () => {
  taskPage.value.page = 1;
  loadTasks();
};

const filterTasks = () => {
  taskPage.value.page = 1;
  loadTasks();
};

const changePage = page => {
  taskPage.value.page = Math.min(Math.max(1, page), totalPages.value);
  loadTasks();
};

const cancelSelectedTask = async () => {
  if (!canCancelSelected.value || !window.confirm('确认取消这个研究任务？已提交的最终报告不会被删除。')) return;
  isCancelling.value = true;
  taskError.value = '';
  try {
    await cancelResearchRun(selectedTask.value.id);
    await loadTasks();
  } catch (error) {
    taskError.value = error.message;
  } finally {
    isCancelling.value = false;
  }
};

const selectTask = async (task) => {
  try {
    selectedTask.value = await getTask(task.id);
    taskLogs.value = await getTaskLogs(task.id);
    emit('thread-change', selectedTask.value.threadId || currentThreadId);
  } catch (error) {
    taskError.value = error.message;
  }
};

const shortThreadId = value => {
  const threadId = String(value || '—');
  return threadId.length > 14 ? `${threadId.slice(0, 8)}…${threadId.slice(-4)}` : threadId;
};

watch(() => props.refreshRevision, loadTasks);
onMounted(loadTasks);
</script>
