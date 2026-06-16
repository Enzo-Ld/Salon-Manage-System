<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { PaymentStatus } from '../../api/types';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const status = ref<PaymentStatus | null>(null);

const paymentNo = computed(() => String(route.query.paymentNo ?? '').trim());
const redirectPath = computed(() => {
  const raw = String(route.query.redirect ?? '').trim();
  return raw.startsWith('/') ? raw : '/';
});

const heading = computed(() => {
  if (!status.value) return '支付结果';
  if (status.value.status === 'SUCCESS') return '支付成功';
  if (status.value.status === 'PAYING') return '等待支付确认';
  if (status.value.status === 'CLOSED') return '支付已关闭';
  if (status.value.status === 'FAILED') return '支付失败';
  if (status.value.status === 'REFUNDING') return '退款处理中';
  if (status.value.status === 'MANUAL_REQUIRED') return '需人工处理';
  if (status.value.status === 'REFUNDED') return '已退款';
  return '支付结果';
});

const tone = computed(() => {
  if (status.value?.status === 'SUCCESS') return 'success';
  if (status.value?.status === 'PAYING' || status.value?.status === 'REFUNDING' || status.value?.status === 'MANUAL_REQUIRED') return 'warning';
  if (status.value?.status === 'FAILED' || status.value?.status === 'CLOSED') return 'error';
  return 'info';
});

async function loadStatus() {
  if (!paymentNo.value) {
    ElMessage.warning('缺少支付单号');
    return;
  }
  loading.value = true;
  try {
    const { data } = await api.paymentStatus(paymentNo.value);
    status.value = data.data;
  } finally {
    loading.value = false;
  }
}

function goBack() {
  router.push(redirectPath.value);
}

function closeWindow() {
  window.close();
}

onMounted(() => {
  void loadStatus();
});
</script>

<template>
  <div class="payment-result page-shell" v-loading="loading">
    <section class="panel result-panel">
      <div class="result-header">
        <p class="result-kicker">支付结果页</p>
        <h1>{{ heading }}</h1>
        <p class="result-copy">
          已从外部支付页面返回，你可以刷新支付状态，或返回原业务页面继续查看。
        </p>
      </div>

      <el-alert
        :type="tone"
        :closable="false"
        :title="status?.failureReason || status?.closeReason || status?.notifyContent || status?.statusLabel || '支付状态同步中，请稍候刷新。'"
      />

      <div class="result-grid">
        <article class="metric-tile">
          <div class="metric-label">支付单号</div>
          <div class="metric-value result-code">{{ paymentNo || '-' }}</div>
        </article>
        <article class="metric-tile">
          <div class="metric-label">当前状态</div>
          <div class="metric-value">{{ status?.statusLabel || '-' }}</div>
        </article>
        <article class="metric-tile">
          <div class="metric-label">支付金额</div>
          <div class="metric-value">{{ status ? '￥' + Number(status.amount).toFixed(2) : '-' }}</div>
        </article>
        <article class="metric-tile">
          <div class="metric-label">支付时间</div>
          <div class="metric-value">{{ status?.paidTime || '-' }}</div>
        </article>
      </div>

      <div class="toolbar result-actions">
        <el-button @click="loadStatus">刷新状态</el-button>
        <el-button type="warning" @click="goBack">返回业务页面</el-button>
        <el-button plain @click="closeWindow">关闭窗口</el-button>
      </div>
    </section>
  </div>
</template>

<style scoped>
.payment-result {
  padding-top: 12px;
}

.result-panel {
  display: grid;
  gap: 20px;
}

.result-header {
  display: grid;
  gap: 10px;
}

.result-kicker {
  margin: 0;
  font-size: 12px;
  letter-spacing: 0.12em;
  text-transform: uppercase;
  color: var(--muted);
}

.result-header h1,
.result-copy {
  margin: 0;
}

.result-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
}

.result-code {
  font-size: 18px;
  word-break: break-all;
}

.result-actions {
  justify-content: flex-end;
}
</style>
