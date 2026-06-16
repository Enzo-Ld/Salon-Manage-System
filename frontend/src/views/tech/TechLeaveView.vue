<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { LeaveRecord } from '../../api/types';

const records = ref<LeaveRecord[]>([]);
const saving = ref(false);
const [defaultStartTime, defaultEndTime] = defaultLeaveRange();
const startTimeValue = ref(defaultStartTime);
const endTimeValue = ref(defaultEndTime);
const pager = reactive({
  pageNum: 1,
  pageSize: 6,
});

const form = reactive<LeaveRecord>({
  technicianId: 0,
  startTime: '',
  endTime: '',
  reason: '',
  status: 'PENDING',
});

const leaveShortcuts = [
  {
    text: '10 分钟后',
    value: () => {
      const [start, end] = defaultLeaveRange();
      startTimeValue.value = start;
      endTimeValue.value = end;
    },
  },
  {
    text: '明天全天',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() + 1);
      start.setHours(9, 0, 0, 0);
      const end = new Date();
      end.setDate(end.getDate() + 1);
      end.setHours(18, 0, 0, 0);
      startTimeValue.value = formatDateTime(start);
      endTimeValue.value = formatDateTime(end);
    },
  },
  {
    text: '明天下午',
    value: () => {
      const start = new Date();
      start.setDate(start.getDate() + 1);
      start.setHours(14, 0, 0, 0);
      const end = new Date();
      end.setDate(end.getDate() + 1);
      end.setHours(18, 0, 0, 0);
      startTimeValue.value = formatDateTime(start);
      endTimeValue.value = formatDateTime(end);
    },
  },
];

const pendingCount = computed(() => records.value.filter((item) => item.status === 'PENDING').length);
const approvedCount = computed(() => records.value.filter((item) => item.status === 'APPROVED').length);
const pagedRecords = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return records.value.slice(start, start + pager.pageSize);
});

const leaveDurationText = computed(() => {
  const start = new Date(startTimeValue.value);
  const end = new Date(endTimeValue.value);
  const diff = end.getTime() - start.getTime();

  if (Number.isNaN(diff) || !startTimeValue.value || !endTimeValue.value) {
    return '请选择完整的开始和结束时间';
  }
  if (diff <= 0) {
    return '结束时间必须晚于开始时间';
  }

  const totalHours = Math.floor(diff / (1000 * 60 * 60));
  const totalDays = Math.floor(totalHours / 24);
  const remainHours = totalHours % 24;

  if (totalDays > 0) {
    return `本次请假约 ${totalDays} 天 ${remainHours} 小时`;
  }
  return `本次请假约 ${totalHours} 小时`;
});

function defaultLeaveRange() {
  const start = new Date();
  start.setMinutes(start.getMinutes() + 10, 0, 0);
  const end = new Date(start);
  end.setHours(end.getHours() + 1);
  return [formatDateTime(start), formatDateTime(end)];
}

function formatDateTime(date: Date) {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, '0');
  const day = String(date.getDate()).padStart(2, '0');
  const hours = String(date.getHours()).padStart(2, '0');
  const minutes = String(date.getMinutes()).padStart(2, '0');
  const seconds = String(date.getSeconds()).padStart(2, '0');
  return `${year}-${month}-${day}T${hours}:${minutes}:${seconds}`;
}

function resetForm() {
  const [start, end] = defaultLeaveRange();
  Object.assign(form, {
    technicianId: 0,
    startTime: '',
    endTime: '',
    reason: '',
    status: 'PENDING',
  });
  startTimeValue.value = start;
  endTimeValue.value = end;
}

function applyDuration(hours: number) {
  const start = new Date(startTimeValue.value);
  if (Number.isNaN(start.getTime())) {
    ElMessage.warning('请先选择开始时间');
    return;
  }
  const end = new Date(start);
  end.setHours(end.getHours() + hours);
  endTimeValue.value = formatDateTime(end);
}

async function loadRecords() {
  try {
    const { data } = await api.leaveRecords();
    records.value = data.data;
  } catch {
    records.value = [];
  }
}

async function saveRecord() {
  if (!startTimeValue.value || !endTimeValue.value || !form.reason.trim()) {
    ElMessage.warning('请先选择完整的请假时间并填写请假原因');
    return;
  }

  const startTime = startTimeValue.value;
  const endTime = endTimeValue.value;
  if (new Date(endTime).getTime() <= new Date(startTime).getTime()) {
    ElMessage.warning('结束时间必须晚于开始时间');
    return;
  }

  saving.value = true;
  try {
    await api.saveLeaveRecord({
      startTime,
      endTime,
      reason: form.reason.trim(),
    });
    ElMessage.success('请假申请已提交');
    resetForm();
    await loadRecords();
  } finally {
    saving.value = false;
  }
}

async function cancelRecord(id?: number) {
  if (!id) return;
  await api.cancelLeaveRecord(id);
  ElMessage.success('请假记录已撤回');
  await loadRecords();
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

function statusTagType(status: string) {
  if (status === 'PENDING') return 'warning';
  if (status === 'APPROVED') return 'success';
  if (status === 'REJECTED') return 'danger';
  if (status === 'CANCELLED') return 'info';
  return 'info';
}

function statusLabel(status: string) {
  if (status === 'PENDING') return '待审批';
  if (status === 'APPROVED') return '已通过';
  if (status === 'REJECTED') return '已驳回';
  if (status === 'CANCELLED') return '已撤回';
  return status;
}

onMounted(() => {
  void loadRecords();
});
</script>

<template>
  <div class="page-shell">
    <section class="metric-grid">
      <article class="metric-tile">
        <div class="metric-label">请假总数</div>
        <div class="metric-value">{{ records.length }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">待审批</div>
        <div class="metric-value">{{ pendingCount }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">已通过</div>
        <div class="metric-value">{{ approvedCount }}</div>
      </article>
    </section>

    <section class="panel leave-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">技师请假记录</h2>
          <p class="panel-subtitle">请假申请提交后，需要管理员审批通过，才会联动排班和考勤限制。</p>
        </div>
      </div>

      <div class="leave-layout">
        <article class="request-card">
          <div class="request-card__head">
            <div>
              <h3>新建请假申请</h3>
              <p>使用大号开始时间和结束时间选择器，减少误触，操作更顺手。</p>
            </div>
            <el-tag type="warning" effect="plain">中文大号时间选择</el-tag>
          </div>

          <el-form label-position="top" class="leave-form">
            <div class="picker-grid">
              <el-form-item label="请假开始时间">
                <el-date-picker
                  v-model="startTimeValue"
                  class="leave-single-picker"
                  type="datetime"
                  placeholder="请选择开始时间"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  format="YYYY-MM-DD HH:mm"
                  size="large"
                />
              </el-form-item>

              <el-form-item label="请假结束时间">
                <el-date-picker
                  v-model="endTimeValue"
                  class="leave-single-picker"
                  type="datetime"
                  placeholder="请选择结束时间"
                  value-format="YYYY-MM-DDTHH:mm:ss"
                  format="YYYY-MM-DD HH:mm"
                  size="large"
                />
              </el-form-item>
            </div>

            <div class="shortcut-group">
              <span class="shortcut-label">快捷开始时间</span>
              <div class="toolbar">
                <el-button v-for="shortcut in leaveShortcuts" :key="shortcut.text" plain @click="shortcut.value()">
                  {{ shortcut.text }}
                </el-button>
              </div>
            </div>

            <div class="shortcut-group">
              <span class="shortcut-label">快捷请假时长</span>
              <div class="toolbar">
                <el-button plain @click="applyDuration(2)">2 小时</el-button>
                <el-button plain @click="applyDuration(4)">半天</el-button>
                <el-button plain @click="applyDuration(8)">1 天</el-button>
                <el-button plain @click="applyDuration(24)">24 小时</el-button>
              </div>
            </div>

            <el-form-item label="时间说明">
              <div class="leave-hint">
                <strong>{{ leaveDurationText }}</strong>
                <span>建议先选择开始时间，再通过结束时间或快捷时长完成填写，避免时间过期。</span>
              </div>
            </el-form-item>

            <el-form-item label="请假原因">
              <el-input
                v-model="form.reason"
                type="textarea"
                :rows="5"
                maxlength="120"
                show-word-limit
                placeholder="请填写请假原因，例如：外出培训、个人事务、身体不适等"
              />
            </el-form-item>

            <div class="toolbar">
              <el-button type="warning" :loading="saving" @click="saveRecord">提交请假申请</el-button>
              <el-button plain @click="resetForm">恢复默认时间</el-button>
            </div>
          </el-form>
        </article>

        <article class="record-card">
          <div class="record-card__head">
            <div>
              <h3>请假申请记录</h3>
              <p>可查看审批状态、审批说明以及提交时间，形成完整流程。</p>
            </div>
          </div>

          <div class="record-list">
            <article v-for="item in pagedRecords" :key="item.id" class="record-item">
              <div class="record-item__top">
                <div>
                  <strong>{{ item.startTime }} 至 {{ item.endTime }}</strong>
                  <p>{{ item.reason }}</p>
                </div>
                <el-tag :type="statusTagType(item.status)">{{ statusLabel(item.status) }}</el-tag>
              </div>

              <div class="record-meta">
                <span>审批说明：{{ item.reviewRemark || '暂无' }}</span>
                <span>审批时间：{{ item.reviewedAt || '未审批' }}</span>
                <span>提交时间：{{ item.createdAt || '--' }}</span>
              </div>

              <div class="toolbar" v-if="item.status === 'PENDING' || item.status === 'APPROVED'">
                <el-button size="small" plain type="danger" @click="cancelRecord(item.id)">撤回申请</el-button>
              </div>
            </article>

            <el-empty v-if="records.length === 0" description="当前还没有请假记录" />
          </div>

          <div v-if="records.length > 0" class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="pager.pageNum"
              :page-size="pager.pageSize"
              :page-sizes="[6, 10, 20]"
              :total="records.length"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.leave-panel {
  padding: 26px;
}

.leave-layout {
  display: grid;
  grid-template-columns: minmax(420px, 520px) 1fr;
  gap: 20px;
}

.request-card,
.record-card {
  border-radius: 24px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  background: rgba(255, 250, 243, 0.82);
  padding: 24px;
}

.request-card__head,
.record-card__head,
.record-item__top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.request-card__head h3,
.record-card__head h3 {
  margin: 0;
}

.request-card__head p,
.record-card__head p,
.record-item p {
  margin: 8px 0 0;
  color: var(--muted);
}

.leave-form {
  margin-top: 20px;
}

.picker-grid {
  display: grid;
  grid-template-columns: 1fr;
  gap: 8px;
}

.leave-single-picker {
  width: 100%;
}

.shortcut-group {
  display: grid;
  gap: 10px;
  margin-bottom: 18px;
}

.shortcut-label {
  color: var(--muted);
  font-weight: 600;
}

:deep(.leave-single-picker .el-input__wrapper) {
  min-height: 64px;
  border-radius: 18px;
  font-size: 18px;
}

.leave-hint {
  margin-top: 12px;
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 244, 223, 0.76);
  color: var(--text);
}

.leave-hint span {
  color: var(--muted);
}

.record-list {
  margin-top: 20px;
  display: grid;
  gap: 14px;
}

.record-item {
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  background: rgba(255, 255, 255, 0.7);
}

.record-meta {
  display: grid;
  gap: 8px;
  margin-top: 12px;
  color: var(--muted);
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .leave-layout {
    grid-template-columns: 1fr;
  }
}
</style>
