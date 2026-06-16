<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { LeaveConflictPreview, LeaveRecord, Technician } from '../../api/types';

const loading = ref(false);
const reviewDialogVisible = ref(false);
const reviewSubmitting = ref(false);
const leaveRecords = ref<LeaveRecord[]>([]);
const technicians = ref<Technician[]>([]);
const currentRecord = ref<LeaveRecord | null>(null);
const conflictPreview = ref<LeaveConflictPreview | null>(null);
const conflictActions = ref<Record<number, { action: 'REASSIGN' | 'CANCEL'; targetTechnicianId?: number | null }>>({});

const filters = reactive({
  status: 'ALL',
  technicianId: 0,
});

const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});

const reviewForm = reactive({
  action: 'APPROVED' as 'APPROVED' | 'REJECTED',
  remark: '',
});

const technicianMap = computed(() =>
  technicians.value.reduce<Record<number, Technician>>((acc, item) => {
    acc[item.id] = item;
    return acc;
  }, {}),
);

const pendingRecords = computed(() => leaveRecords.value.filter((item) => item.status === 'PENDING'));

const filteredRecords = computed(() =>
  leaveRecords.value.filter((item) => {
    const matchStatus = filters.status === 'ALL' || item.status === filters.status;
    const matchTechnician = !filters.technicianId || item.technicianId === filters.technicianId;
    return matchStatus && matchTechnician;
  }),
);

const pagedRecords = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredRecords.value.slice(start, start + pager.pageSize);
});

const stats = computed(() => ({
  total: leaveRecords.value.length,
  pending: leaveRecords.value.filter((item) => item.status === 'PENDING').length,
  approved: leaveRecords.value.filter((item) => item.status === 'APPROVED').length,
  rejected: leaveRecords.value.filter((item) => item.status === 'REJECTED').length,
}));

function technicianName(technicianId: number) {
  return technicianMap.value[technicianId]?.name || `技师 #${technicianId}`;
}

function statusLabel(status: string) {
  switch (status) {
    case 'PENDING':
      return '待审批';
    case 'APPROVED':
      return '已通过';
    case 'REJECTED':
      return '已驳回';
    case 'CANCELLED':
      return '已撤回';
    default:
      return status;
  }
}

function statusTagType(status: string) {
  switch (status) {
    case 'PENDING':
      return 'warning';
    case 'APPROVED':
      return 'success';
    case 'REJECTED':
      return 'danger';
    default:
      return 'info';
  }
}

async function loadData() {
  loading.value = true;
  try {
    const [leaveRes, technicianRes] = await Promise.all([api.leaveRecords(), api.technicians()]);
    leaveRecords.value = leaveRes.data.data;
    technicians.value = technicianRes.data.data;
  } finally {
    loading.value = false;
  }
}

function handleFilterChange() {
  pager.pageNum = 1;
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

async function openReview(record: LeaveRecord, action: 'APPROVED' | 'REJECTED') {
  currentRecord.value = record;
  reviewForm.action = action;
  reviewForm.remark = '';
  conflictPreview.value = null;
  conflictActions.value = {};
  if (action === 'APPROVED' && record.id) {
    const { data } = await api.leaveConflicts(record.id);
    conflictPreview.value = data.data;
    conflictActions.value = Object.fromEntries(
      data.data.conflicts.map((item) => [
        item.appointmentId,
        {
          action: item.candidates.length > 0 ? 'REASSIGN' : 'CANCEL',
          targetTechnicianId: item.candidates[0]?.technicianId ?? null,
        },
      ]),
    );
  }
  reviewDialogVisible.value = true;
}

async function submitReview() {
  if (!currentRecord.value?.id) return;
  if (reviewForm.action === 'REJECTED' && !reviewForm.remark.trim()) {
    ElMessage.warning('驳回申请时请填写审批说明');
    return;
  }

  reviewSubmitting.value = true;
  try {
    if (reviewForm.action === 'APPROVED') {
      if (conflictPreview.value?.hasConflicts) {
        const items = conflictPreview.value.conflicts.map((item) => ({
          appointmentId: item.appointmentId,
          action: conflictActions.value[item.appointmentId]?.action || 'CANCEL',
          targetTechnicianId: conflictActions.value[item.appointmentId]?.targetTechnicianId || null,
        }));
        if (items.some((item) => item.action === 'REASSIGN' && !item.targetTechnicianId)) {
          ElMessage.warning('请为所有转派预约选择目标技师，或改为取消预约');
          return;
        }
        await api.resolveAndApproveLeaveRecord(currentRecord.value.id, {
          remark: reviewForm.remark.trim(),
          items,
        });
      } else {
        await api.approveLeaveRecord(currentRecord.value.id, reviewForm.remark.trim());
      }
      ElMessage.success('请假申请已审批通过');
    } else {
      await api.rejectLeaveRecord(currentRecord.value.id, reviewForm.remark.trim());
      ElMessage.success('请假申请已驳回');
    }
    reviewDialogVisible.value = false;
    await loadData();
  } finally {
    reviewSubmitting.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section class="metric-grid">
      <article class="metric-tile">
        <div class="metric-label">请假总数</div>
        <div class="metric-value">{{ stats.total }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">待审批</div>
        <div class="metric-value">{{ stats.pending }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">已通过</div>
        <div class="metric-value">{{ stats.approved }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">已驳回</div>
        <div class="metric-value">{{ stats.rejected }}</div>
      </article>
    </section>

    <section class="panel leave-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">管理员请假审批</h2>
          <p class="panel-subtitle">集中处理技师提交的请假申请，并将审批结果同步回写到请假记录中。</p>
        </div>
      </div>

      <div class="leave-layout">
        <article class="queue-card">
          <div class="queue-card__head">
            <div>
              <h3>待审批队列</h3>
              <p>优先处理新提交的请假申请，审批后会联动排班与考勤逻辑。</p>
            </div>
            <el-tag type="warning" effect="plain">{{ pendingRecords.length }} 条待处理</el-tag>
          </div>

          <div class="queue-list" v-if="pendingRecords.length > 0">
            <article v-for="item in pendingRecords" :key="item.id" class="queue-item">
              <div class="queue-item__top">
                <div>
                  <strong>{{ technicianName(item.technicianId) }}</strong>
                  <p>{{ item.startTime }} 至 {{ item.endTime }}</p>
                </div>
                <el-tag type="warning">待审批</el-tag>
              </div>
              <p class="queue-reason">请假原因：{{ item.reason }}</p>
              <div class="toolbar">
                <el-button type="warning" @click="openReview(item, 'APPROVED')">审批通过</el-button>
                <el-button plain type="danger" @click="openReview(item, 'REJECTED')">驳回申请</el-button>
              </div>
            </article>
          </div>
          <el-empty v-else description="当前没有待审批的请假申请" />
        </article>

        <article class="record-card">
          <div class="record-card__head">
            <div>
              <h3>请假记录总览</h3>
              <p>支持按审批状态和技师筛选，方便查看历史审批轨迹与结果。</p>
            </div>
          </div>

          <div class="filter-bar">
            <el-select v-model="filters.status" @change="handleFilterChange">
              <el-option label="全部状态" value="ALL" />
              <el-option label="待审批" value="PENDING" />
              <el-option label="已通过" value="APPROVED" />
              <el-option label="已驳回" value="REJECTED" />
              <el-option label="已撤回" value="CANCELLED" />
            </el-select>
            <el-select v-model="filters.technicianId" clearable placeholder="筛选技师" @change="handleFilterChange">
              <el-option v-for="item in technicians" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </div>

          <el-table :data="pagedRecords" stripe>
            <el-table-column label="技师" min-width="120">
              <template #default="{ row }">{{ technicianName(row.technicianId) }}</template>
            </el-table-column>
            <el-table-column prop="startTime" label="开始时间" min-width="170" />
            <el-table-column prop="endTime" label="结束时间" min-width="170" />
            <el-table-column prop="reason" label="请假原因" min-width="220" show-overflow-tooltip />
            <el-table-column label="审批状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusTagType(row.status)">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="reviewRemark" label="审批说明" min-width="220">
              <template #default="{ row }">{{ row.reviewRemark || '暂无' }}</template>
            </el-table-column>
            <el-table-column prop="reviewedAt" label="审批时间" min-width="170">
              <template #default="{ row }">{{ row.reviewedAt || '未审批' }}</template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="pager.pageNum"
              :page-size="pager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="filteredRecords.length"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </article>
      </div>
    </section>

    <el-dialog
      v-model="reviewDialogVisible"
      :title="reviewForm.action === 'APPROVED' ? '审批通过请假申请' : '驳回请假申请'"
      width="560px"
    >
      <div v-if="currentRecord" class="dialog-copy">
        <p><strong>技师姓名：</strong>{{ technicianName(currentRecord.technicianId) }}</p>
        <p><strong>请假时间：</strong>{{ currentRecord.startTime }} 至 {{ currentRecord.endTime }}</p>
        <p><strong>请假原因：</strong>{{ currentRecord.reason }}</p>
      </div>

      <div v-if="reviewForm.action === 'APPROVED' && conflictPreview?.hasConflicts" class="conflict-box">
        <h4>请假时段已有预约，请先处理后再通过</h4>
        <article v-for="item in conflictPreview.conflicts" :key="item.appointmentId" class="conflict-item">
          <div>
            <strong>#{{ item.appointmentId }} {{ item.serviceItemName }}</strong>
            <p>{{ item.username }}，{{ item.appointmentTime }} 至 {{ item.appointmentEndTime }}</p>
          </div>
          <el-radio-group v-model="conflictActions[item.appointmentId].action">
            <el-radio-button label="REASSIGN" :disabled="item.candidates.length === 0">转派</el-radio-button>
            <el-radio-button label="CANCEL">取消并退款</el-radio-button>
          </el-radio-group>
          <el-select
            v-if="conflictActions[item.appointmentId]?.action === 'REASSIGN'"
            v-model="conflictActions[item.appointmentId].targetTechnicianId"
            placeholder="选择可转派技师"
            filterable
          >
            <el-option
              v-for="candidate in item.candidates"
              :key="candidate.technicianId"
              :label="candidate.technicianName"
              :value="candidate.technicianId"
            >
              <div class="technician-option">
                <strong>{{ candidate.technicianName }}</strong>
                <span>{{ candidate.reason || '可承接该预约时段' }}</span>
              </div>
            </el-option>
          </el-select>
          <el-tag v-if="conflictActions[item.appointmentId]?.action === 'REASSIGN' && item.candidates.length === 0" type="danger">
            当前没有可转派技师，请选择取消预约或驳回请假
          </el-tag>
        </article>
      </div>

      <el-form label-position="top">
        <el-form-item :label="reviewForm.action === 'APPROVED' ? '审批备注（可选）' : '驳回原因'">
          <el-input
            v-model="reviewForm.remark"
            type="textarea"
            :rows="4"
            maxlength="200"
            show-word-limit
            :placeholder="reviewForm.action === 'APPROVED' ? '可填写排班调整建议、注意事项或额外提醒' : '请输入驳回原因，便于技师了解审批结果'"
          />
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="toolbar">
          <el-button @click="reviewDialogVisible = false">取消</el-button>
          <el-button
            :type="reviewForm.action === 'APPROVED' ? 'warning' : 'danger'"
            :loading="reviewSubmitting"
            @click="submitReview"
          >
            提交审批
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.conflict-box {
  display: grid;
  gap: 12px;
  margin: 16px 0;
  padding: 14px;
  border-radius: 16px;
  border: 1px solid rgba(225, 132, 28, 0.28);
  background: rgba(255, 247, 235, 0.86);
}

.conflict-box h4,
.conflict-item p {
  margin: 0;
}

.conflict-item {
  display: grid;
  gap: 10px;
  padding: 12px;
  border-radius: 14px;
  background: rgba(255, 255, 255, 0.72);
}

.technician-option {
  display: grid;
  line-height: 1.35;
}

.technician-option span {
  color: var(--muted);
  font-size: 12px;
}
</style>
