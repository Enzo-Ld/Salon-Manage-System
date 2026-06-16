<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { AppointmentSummary } from '../../api/types';

const router = useRouter();
const list = ref<AppointmentSummary[]>([]);
const pager = reactive({
  pageNum: 1,
  pageSize: 9,
});

const pagedList = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return list.value.slice(start, start + pager.pageSize);
});

const grouped = computed(() => ({
  pending: pagedList.value.filter((item) => item.status === 'PENDING'),
  serving: pagedList.value.filter((item) => ['APPROVED', 'WAIT_SERVICE', 'IN_SERVICE'].includes(item.status)),
  done: pagedList.value.filter((item) => ['COMPLETED', 'REJECTED', 'CANCELLED'].includes(item.status)),
}));

async function loadAppointments() {
  const { data } = await api.appointments();
  list.value = data.data;
}

async function updateStatus(id: number, status: string, remark: string) {
  await api.updateAppointmentStatus(id, status, remark);
  ElMessage.success('预约状态已更新');
  await loadAppointments();
}

function openChat(item: AppointmentSummary) {
  router.push({
    path: '/chat',
    query: {
      receiverId: item.userId,
      appointmentId: item.id,
      serviceName: item.serviceItemName,
      appointmentTime: `${item.appointmentTime}${item.appointmentEndTime ? ` - ${item.appointmentEndTime}` : ''}`,
      draft: `您好，关于“${item.serviceItemName}”预约，如需确认到店时间、准备事项或调整安排，可以直接在这里沟通。`,
    },
  });
}

function nextActions(item: AppointmentSummary) {
  switch (item.status) {
    case 'PENDING':
      return [
        { label: '确认预约', status: 'APPROVED', remark: '技师审核通过', type: 'warning' as const },
        { label: '拒绝预约', status: 'REJECTED', remark: '技师审核拒绝', type: 'danger' as const },
      ];
    case 'APPROVED':
      return [{ label: '到店待服务', status: 'WAIT_SERVICE', remark: '用户到店，进入待服务', type: 'warning' as const }];
    case 'WAIT_SERVICE':
      return [{ label: '开始服务', status: 'IN_SERVICE', remark: '技师开始服务', type: 'warning' as const }];
    case 'IN_SERVICE':
      return [{ label: '服务完成', status: 'COMPLETED', remark: '服务完成', type: 'success' as const }];
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

onMounted(() => {
  void loadAppointments();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel appointment-board">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">技师预约处理</h2>
          <p class="panel-subtitle">按当前状态推进预约流转，支持一键联系预约用户，所有操作都会写入状态日志。</p>
        </div>
        <el-button type="warning" plain @click="router.push('/chat')">进入在线沟通</el-button>
      </div>

      <div class="board-grid">
        <div class="board-column">
          <h3>待处理</h3>
          <div class="soft-list">
            <div v-for="item in grouped.pending" :key="item.id" class="soft-list-item">
              <strong>{{ item.serviceItemName }}</strong>
              <p>预约时间：{{ item.appointmentTime }}<span v-if="item.appointmentEndTime"> - {{ item.appointmentEndTime }}</span></p>
              <p>预约用户：{{ item.username }}</p>
              <div class="toolbar">
                <el-button
                  v-for="action in nextActions(item)"
                  :key="action.status"
                  size="small"
                  :type="action.type"
                  @click="updateStatus(item.id, action.status, action.remark)"
                >
                  {{ action.label }}
                </el-button>
                <el-button size="small" plain @click="openChat(item)">联系用户</el-button>
              </div>
            </div>
            <el-empty v-if="grouped.pending.length === 0" description="当前页没有待处理预约" />
          </div>
        </div>

        <div class="board-column">
          <h3>服务进行中</h3>
          <div class="soft-list">
            <div v-for="item in grouped.serving" :key="item.id" class="soft-list-item">
              <strong>{{ item.serviceItemName }} · {{ item.statusLabel }}</strong>
              <p>预约时间：{{ item.appointmentTime }}<span v-if="item.appointmentEndTime"> - {{ item.appointmentEndTime }}</span></p>
              <p>{{ item.latestRemark || '等待下一步处理' }}</p>
              <div class="toolbar">
                <el-button
                  v-for="action in nextActions(item)"
                  :key="action.status"
                  size="small"
                  :type="action.type"
                  @click="updateStatus(item.id, action.status, action.remark)"
                >
                  {{ action.label }}
                </el-button>
                <el-button size="small" plain @click="openChat(item)">联系用户</el-button>
              </div>
            </div>
            <el-empty v-if="grouped.serving.length === 0" description="当前页没有进行中的预约" />
          </div>
        </div>

        <div class="board-column">
          <h3>历史记录</h3>
          <div class="soft-list">
            <div v-for="item in grouped.done" :key="item.id" class="soft-list-item">
              <strong>{{ item.serviceItemName }} · {{ item.statusLabel }}</strong>
              <p>预约时间：{{ item.appointmentTime }}</p>
              <p>预约用户：{{ item.username }}</p>
              <div class="toolbar">
                <el-button size="small" plain @click="openChat(item)">继续沟通</el-button>
              </div>
            </div>
            <el-empty v-if="grouped.done.length === 0" description="当前页没有历史预约记录" />
          </div>
        </div>
      </div>

      <div class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pager.pageNum"
          :page-size="pager.pageSize"
          :page-sizes="[9, 15, 30]"
          :total="list.length"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.appointment-board {
  padding: 26px;
}

.board-grid {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 18px;
}

.board-column h3 {
  margin: 0 0 14px;
}

.soft-list-item p {
  margin: 8px 0 0;
  color: var(--muted);
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1100px) {
  .board-grid {
    grid-template-columns: 1fr;
  }
}
</style>
