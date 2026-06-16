<script setup lang="ts">
import { ElMessage } from 'element-plus';
import QRCode from 'qrcode';
import { computed, onBeforeUnmount, ref, watch } from 'vue';
import type { PaymentCreateResult, PaymentStatus } from '../api/types';

const props = defineProps<{
  modelValue: boolean;
  payment: PaymentCreateResult | null;
  status: PaymentStatus | null;
  loading?: boolean;
}>();

const emit = defineEmits<{
  'update:modelValue': [value: boolean];
  refresh: [];
  complete: [];
  cancel: [];
}>();

const externalOpened = ref(false);
const qrImageUrl = ref('');
let refreshTimer: number | null = null;

const canOperate = computed(() => Boolean(props.status?.canComplete || props.status?.canCancel));
const isRedirectCheckout = computed(() => props.payment?.checkoutType === 'REDIRECT_FORM' || Boolean(props.payment?.formHtml));
const isQrCheckout = computed(() => props.payment?.checkoutType === 'QR_CODE');
const usesExternalCashier = computed(() => isRedirectCheckout.value || Boolean(props.payment?.payUrl && /^https?:\/\//i.test(props.payment.payUrl) && !isQrCheckout.value));
const externalButtonLabel = computed(() => (props.payment?.payChannel === 'ALIPAY' ? '打开支付宝页面' : '打开支付页面'));
const actionHint = computed(() => {
  if (props.status?.nextAction === 'CONTACT_ADMIN') {
    return '退款或支付状态需要管理员人工处理，请稍后查看处理结果。';
  }
  if (props.payment?.checkoutType === 'QR_CODE') {
    return '请使用微信扫描二维码完成支付，当前页面会自动刷新支付结果。';
  }
  if (props.payment?.checkoutType === 'MANUAL') {
    return '本地演示模式下完成付款后，可使用手动确认按钮推进订单状态。';
  }
  if (isRedirectCheckout.value) {
    return '请打开支付宝页面完成支付，支付完成后回到当前页面，系统会自动刷新支付状态。';
  }
  return '请完成支付后返回当前页面，或手动刷新支付状态。';
});
const providerText = computed(() => {
  if (!props.payment) return '';
  if (props.payment.payChannel === 'WECHAT') return '微信支付';
  if (props.payment.payChannel === 'ALIPAY') return '支付宝';
  return '钱包余额';
});
const statusNotice = computed(() => props.status?.failureReason || props.status?.closeReason || props.status?.notifyContent);

function closeDialog() {
  emit('update:modelValue', false);
}

function openExternalCashier() {
  if (!props.payment) return false;
  if (props.payment.formHtml) {
    const popup = window.open('', '_blank');
    if (!popup) {
      ElMessage.warning('浏览器拦截了支付弹窗，请允许弹窗后重试。');
      return false;
    }
    popup.document.open();
    popup.document.write(props.payment.formHtml);
    popup.document.close();
    externalOpened.value = true;
    return true;
  }
  if (props.payment.payUrl && /^https?:\/\//i.test(props.payment.payUrl)) {
    window.open(props.payment.payUrl, '_blank', 'noopener');
    externalOpened.value = true;
    return true;
  }
  ElMessage.warning('当前支付单暂未生成可打开的外部支付页面。');
  return false;
}

function clearRefreshTimer() {
  if (refreshTimer !== null) {
    window.clearInterval(refreshTimer);
    refreshTimer = null;
  }
}

watch(
  () => props.modelValue,
  (visible) => {
    if (!visible) {
      externalOpened.value = false;
    }
  },
);

watch(
  () => props.payment?.qrCodeText,
  async (text) => {
    if (!text) {
      qrImageUrl.value = '';
      return;
    }
    try {
      qrImageUrl.value = await QRCode.toDataURL(text, {
        width: 232,
        margin: 2,
        color: {
          dark: '#1f2937',
          light: '#ffffff',
        },
      });
    } catch {
      qrImageUrl.value = '';
    }
  },
  { immediate: true },
);

watch(
  () => [props.modelValue, props.payment?.paymentNo] as const,
  ([visible, paymentNo]) => {
    if (!visible || !paymentNo) return;
    if (!usesExternalCashier.value || externalOpened.value) return;
    window.setTimeout(() => {
      if (props.modelValue && props.payment?.paymentNo === paymentNo && !externalOpened.value) {
        openExternalCashier();
      }
    }, 80);
  },
  { immediate: true },
);

watch(
  () => [props.modelValue, props.status?.status, props.status?.canComplete, props.status?.canCancel] as const,
  ([visible, status, canComplete, canCancel]) => {
    clearRefreshTimer();
    if (!visible) return;
    if (status === 'SUCCESS' || status === 'FAILED' || status === 'CLOSED' || status === 'REFUNDED' || status === 'MANUAL_REQUIRED') return;
    if (canComplete || canCancel) return;
    refreshTimer = window.setInterval(() => {
      emit('refresh');
    }, 4000);
  },
  { immediate: true },
);

onBeforeUnmount(clearRefreshTimer);
</script>

<template>
  <el-dialog
    :model-value="modelValue"
    width="520px"
    title="支付收银台"
    @close="closeDialog"
    @update:model-value="emit('update:modelValue', $event)"
  >
    <div v-if="payment" class="payment-box">
      <div class="payment-hero">
        <strong>{{ providerText }}</strong>
        <span>{{ payment.statusLabel }}</span>
      </div>

      <div class="payment-info">
        <div v-if="qrImageUrl" class="payment-qrcode">
          <img :src="qrImageUrl" alt="payment qrcode" />
          <span>{{ isQrCheckout ? '请扫码完成支付。' : '支付凭证码已生成。' }}</span>
        </div>
        <p><strong>支付单号：</strong> {{ payment.paymentNo }}</p>
        <p><strong>支付标题：</strong> {{ payment.subject }}</p>
        <p><strong>支付金额：</strong> {{ '￥' + Number(payment.amount).toFixed(2) }}</p>
        <p v-if="payment.qrCodeText" class="code-text"><strong>收银码：</strong> {{ payment.qrCodeText }}</p>
        <p><strong>过期时间：</strong> {{ payment.expireTime || '-' }}</p>
      </div>

      <el-alert
        type="info"
        :closable="false"
        :title="actionHint"
      />

      <el-alert
        v-if="statusNotice"
        type="warning"
        :closable="false"
        :title="statusNotice"
      />

      <div v-if="status" class="payment-status">
        <span>当前状态：{{ status.statusLabel }}</span>
        <span v-if="status.nextAction">下一步：{{ status.nextAction }}</span>
        <span v-if="status.paidTime">支付时间：{{ status.paidTime }}</span>
      </div>
    </div>

    <template #footer>
      <div class="toolbar">
        <el-button v-if="usesExternalCashier" type="primary" plain @click="openExternalCashier">
          {{ externalButtonLabel }}
        </el-button>
        <el-button :loading="loading" @click="emit('refresh')">刷新状态</el-button>
        <el-button
          v-if="canOperate"
          type="danger"
          plain
          :loading="loading"
          :disabled="!status?.canCancel"
          @click="emit('cancel')"
        >
          取消支付
        </el-button>
        <el-button
          v-if="canOperate"
          type="warning"
          :loading="loading"
          :disabled="!status?.canComplete"
          @click="emit('complete')"
        >
          确认已支付
        </el-button>
      </div>
    </template>
  </el-dialog>
</template>

<style scoped>
.payment-box {
  display: grid;
  gap: 16px;
}

.payment-hero {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 16px 18px;
  border-radius: 8px;
  background: rgba(255, 244, 223, 0.82);
}

.payment-info {
  display: grid;
  gap: 10px;
  padding: 18px;
  border-radius: 8px;
  background: rgba(255, 250, 243, 0.82);
  border: 1px solid rgba(122, 86, 37, 0.12);
}

.payment-qrcode {
  display: grid;
  justify-items: center;
  gap: 10px;
  margin-bottom: 8px;
}

.payment-qrcode img {
  width: 220px;
  height: 220px;
  padding: 12px;
  border-radius: 8px;
  background: #fff;
  box-shadow: inset 0 0 0 1px rgba(122, 86, 37, 0.12);
}

.payment-qrcode span {
  color: var(--muted);
}

.payment-info p,
.payment-status {
  margin: 0;
}

.code-text {
  overflow-wrap: anywhere;
}

.payment-status {
  display: grid;
  gap: 8px;
  color: var(--muted);
}
</style>
