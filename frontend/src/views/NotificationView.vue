<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../api/modules';
import type { SystemNotification } from '../api/types';
import { useNotificationStore } from '../store/notifications';
import { normalizePagePayload } from '../utils/page';

const router = useRouter();
const notificationStore = useNotificationStore();

const loading = ref(false);
const notifications = ref<SystemNotification[]>([]);
const total = ref(0);
const pages = ref(0);
const allNotificationTotal = ref(0);

type NotificationTab = 'UNREAD' | 'READ' | '';

const filters = reactive({
  readStatus: 'UNREAD' as NotificationTab,
  keyword: '',
  pageNum: 1,
  pageSize: 10,
});

const stats = computed(() => ({
  total: allNotificationTotal.value,
  unread: notificationStore.unreadCount,
  read: Math.max(allNotificationTotal.value - notificationStore.unreadCount, 0),
}));

const emptyDescription = computed(() => {
  if (filters.readStatus === 'UNREAD') {
    return '暂无未读消息';
  }
  if (filters.readStatus === 'READ') {
    return '暂无已读消息';
  }
  return '暂无站内消息';
});

const showMarkAllRead = computed(() => filters.readStatus !== 'READ');
const showClearRead = computed(() => filters.readStatus !== 'UNREAD');

function handleSearch() {
  filters.pageNum = 1;
  void loadNotifications(1);
}

function resetKeyword() {
  filters.keyword = '';
  handleSearch();
}

function selectStatus(readStatus: NotificationTab) {
  if (filters.readStatus === readStatus) {
    return;
  }
  filters.readStatus = readStatus;
  filters.pageNum = 1;
  void loadNotifications(1);
}

async function loadNotificationStats() {
  const { data } = await api.notifications({
    pageNum: 1,
    pageSize: 1,
    readStatus: '',
  });
  const pageData = normalizePagePayload<SystemNotification>(data.data, 1, 1);
  allNotificationTotal.value = pageData.total;
  await notificationStore.refreshUnreadCount();
}

async function loadNotifications(targetPage = filters.pageNum) {
  loading.value = true;
  try {
    const { data } = await api.notifications({
      pageNum: targetPage,
      pageSize: filters.pageSize,
      readStatus: filters.readStatus,
      keyword: filters.keyword.trim() || undefined,
    });
    const pageData = normalizePagePayload<SystemNotification>(data.data, targetPage, filters.pageSize);
    notifications.value = pageData.records;
    total.value = pageData.total;
    pages.value = pageData.pages;
    filters.pageNum = pageData.pageNum;
    await loadNotificationStats();

    if (pages.value > 0 && filters.pageNum > pages.value) {
      filters.pageNum = pages.value;
      await loadNotifications(filters.pageNum);
    }
  } catch {
    notifications.value = [];
    total.value = 0;
    pages.value = 0;
    allNotificationTotal.value = 0;
    notificationStore.unreadCount = 0;
  } finally {
    loading.value = false;
  }
}

async function markRead(row: SystemNotification) {
  if (row.readStatus === 'READ') return;
  await api.markNotificationRead(row.id);
  ElMessage.success('已标记为已读');
  await loadNotifications(filters.pageNum);
}

async function markAllRead() {
  await api.markAllNotificationsRead();
  ElMessage.success('全部消息已标记为已读');
  await loadNotifications(filters.pageNum);
}

async function clearRead() {
  try {
    await ElMessageBox.confirm('确认清理全部已读站内消息吗？该操作不可撤销。', '清理已读', {
      type: 'warning',
      confirmButtonText: '确认清理',
      cancelButtonText: '取消',
    });
    await api.clearReadNotifications();
    ElMessage.success('已清理全部已读消息');
    const fallbackPage = total.value > 0 && notifications.value.length === 0 && filters.pageNum > 1
      ? filters.pageNum - 1
      : filters.pageNum;
    await loadNotifications(fallbackPage);
  } catch {
    // noop
  }
}

async function openNotification(row: SystemNotification) {
  if (row.readStatus === 'UNREAD') {
    await markRead(row);
  }
  if (row.targetPath) {
    router.push(row.targetPath);
  }
}

function tagType(status: string) {
  return status === 'UNREAD' ? 'warning' : 'info';
}

function statusLabel(status: string) {
  return status === 'UNREAD' ? '未读' : '已读';
}

function handlePageChange(pageNum: number) {
  void loadNotifications(pageNum);
}

function handlePageSizeChange(pageSize: number) {
  filters.pageSize = pageSize;
  filters.pageNum = 1;
  void loadNotifications(1);
}

onMounted(() => {
  void loadNotifications();
});
</script>

<template>
  <div class="page-shell">
    <section class="metric-grid">
      <article
        class="metric-tile notification-filter-card"
        :class="{ active: filters.readStatus === '' }"
        role="button"
        tabindex="0"
        @click="selectStatus('')"
        @keyup.enter="selectStatus('')"
      >
        <div class="metric-label">消息总数</div>
        <div class="metric-value">{{ stats.total }}</div>
      </article>
      <article
        class="metric-tile notification-filter-card"
        :class="{ active: filters.readStatus === 'UNREAD' }"
        role="button"
        tabindex="0"
        @click="selectStatus('UNREAD')"
        @keyup.enter="selectStatus('UNREAD')"
      >
        <div class="metric-label">未读消息</div>
        <div class="metric-value">{{ stats.unread }}</div>
      </article>
      <article
        class="metric-tile notification-filter-card"
        :class="{ active: filters.readStatus === 'READ' }"
        role="button"
        tabindex="0"
        @click="selectStatus('READ')"
        @keyup.enter="selectStatus('READ')"
      >
        <div class="metric-label">已读消息</div>
        <div class="metric-value">{{ stats.read }}</div>
      </article>
    </section>

    <section class="panel notification-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">站内消息中心</h2>
          <p class="panel-subtitle">请假审批、预约状态、商城订单和后台通知都会集中展示，并支持分页查看和清理已读。</p>
        </div>
        <div class="toolbar">
          <el-button plain @click="loadNotifications()">刷新列表</el-button>
          <el-button v-if="showClearRead" plain @click="clearRead">一键清理已读</el-button>
          <el-button v-if="showMarkAllRead" type="warning" @click="markAllRead">全部设为已读</el-button>
        </div>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="filters.keyword" placeholder="搜索消息标题 / 内容" clearable @keyup.enter="handleSearch" />
        <el-button plain @click="handleSearch">查询</el-button>
        <el-button text @click="resetKeyword">重置</el-button>
      </div>

      <div class="soft-list">
        <div v-for="item in notifications" :key="item.id" class="soft-list-item notification-item">
          <div class="notification-main">
            <div class="notification-head">
              <strong>{{ item.title }}</strong>
              <el-tag :type="tagType(item.readStatus)">{{ statusLabel(item.readStatus) }}</el-tag>
            </div>
            <p>{{ item.content }}</p>
            <small>{{ item.createdAt }}</small>
          </div>
          <div class="toolbar">
            <el-button v-if="item.readStatus === 'UNREAD'" size="small" plain @click="markRead(item)">标记已读</el-button>
            <el-button size="small" type="warning" @click="openNotification(item)">
              {{ item.targetPath ? '打开详情' : '知道了' }}
            </el-button>
          </div>
        </div>
        <el-empty v-if="notifications.length === 0" :description="emptyDescription" />
      </div>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="filters.pageNum"
          :page-size="filters.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.notification-panel {
  padding: 26px;
}

.notification-filter-card {
  cursor: pointer;
  position: relative;
  border: 1px solid transparent;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.notification-filter-card:hover,
.notification-filter-card:focus-visible {
  transform: translateY(-2px);
  border-color: rgba(214, 147, 42, 0.42);
  box-shadow: 0 18px 50px rgba(108, 71, 15, 0.12);
  outline: none;
}

.notification-filter-card.active {
  border-color: rgba(214, 147, 42, 0.72);
  box-shadow: inset 0 0 0 1px rgba(214, 147, 42, 0.36), 0 18px 50px rgba(108, 71, 15, 0.12);
}

.notification-filter-card.active::after {
  content: "当前列表";
  position: absolute;
  right: 18px;
  top: 16px;
  padding: 4px 9px;
  border-radius: 999px;
  color: #9a5a00;
  background: rgba(255, 177, 58, 0.16);
  font-size: 12px;
  font-weight: 700;
}

.notification-item {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
}

.filter-bar {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: 280px;
}

.notification-main {
  display: grid;
  gap: 8px;
}

.notification-head {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
}

.notification-main p,
.notification-main small {
  margin: 0;
  color: var(--muted);
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .notification-item {
    flex-direction: column;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}
</style>
