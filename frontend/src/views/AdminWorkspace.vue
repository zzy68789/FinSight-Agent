<template>
    <main class="workspace-main mx-auto grid max-w-7xl grid-cols-1 gap-6 px-4 py-6 sm:px-6 lg:grid-cols-12 lg:px-8">
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
</template>

<script setup>
import { onActivated, ref } from 'vue';
import { RefreshCwIcon } from 'lucide-vue-next';
import {
  adminDeleteReport,
  adminGetTaskLogs,
  adminListReports,
  adminListTasks,
  adminListUsers,
  adminSystemHealth,
  adminUpdateUserRole,
  adminUpdateUserStatus
} from '../services/api';
import {
  formatDate,
  statusLabel,
  statusStyles,
  stepNameLabel
} from '../modules/presentation';

const emit = defineEmits(['warning']);

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

const loadAdminHealth = async () => {
  const health = await adminSystemHealth();
  adminHealth.value = health.components || {};
};

const loadAdminUsers = async () => {
  adminUsers.value = await adminListUsers({ keyword: adminUserKeyword.value });
};

const loadAdminTasks = async () => {
  adminTasks.value = await adminListTasks({
    status: adminTaskStatus.value,
    ownerId: adminTaskOwnerId.value,
    keyword: adminTaskKeyword.value
  });
};

const loadAdminReports = async () => {
  adminReports.value = await adminListReports({
    ownerId: adminReportOwnerId.value,
    keyword: adminReportKeyword.value
  });
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

const changeAdminUserRole = async (user, role) => {
  try {
    const updated = await adminUpdateUserRole(user.id, role);
    adminUsers.value = adminUsers.value.map((item) => item.id === updated.id ? updated : item);
  } catch (error) {
    emit('warning', error.message);
    await loadAdminUsers();
  }
};

const changeAdminUserStatus = async (user, status) => {
  try {
    const updated = await adminUpdateUserStatus(user.id, status);
    adminUsers.value = adminUsers.value.map((item) => item.id === updated.id ? updated : item);
  } catch (error) {
    emit('warning', error.message);
    await loadAdminUsers();
  }
};

const loadAdminTaskLogs = async (task) => {
  try {
    adminSelectedTask.value = task;
    adminTaskLogs.value = await adminGetTaskLogs(task.id);
  } catch (error) {
    emit('warning', error.message);
  }
};

const deleteAdminReport = async (report) => {
  const confirmed = window.confirm('确定要删除报告 #' + report.id + ' 吗？');
  if (!confirmed) return;

  try {
    await adminDeleteReport(report.id);
    adminReports.value = adminReports.value.filter((item) => item.id !== report.id);
  } catch (error) {
    emit('warning', error.message);
  }
};

onActivated(loadAdminDashboard);
</script>

