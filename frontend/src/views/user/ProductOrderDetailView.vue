<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { PaymentCapability, PaymentCreateResult, PaymentStatus, ProductOrderDetail, WalletSummary } from '../../api/types';
import PaymentCashierDialog from '../../components/PaymentCashierDialog.vue';

const route = useRoute();
const router = useRouter();
const detail = ref<ProductOrderDetail | null>(null);
const wallet = ref<WalletSummary | null>(null);
const loading = ref(false);
const payDialogVisible = ref(false);
const afterSaleDialogVisible = ref(false);
const paying = ref(false);
const paymentActionLoading = ref(false);
const cashierVisible = ref(false);
const paymentCreate = ref<PaymentCreateResult | null>(null);
const paymentStatus = ref<PaymentStatus | null>(null);
const paymentCapabilities = ref<PaymentCapability | null>(null);
const afterSaleSubmitting = ref(false);
const autopayHandled = ref(false);

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

const afterSaleForm = reactive({
  reason: '',
});

const progressSteps = computed(() => {
  const status = detail.value?.status;
  const refunded = status === 'REFUNDED';
  const cancelled = status === 'CANCELLED';
  const stepKeys = ['CREATED', 'PAID', 'SHIPPED', 'COMPLETED'];
  const currentIndex = stepKeys.indexOf(status ?? '');
  const steps = [
    { key: 'CREATED', label: '订单已创建', desc: '订单已创建，等待完成支付。' },
    { key: 'PAID', label: '订单已支付', desc: '支付完成，商家可开始备货发货。' },
    { key: 'SHIPPED', label: '订单已发货', desc: '发货后可在这里查看物流信息。' },
    { key: 'COMPLETED', label: '订单已完成', desc: '订单已完成，可进行评价或售后申请。' },
  ];

  return steps.map((step, index) => ({
    ...step,
    state: refunded
      ? 'done'
      : cancelled
        ? index === 0
          ? 'done'
          : 'cancelled'
        : currentIndex > index
          ? 'done'
          : currentIndex === index
            ? 'current'
            : 'todo',
  }));
});

const orderCode = computed(() => `BFSM-${String(detail.value?.id ?? '').padStart(6, '0')}`);
const walletBalanceText = computed(() => Number(wallet.value?.balance ?? 0).toFixed(2));
const refundActionLabel = computed(() => (detail.value?.status === 'PAID' ? '申请退款' : '申请售后'));
const refundDialogTitle = computed(() => (detail.value?.status === 'PAID' ? '申请退款' : '申请售后'));
const refundReasonLabel = computed(() => (detail.value?.status === 'PAID' ? '退款原因' : '售后原因'));
const refundReasonPlaceholder = computed(() =>
  detail.value?.status === 'PAID' ? '请输入退款原因' : '请输入售后原因',
);
const requestTimeline = computed(() => {
  if (!detail.value) return [];
  const rows: Array<{ key: string; title: string; time: string; desc: string }> = [];
  if (detail.value.afterSaleAppliedTime) {
    rows.push({
      key: 'applied',
      title: detail.value.status === 'PAID' ? '退款申请已提交' : '售后申请已提交',
      time: detail.value.afterSaleAppliedTime,
      desc: detail.value.afterSaleReason || '申请已提交，等待管理员处理。',
    });
  }
  if (detail.value.afterSaleHandledTime) {
    rows.push({
      key: 'handled',
      title: detail.value.afterSaleStatus === 'REJECTED' ? '申请已驳回' : '管理员已处理',
      time: detail.value.afterSaleHandledTime,
      desc: detail.value.afterSaleRemark || '管理员已返回处理结果。',
    });
  }
  if (detail.value.refundTime) {
    rows.push({
      key: 'refunded',
      title: '退款完成',
      time: detail.value.refundTime,
      desc: detail.value.refundAmount ? '退款金额 ￥' + Number(detail.value.refundAmount).toFixed(2) + ' 已按原支付方式处理。' : '退款处理已完成。',
    });
  }
  return rows;
});

async function loadDetail() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    const [detailRes, walletRes] = await Promise.all([api.productOrderDetail(id), api.walletSummary()]);
    detail.value = detailRes.data.data;
    wallet.value = walletRes.data.data;
    await maybeOpenAutopay();
  } finally {
    loading.value = false;
  }
}

async function loadPaymentCapabilities() {
  try {
    const { data } = await api.paymentCapabilities();
    paymentCapabilities.value = data.data;
  } catch {
    paymentCapabilities.value = null;
  }
}

async function maybeOpenAutopay() {
  if (autopayHandled.value) return;
  if (route.query.autopay !== '1') return;
  if (!detail.value?.canPay) return;
  autopayHandled.value = true;
  payDialogVisible.value = true;
  await router.replace({
    path: route.path,
    query: { ...route.query, autopay: undefined },
  });
}

async function cancelOrder() {
  if (!detail.value?.canCancel) return;
  try {
    await ElMessageBox.confirm('确认取消这笔未支付订单吗？', '取消订单', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '再想想',
    });
    await api.cancelProductOrder(detail.value.id);
    ElMessage.success('订单已取消');
    await loadDetail();
  } catch {
    // noop
  }
}

async function confirmReceipt() {
  if (!detail.value?.canConfirmReceipt) return;
  try {
    await ElMessageBox.confirm('确认收货并完成这笔订单吗？', '确认收货', {
      type: 'warning',
      confirmButtonText: '确认收货',
      cancelButtonText: '暂不',
    });
    await api.confirmProductOrderReceipt(detail.value.id);
    ElMessage.success('订单已完成');
    await loadDetail();
  } catch {
    // noop
  }
}

async function submitPayment() {
  if (!detail.value) return;
  paying.value = true;
  try {
    if (payForm.payMethod === 'BALANCE') {
      await api.payProductOrder(detail.value.id, payForm.payMethod);
      ElMessage.success('余额支付成功');
      payDialogVisible.value = false;
      await loadDetail();
      return;
    }

    if (!supportedOnlineChannelSet.value.has(payForm.payMethod)) {
      ElMessage.warning('当前选择的支付方式暂不可用，请切换可用渠道。');
      payForm.payMethod = (onlinePayOptions.value[0]?.value ?? 'ALIPAY') as 'BALANCE' | 'WECHAT' | 'ALIPAY';
      return;
    }

    const { data } = await api.createProductOrderPayment(detail.value.id, payForm.payMethod);
    paymentCreate.value = data.data;
    payDialogVisible.value = false;
    cashierVisible.value = true;
    await refreshPaymentStatus();
  } catch {
    paymentCreate.value = null;
    paymentStatus.value = null;
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
  } catch {
    // noop
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
    ElMessage.success('支付已确认');
    cashierVisible.value = false;
    await loadDetail();
  } catch {
    // noop
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
    await loadDetail();
  } catch {
    // noop
  } finally {
    paymentActionLoading.value = false;
  }
}

async function submitAfterSale() {
  if (!detail.value) return;
  if (!afterSaleForm.reason.trim()) {
    ElMessage.warning('请输入售后原因');
    return;
  }
  afterSaleSubmitting.value = true;
  try {
    await api.applyProductAfterSale(detail.value.id, afterSaleForm.reason.trim());
    ElMessage.success(`${refundActionLabel.value}已提交`);
    afterSaleDialogVisible.value = false;
    afterSaleForm.reason = '';
    await loadDetail();
  } finally {
    afterSaleSubmitting.value = false;
  }
}

function goToProduct(productId?: number | null, action?: 'comment') {
  if (!productId) return;
  router.push({
    path: `/shop/${productId}`,
    query: action ? { action } : undefined,
  });
}

onMounted(() => {
  void loadPaymentCapabilities();
  void loadDetail();
});
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section v-if="detail" class="toolbar page-toolbar">
      <el-button plain @click="router.back()">返回</el-button>
      <el-button @click="router.push('/shop/orders')">全部订单</el-button>
    </section>

    <section v-if="detail" class="panel order-hero">
      <div>
        <p class="order-kicker">商品订单详情</p>
        <h2>{{ detail.orderSummary }}</h2>
        <p class="order-copy">{{ detail.nextActionHint }}</p>
      </div>
      <div class="order-meta">
        <div class="order-code">{{ orderCode }}</div>
        <div class="toolbar">
          <el-tag size="large" effect="dark">{{ detail.statusLabel }}</el-tag>
          <el-tag type="info" effect="plain">{{ detail.afterSaleStatusLabel }}</el-tag>
        </div>
      </div>
    </section>

    <section v-if="detail" class="metric-grid">
      <article class="metric-tile">
        <div class="metric-label">应付金额</div>
        <div class="metric-value">{{ '￥' + Number(detail.payAmount).toFixed(2) }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">钱包余额</div>
        <div class="metric-value">{{ '￥' + walletBalanceText }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">支付状态</div>
        <div class="metric-value">{{ detail.payStatusLabel }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">商品数量</div>
        <div class="metric-value">{{ detail.itemCount }}</div>
      </article>
    </section>

    <section v-if="detail" class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">订单进度</h3>
          <p class="panel-subtitle">查看当前订单状态以及下一步操作提示。</p>
        </div>
      </div>
      <div class="progress-strip">
        <div
          v-for="step in progressSteps"
          :key="step.key"
          class="progress-step"
          :class="`is-${step.state}`"
        >
          <div class="progress-dot"></div>
          <div class="progress-body">
            <div class="progress-title">{{ step.label }}</div>
            <div class="progress-desc">{{ step.desc }}</div>
          </div>
        </div>
      </div>
    </section>

    <section v-if="detail" class="section-grid">
      <article class="panel detail-card">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">订单商品</h3>
            <p class="panel-subtitle">可进入商品详情页查看信息或进行评价。</p>
          </div>
        </div>
        <div class="item-list">
          <div v-for="item in detail.items" :key="item.productId" class="item-row">
            <div>
              <div class="item-name">{{ item.productName }}</div>
              <div class="item-meta">{{ item.categoryName || '默认规格' }}</div>
            </div>
            <div class="item-side">
              <span>x{{ item.quantity }}</span>
              <span>{{ '￥' + Number(item.amount).toFixed(2) }}</span>
              <el-button text @click="goToProduct(item.productId, detail.canComment ? 'comment' : undefined)">
                查看商品
              </el-button>
            </div>
          </div>
        </div>
      </article>

      <article class="panel detail-card">
        <div class="panel-header">
          <div>
            <h3 class="panel-title">收货与支付信息</h3>
            <p class="panel-subtitle">查看收货人、地址、支付和售后信息。</p>
          </div>
        </div>
        <div class="kv-list">
          <div><span>收货人</span><strong>{{ detail.receiverName }}</strong></div>
          <div><span>联系电话</span><strong>{{ detail.receiverPhone }}</strong></div>
          <div><span>收货地址</span><strong>{{ detail.fullAddress || '-' }}</strong></div>
          <div><span>下单时间</span><strong>{{ detail.createdAt }}</strong></div>
          <div><span>支付状态</span><strong>{{ detail.payStatusLabel }}</strong></div>
          <div><span>售后状态</span><strong>{{ detail.afterSaleStatusLabel }}</strong></div>
        </div>
      </article>
    </section>

    <section v-if="detail && requestTimeline.length" class="panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">售后时间线</h3>
          <p class="panel-subtitle">查看售后申请、退款进度和处理结果。</p>
        </div>
      </div>
      <div class="timeline-list">
        <div v-for="node in requestTimeline" :key="node.key" class="timeline-item">
          <div class="timeline-dot"></div>
          <div class="timeline-body">
            <div class="timeline-head">
              <strong>{{ node.title }}</strong>
              <span>{{ node.time }}</span>
            </div>
            <p>{{ node.desc }}</p>
          </div>
        </div>
      </div>
    </section>

    <section v-if="detail" class="toolbar page-actions">
      <el-button v-if="detail.canCancel" plain @click="cancelOrder">取消订单</el-button>
      <el-button v-if="detail.canPay" type="warning" @click="payDialogVisible = true">立即支付</el-button>
      <el-button v-if="detail.canConfirmReceipt" type="success" @click="confirmReceipt">确认收货</el-button>
      <el-button v-if="detail.canRequestAfterSale" type="danger" plain @click="afterSaleDialogVisible = true">
        {{ refundActionLabel }}
      </el-button>
      <el-button v-if="detail.canComment" @click="goToProduct(detail.items[0]?.productId, 'comment')">
        去评价
      </el-button>
    </section>

    <el-dialog v-model="payDialogVisible" title="订单支付" width="420px">
      <el-form label-position="top">
        <el-form-item label="支付方式">
          <el-radio-group v-model="payForm.payMethod">
            <el-radio value="BALANCE">钱包余额</el-radio>
            <el-radio
              v-for="option in onlinePayOptions"
              :key="option.value"
              :value="option.value"
            >
              {{ option.label }}
            </el-radio>
          </el-radio-group>
        </el-form-item>
        <p class="dialog-tip">当前钱包余额：{{ '￥' + walletBalanceText }}</p>
      </el-form>
      <template #footer>
        <el-button @click="payDialogVisible = false">关闭</el-button>
        <el-button type="warning" :loading="paying" @click="submitPayment">继续支付</el-button>
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

    <el-dialog v-model="afterSaleDialogVisible" :title="refundDialogTitle" width="420px">
      <el-form label-position="top">
        <el-form-item :label="refundReasonLabel">
          <el-input
            v-model="afterSaleForm.reason"
            type="textarea"
            :rows="4"
            :maxlength="200"
            show-word-limit
            :placeholder="refundReasonPlaceholder"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="afterSaleDialogVisible = false">关闭</el-button>
        <el-button type="primary" :loading="afterSaleSubmitting" @click="submitAfterSale">
          提交
        </el-button>
      </template>
    </el-dialog>
  </div>
</template>
<style scoped>
.order-hero,
.detail-card {
  padding: 24px 26px;
}

.order-hero {
  display: flex;
  justify-content: space-between;
  gap: 20px;
}

.order-kicker {
  margin: 0 0 10px;
  color: var(--accent-deep);
  letter-spacing: 0.14em;
  text-transform: uppercase;
  font-size: 12px;
}

.order-hero h2,
.order-copy {
  margin: 0;
}

.order-copy {
  margin-top: 12px;
  color: var(--muted);
  line-height: 1.7;
}

.order-meta {
  display: grid;
  justify-items: end;
  align-content: space-between;
  gap: 18px;
}

.order-code {
  font-family: 'Cascadia Code', 'Consolas', monospace;
  font-size: 18px;
}

.step-list {
  display: grid;
  gap: 16px;
}

.step-item {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 14px;
  align-items: start;
}

.step-item p {
  margin: 8px 0 0;
  color: var(--muted);
}

.step-dot {
  width: 18px;
  height: 18px;
  border-radius: 50%;
  margin-top: 4px;
  background: rgba(122, 86, 37, 0.16);
}

.step-item.is-done .step-dot {
  background: var(--success);
}

.step-item.is-current .step-dot {
  background: var(--accent);
  box-shadow: 0 0 0 6px rgba(186, 140, 66, 0.14);
}

.step-item.is-cancelled .step-dot {
  background: #d05e39;
}

.soft-list {
  display: grid;
  gap: 12px;
}

.soft-list-item {
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 250, 243, 0.8);
}

.action-toolbar {
  margin-top: 18px;
}

.page-toolbar {
  margin-bottom: 12px;
}

.timeline-list {
  display: grid;
  gap: 16px;
}

.timeline-item {
  display: grid;
  grid-template-columns: 18px 1fr;
  gap: 14px;
  align-items: start;
}

.timeline-dot {
  width: 18px;
  height: 18px;
  margin-top: 4px;
  border-radius: 50%;
  background: var(--accent);
  box-shadow: 0 0 0 6px rgba(186, 140, 66, 0.14);
}

.timeline-copy p {
  margin: 8px 0 0;
  color: var(--muted);
}

.timeline-time {
  font-size: 14px;
}

@media (max-width: 900px) {
  .order-hero {
    flex-direction: column;
  }

  .order-meta {
    justify-items: start;
  }
}
</style>
