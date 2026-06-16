<script setup lang="ts">
// @ts-nocheck
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { AppointmentDetail, AppointmentSummary, PaymentCapability, PaymentCreateResult, PaymentStatus } from '../../api/types';
import PaymentCashierDialog from '../../components/PaymentCashierDialog.vue';

const route = useRoute();
const router = useRouter();

const list = ref<AppointmentSummary[]>([]);
const detail = ref<AppointmentDetail | null>(null);
const drawerVisible = ref(false);
const commentVisible = ref(false);
const payDialogVisible = ref(false);
const cashierVisible = ref(false);
const paying = ref(false);
const paymentActionLoading = ref(false);
const autopayHandled = ref(false);
const paymentCreate = ref<PaymentCreateResult | null>(null);
const paymentStatus = ref<PaymentStatus | null>(null);
const paymentCapabilities = ref<PaymentCapability | null>(null);
const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});
const filters = reactive({
  keyword: '',
  status: '',
  payStatus: '',
});

const payForm = reactive<{ payMethod: 'BALANCE' | 'WECHAT' | 'ALIPAY' }>({
  payMethod: 'BALANCE',
});

const onlinePayOptions = computed(() => {
  const channels = paymentCapabilities.value?.channels?.filter((item) => item.enabled);
  if (channels?.length) {
    return channels.map((item) => ({
      value: item.channel,
      label: item.label,
    }));
  }
  const fallback = paymentCapabilities.value?.supportedChannels?.length
    ? paymentCapabilities.value.supportedChannels
    : ['WECHAT', 'ALIPAY'];
  return fallback.map((value) => ({
    value,
    label: value === 'WECHAT' ? '微信支付' : '支付宝',
  }));
});
const supportedOnlineChannelSet = computed(() => new Set(onlinePayOptions.value.map((item) => item.value)));

const commentForm = reactive({
  appointmentId: 0,
  rating: 5,
  content: '',
});

const filteredList = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return list.value.filter((item) => {
    const matchKeyword = !keyword || [
      item.id,
      item.serviceItemName,
      item.technicianName,
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchStatus = !filters.status || item.status === filters.status;
    const matchPayStatus = !filters.payStatus || item.payStatus === filters.payStatus;
    return matchKeyword && matchStatus && matchPayStatus;
  });
});

const pagedList = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredList.value.slice(start, start + pager.pageSize);
});

async function loadAppointments() {
  const { data } = await api.appointments();
  list.value = data.data;
  await maybeHandleAutopay();
}

async function loadPaymentCapabilities() {
  try {
    const { data } = await api.paymentCapabilities();
    paymentCapabilities.value = data.data;
  } catch {
    paymentCapabilities.value = null;
  }
}

async function maybeHandleAutopay() {
  if (autopayHandled.value) return;
  const id = Number(route.query.autopay ?? 0);
  if (!id) return;
  const item = list.value.find((row) => row.id === id);
  if (!item || !item.canPay) return;
  autopayHandled.value = true;
  await openDetail(id, true);
  payDialogVisible.value = true;
  await router.replace({ path: route.path, query: { ...route.query, autopay: undefined } });
}

async function openDetail(id: number, keepDrawer = true) {
  const { data } = await api.appointmentDetail(id);
  detail.value = data.data;
  drawerVisible.value = keepDrawer;
}

async function refreshCurrentDetail() {
  if (!detail.value) return;
  await openDetail(detail.value.id, drawerVisible.value);
}

async function cancelAppointment(item: AppointmentSummary | AppointmentDetail) {
  try {
    await ElMessageBox.confirm(
      item.payStatus === 'PAID' ? '确认取消该预约并按原支付方式处理退款吗？' : '确认取消该预约吗？',
      '取消预约',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '再想想',
      },
    );
    await api.cancelAppointment(item.id);
    ElMessage.success(item.payStatus === 'PAID' ? '预约已取消，退款将按原支付方式处理' : '预约已取消');
    drawerVisible.value = false;
    await loadAppointments();
  } catch {
    // noop
  }
}

function openCommentDialog(item: AppointmentSummary) {
  commentForm.appointmentId = item.id;
  commentForm.rating = 5;
  commentForm.content = '';
  commentVisible.value = true;
}

async function submitComment() {
  await api.commentAppointment(commentForm.appointmentId, commentForm.rating, commentForm.content);
  ElMessage.success('评价提交成功');
  commentVisible.value = false;
  await loadAppointments();
  await refreshCurrentDetail();
}

function canChat(item: AppointmentSummary | AppointmentDetail) {
  return Boolean(item.technicianUserId) && item.payStatus === 'PAID' && !['REJECTED', 'CANCELLED'].includes(item.status);
}

function openChat(item: AppointmentSummary | AppointmentDetail) {
  if (!item.technicianUserId) {
    ElMessage.warning('当前暂无可聊天的技师');
    return;
  }

  const appointmentRange = item.appointmentEndTime
    ? `${item.appointmentTime} - ${item.appointmentEndTime}`
    : item.appointmentTime;

  router.push({
    path: '/chat',
    query: {
      receiverId: String(item.technicianUserId),
      appointmentId: String(item.id),
      serviceName: item.serviceItemName,
      appointmentTime: appointmentRange,
      technicianName: item.technicianName,
      draft: `你好，我想确认一下 ${appointmentRange} 的 ${item.serviceItemName} 预约。`,
    },
  });
}

function openPayDialog(item: AppointmentSummary | AppointmentDetail) {
  if (!item.canPay) return;
  payForm.payMethod = 'BALANCE';
  detail.value = 'logs' in item ? item : detail.value;
  payDialogVisible.value = true;
}

async function submitPayment() {
  if (!detail.value) return;
  paying.value = true;
  try {
    if (payForm.payMethod === 'BALANCE') {
      const { data } = await api.payAppointment(detail.value.id, 'BALANCE');
      detail.value = data.data;
      ElMessage.success('余额支付成功，等待技师确认预约。');
      payDialogVisible.value = false;
      await loadAppointments();
      return;
    }
    if (!supportedOnlineChannelSet.value.has(payForm.payMethod)) {
      ElMessage.warning('当前选择的支付方式暂不可用，请切换可用渠道。');
      payForm.payMethod = onlinePayOptions.value[0]?.value ?? 'ALIPAY';
      return;
    }

    const { data } = await api.createAppointmentPayment(detail.value.id, payForm.payMethod);
    paymentCreate.value = data.data;
    payDialogVisible.value = false;
    cashierVisible.value = true;
    await refreshPaymentStatus();
  } finally {
    paying.value = false;
  }
}

async function refreshPaymentStatus() {
  if (!paymentCreate.value) return;
  paymentActionLoading.value = true;
  try {
    const { data } = await api.paymentStatus(paymentCreate.value.paymentNo);
    paymentStatus.value = data.data;
  } finally {
    paymentActionLoading.value = false;
  }
}

async function markPaymentSuccess() {
  if (!paymentCreate.value) return;
  paymentActionLoading.value = true;
  try {
    const { data } = await api.completePayment(paymentCreate.value.paymentNo);
    paymentStatus.value = data.data;
    if (data.data.status === 'FAILED') {
      ElMessage.warning(data.data.notifyContent || '支付尚未完成，请重新选择可预约的时间段。');
    } else {
      ElMessage.success('支付已确认');
      cashierVisible.value = false;
    }
    await loadAppointments();
    await refreshCurrentDetail();
  } finally {
    paymentActionLoading.value = false;
  }
}

async function markPaymentFail() {
  if (!paymentCreate.value) return;
  paymentActionLoading.value = true;
  try {
    const { data } = await api.cancelPayment(paymentCreate.value.paymentNo);
    paymentStatus.value = data.data;
    ElMessage.warning('支付已取消');
    await loadAppointments();
    await refreshCurrentDetail();
  } finally {
    paymentActionLoading.value = false;
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
  Object.assign(filters, { keyword: '', status: '', payStatus: '' });
  pager.pageNum = 1;
}

onMounted(async () => {
  await loadPaymentCapabilities();
  await loadAppointments();
});

[ 
  onMounted,
  PaymentCashierDialog,
  cashierVisible,
  paying,
  paymentActionLoading,
  paymentCreate,
  paymentStatus,
  payForm,
  supportedOnlineChannelSet,
  pagedList,
  loadPaymentCapabilities,
  cancelAppointment,
  openCommentDialog,
  submitComment,
  canChat,
];
</script>

<template>
  <div class="page-shell">
    <section class="panel appointment-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">我的预约</h2>
          <p class="panel-subtitle">在这里统一查看预约状态、支付进度、取消记录和评价信息。</p>
        </div>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="filters.keyword" placeholder="按编号 / 服务 / 技师搜索" clearable @keyup.enter="handleSearch" />
        <el-select v-model="filters.status" placeholder="预约状态" clearable @change="handleSearch">
          <el-option label="已创建" value="CREATED" />
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
        <el-button plain @click="handleSearch">查询</el-button>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="pagedList" stripe>
        <el-table-column prop="serviceItemName" label="服务项目" min-width="160" />
        <el-table-column prop="technicianName" label="技师" min-width="120" />
        <el-table-column prop="appointmentTime" label="开始时间" min-width="170" />
        <el-table-column prop="appointmentEndTime" label="结束时间" min-width="170" />
        <el-table-column prop="statusLabel" label="预约状态" min-width="110" />
        <el-table-column prop="payStatusLabel" label="支付状态" min-width="110" />
        <el-table-column label="金额" min-width="100">
          <template #default="{ row }">{{ '￥' + Number(row.amount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column label="操作" min-width="420" fixed="right">
          <template #default="{ row }">
            <div class="toolbar">
              <el-button size="small" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="row.canPay" size="small" type="warning" @click="openDetail(row.id).then(() => openPayDialog(row))">支付</el-button>
              <el-button v-if="canChat(row)" size="small" type="primary" plain @click="openChat(row)">沟通</el-button>
              <el-button v-if="row.canCancel" size="small" type="danger" plain @click="cancelAppointment(row)">取消</el-button>
              <el-button v-if="row.canComment" size="small" type="success" plain @click="openCommentDialog(row)">评价</el-button>
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
          :total="filteredList.length"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-drawer v-model="drawerVisible" title="预约详情" size="42%">
      <div v-if="detail" class="drawer-content">
        <div class="soft-list">
          <div class="soft-list-item"><strong>服务项目：</strong>{{ detail.serviceItemName }}</div>
          <div class="soft-list-item"><strong>技师：</strong>{{ detail.technicianName }}</div>
          <div class="soft-list-item"><strong>开始时间：</strong>{{ detail.appointmentTime }}</div>
          <div class="soft-list-item"><strong>结束时间：</strong>{{ detail.appointmentEndTime || '-' }}</div>
          <div class="soft-list-item"><strong>预约状态：</strong>{{ detail.statusLabel }}</div>
          <div class="soft-list-item"><strong>支付状态：</strong>{{ detail.payStatusLabel }}</div>
          <div class="soft-list-item"><strong>支付方式：</strong>{{ detail.payMethodLabel || '-' }}</div>
          <div class="soft-list-item"><strong>支付单号：</strong>{{ detail.payNo || '-' }}</div>
          <div class="soft-list-item"><strong>最近支付状态：</strong>{{ detail.latestPaymentStatusLabel || '-' }}</div>
          <div class="soft-list-item"><strong>退款状态：</strong>{{ detail.refundStatusLabel || '-' }}</div>
          <div class="soft-list-item"><strong>退款单号：</strong>{{ detail.refundNo || '-' }}</div>
          <div class="soft-list-item"><strong>退款时间：</strong>{{ detail.refundTime || '-' }}</div>
          <div class="soft-list-item"><strong>下一步：</strong>{{ detail.nextActionHint }}</div>
          <div class="soft-list-item"><strong>备注：</strong>{{ detail.latestRemark || '无' }}</div>
        </div>

        <div class="toolbar">
          <el-button v-if="detail.canPay" type="warning" @click="openPayDialog(detail)">立即支付</el-button>
          <el-button v-if="canChat(detail)" type="primary" plain @click="openChat(detail)">在线沟通</el-button>
          <el-button v-if="detail.canCancel" type="danger" plain @click="cancelAppointment(detail)">取消预约</el-button>
        </div>

        <div class="timeline-block">
          <h4>状态记录</h4>
          <el-timeline>
            <el-timeline-item v-for="log in detail.logs" :key="log.id" :timestamp="log.createdAt">
              {{ log.operatorName }}：{{ log.fromStatusLabel || '已创建' }} -> {{ log.toStatusLabel }} {{ log.remark }}
            </el-timeline-item>
          </el-timeline>
        </div>
      </div>
    </el-drawer>

    <el-dialog v-model="payDialogVisible" title="预约支付" width="440px">
      <el-form label-position="top">
        <el-form-item label="支付方式">
          <el-radio-group v-model="payForm.payMethod">
            <el-radio value="BALANCE">钱包余额</el-radio>
            <el-radio v-for="option in onlinePayOptions" :key="option.value" :value="option.value">{{ option.label }}</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-alert
          type="info"
          :closable="false"
          :title="detail ? '本次预约金额为 ￥' + Number(detail.amount).toFixed(2) + '，支付后预约将进入待确认状态。' : '完成支付后预约才会正式生效。'"
        />
      </el-form>
      <template #footer>
        <div class="toolbar">
          <el-button @click="payDialogVisible = false">关闭</el-button>
          <el-button type="warning" :loading="paying" @click="submitPayment">确认支付</el-button>
        </div>
      </template>
    </el-dialog>

    <PaymentCashierDialog
      v-model="cashierVisible"
      :payment="paymentCreate"
      :status="paymentStatus"
      :loading="paymentActionLoading"
      @refresh="refreshPaymentStatus"
      @complete="markPaymentSuccess"
      @cancel="markPaymentFail"
    />

    <el-dialog v-model="commentVisible" title="提交评价" width="420px">
      <el-form label-position="top">
        <el-form-item label="评分">
          <el-rate v-model="commentForm.rating" />
        </el-form-item>
        <el-form-item label="评价内容">
          <el-input v-model="commentForm.content" type="textarea" :rows="4" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="commentVisible = false">取消</el-button>
        <el-button type="warning" @click="submitComment">提交</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.appointment-panel {
  padding: 26px;
}

.filter-bar {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-bar .el-input,
.filter-bar .el-select {
  width: 210px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.drawer-content {
  display: grid;
  gap: 24px;
}

.timeline-block h4 {
  margin: 0 0 16px;
}
</style>
