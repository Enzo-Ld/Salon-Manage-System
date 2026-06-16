<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { AppointmentDetail, AppointmentSummary } from '../../api/types';

const appointments = ref<AppointmentSummary[]>([]);
const detail = ref<AppointmentDetail | null>(null);
const drawerVisible = ref(false);
const loading = ref(false);

const pager = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});
const filters = reactive({
  keyword: '',
  status: '',
  payStatus: '',
  dateRange: [] as string[],
});

const filteredAppointments = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return appointments.value.filter((item) => {
    const matchKeyword = !keyword || [
      item.id,
      item.username,
      item.serviceItemName,
      item.technicianName,
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchStatus = !filters.status || item.status === filters.status;
    const matchPayStatus = !filters.payStatus || item.payStatus === filters.payStatus;
    const day = item.appointmentTime?.slice(0, 10) || '';
    const matchDate = filters.dateRange.length !== 2 || (day >= filters.dateRange[0] && day <= filters.dateRange[1]);
    return matchKeyword && matchStatus && matchPayStatus && matchDate;
  });
});

const pagedAppointments = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredAppointments.value.slice(start, start + pager.pageSize);
});

async function loadAppointments() {
  loading.value = true;
  try {
    const { data } = await api.appointments();
    appointments.value = data.data;
    pager.total = data.data.length;
  } finally {
    loading.value = false;
  }
}

async function showDetail(id: number) {
  const { data } = await api.appointmentDetail(id);
  detail.value = data.data;
  drawerVisible.value = true;
}

async function autoAssign(id: number) {
  await api.autoAssign(id);
  ElMessage.success('系统已完成自动分配');
  await loadAppointments();
}

async function updateStatus(id: number, status: string, remark: string) {
  await api.updateAppointmentStatus(id, status, remark);
  ElMessage.success('预约状态已更新');
  await loadAppointments();
}

function actions(item: AppointmentSummary) {
  switch (item.status) {
    case 'PENDING':
      return [
        { label: '自动分配', type: 'info' as const, action: () => autoAssign(item.id) },
        { label: '审核通过', type: 'warning' as const, action: () => updateStatus(item.id, 'APPROVED', '管理员审核通过') },
        { label: '审核拒绝', type: 'danger' as const, action: () => updateStatus(item.id, 'REJECTED', '管理员审核拒绝') },
      ];
    case 'APPROVED':
      return [{ label: '进入待服务', type: 'warning' as const, action: () => updateStatus(item.id, 'WAIT_SERVICE', '管理员确认待服务') }];
    case 'WAIT_SERVICE':
      return [{ label: '开始服务', type: 'warning' as const, action: () => updateStatus(item.id, 'IN_SERVICE', '管理员更新为服务中') }];
    case 'IN_SERVICE':
      return [{ label: '完成服务', type: 'success' as const, action: () => updateStatus(item.id, 'COMPLETED', '管理员确认服务完成') }];
    default:
      return [];
  }
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

function handleSearch() {
  pager.pageNum = 1;
}

function resetFilters() {
  Object.assign(filters, { keyword: '', status: '', payStatus: '', dateRange: [] });
  pager.pageNum = 1;
}

onMounted(() => {
  void loadAppointments();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel appointment-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">预约管理</h2>
          <p class="panel-subtitle">管理员可查看全部预约、自动分配技师并推进状态流转。</p>
        </div>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="filters.keyword" placeholder="预约号 / 用户 / 服务 / 技师" clearable @keyup.enter="handleSearch" />
        <el-select v-model="filters.status" placeholder="预约状态" clearable @change="handleSearch">
          <el-option label="待支付" value="CREATED" />
          <el-option label="待确认" value="PENDING" />
          <el-option label="已确认" value="APPROVED" />
          <el-option label="待服务" value="WAIT_SERVICE" />
          <el-option label="服务中" value="IN_SERVICE" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="已拒绝" value="REJECTED" />
        </el-select>
        <el-select v-model="filters.payStatus" placeholder="支付状态" clearable @change="handleSearch">
          <el-option label="未支付" value="UNPAID" />
          <el-option label="已支付" value="PAID" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
        <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" @change="handleSearch" />
        <el-button plain @click="handleSearch">查询</el-button>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="pagedAppointments" stripe>
        <el-table-column prop="id" label="预约号" width="90" />
        <el-table-column prop="username" label="用户" min-width="110" />
        <el-table-column prop="serviceItemName" label="服务项目" min-width="150" />
        <el-table-column prop="technicianName" label="技师" min-width="120" />
        <el-table-column prop="appointmentTime" label="预约时间" min-width="170" />
        <el-table-column prop="statusLabel" label="状态" min-width="100" />
        <el-table-column prop="assignModeLabel" label="分配方式" min-width="100" />
        <el-table-column label="操作" min-width="340" fixed="right">
          <template #default="{ row }">
            <div class="toolbar">
              <el-button size="small" @click="showDetail(row.id)">详情</el-button>
              <el-button
                v-for="item in actions(row)"
                :key="item.label"
                size="small"
                :type="item.type"
                plain
                @click="item.action()"
              >
                {{ item.label }}
              </el-button>
            </div>
          </template>
        </el-table-column>
      </el-table>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pager.pageNum"
          :page-size="pager.pageSize"
          :page-sizes="[10, 20, 50]"
          :total="filteredAppointments.length"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="预约详情" size="42%">
      <div v-if="detail" class="soft-list">
        <div class="soft-list-item"><strong>用户：</strong>{{ detail.username }}</div>
        <div class="soft-list-item"><strong>服务：</strong>{{ detail.serviceItemName }}</div>
        <div class="soft-list-item"><strong>技师：</strong>{{ detail.technicianName }}</div>
        <div class="soft-list-item"><strong>状态：</strong>{{ detail.statusLabel }}</div>
        <div class="soft-list-item"><strong>备注：</strong>{{ detail.latestRemark || '暂无' }}</div>
        <div class="soft-list-item">
          <strong>状态日志</strong>
          <el-timeline>
            <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="log.createdAt">
              {{ log.operatorName }}：{{ log.fromStatusLabel || '创建' }} -> {{ log.toStatusLabel }}，{{ log.remark }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>
  </div>
</template>

<style scoped>
.appointment-panel {
  padding: 26px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.filter-bar {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-bar .el-input,
.filter-bar .el-select {
  width: 210px;
}
</style>
