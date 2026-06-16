<script setup lang="ts">
// @ts-nocheck
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { PaymentCapability, PaymentCreateResult, PaymentStatus, RechargeActivity, WalletSummary, WalletTransaction } from '../../api/types';
import PaymentCashierDialog from '../../components/PaymentCashierDialog.vue';
import { normalizePagePayload } from '../../utils/page';

const loading = ref(false);
const summary = ref<WalletSummary | null>(null);
const activities = ref<RechargeActivity[]>([]);
const transactions = ref<WalletTransaction[]>([]);
const submitting = ref(false);
const paymentActionLoading = ref(false);
const cashierVisible = ref(false);
const paymentCreate = ref<PaymentCreateResult | null>(null);
const paymentStatus = ref<PaymentStatus | null>(null);
const paymentCapabilities = ref<PaymentCapability | null>(null);
const transactionKeyword = ref('');
const transactionType = ref('');
const amountDirection = ref('');
const pager = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});

const form = reactive<{
  activityId: number | null;
  amount: number;
  payMethod: 'WECHAT' | 'ALIPAY';
}>({
  activityId: null,
  amount: 100,
  payMethod: 'WECHAT',
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

const balanceText = computed(() => Number(summary.value?.balance ?? 0).toFixed(2));
const rechargeText = computed(() => Number(summary.value?.totalRecharge ?? 0).toFixed(2));
const giftText = computed(() => Number(summary.value?.totalGift ?? 0).toFixed(2));
const transactionTypeOptions = computed(() =>
  Array.from(new Set(transactions.value.map((item) => item.txnType).filter(Boolean))).map((type) => ({
    label: txnTypeLabel(type),
    value: type,
  })),
);
const filteredTransactions = computed(() => {
  const normalizedKeyword = transactionKeyword.value.trim().toLowerCase();
  return transactions.value.filter((item) => {
    const keywordMatch =
      !normalizedKeyword ||
      [txnTypeLabel(item.txnType), item.txnType, item.remark, item.relatedType, item.createdAt].some((value) =>
        String(value ?? '').toLowerCase().includes(normalizedKeyword),
      );
    const typeMatch = !transactionType.value || item.txnType === transactionType.value;
    const amount = Number(item.amount);
    const directionMatch =
      !amountDirection.value ||
      (amountDirection.value === 'IN' && amount >= 0) ||
      (amountDirection.value === 'OUT' && amount < 0);
    return keywordMatch && typeMatch && directionMatch;
  });
});
const pagedTransactions = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredTransactions.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  loading.value = true;
  try {
    const [summaryRes, activitiesRes] = await Promise.all([
      api.walletSummary(),
      api.walletActivities(),
    ]);
    summary.value = summaryRes.data.data;
    activities.value = activitiesRes.data.data;
    await loadTransactions(pager.pageNum);
  } catch {
    summary.value = null;
    activities.value = [];
    transactions.value = [];
    pager.total = 0;
  } finally {
    loading.value = false;
  }
}

async function loadPaymentCapabilities() {
  try {
    const { data } = await api.paymentCapabilities();
    paymentCapabilities.value = data.data;
    if (!supportedOnlineChannelSet.value.has(form.payMethod)) {
      form.payMethod = (onlinePayOptions.value[0]?.value ?? 'ALIPAY') as 'WECHAT' | 'ALIPAY';
    }
  } catch {
    paymentCapabilities.value = null;
  }
}

async function loadTransactions(pageNum = pager.pageNum) {
  try {
    const { data } = await api.walletTransactions();
    transactions.value = Array.isArray(data.data)
      ? data.data
      : normalizePagePayload<WalletTransaction>(data.data, 1, 100000).records;
    pager.total = filteredTransactions.value.length;
    pager.pageNum = pageNum;
  } catch {
    transactions.value = [];
    pager.total = 0;
  }
}

function pickActivity(activity: RechargeActivity) {
  form.activityId = activity.id ?? null;
  form.amount = Number(activity.rechargeAmount);
}

function activityEndText(activity: RechargeActivity) {
  if (!activity.endTime) {
    return '长期有效';
  }
  return `截止 ${activity.endTime.replace('T', ' ').slice(0, 16)}`;
}

function clearActivity() {
  form.activityId = null;
}

async function submitRecharge() {
  submitting.value = true;
  try {
    if (!form.amount || Number(form.amount) <= 0) {
      ElMessage.warning('请输入有效的充值金额');
      return;
    }
    if (!supportedOnlineChannelSet.value.has(form.payMethod)) {
      ElMessage.warning('当前选择的支付方式暂不可用，请切换可用渠道。');
      form.payMethod = (onlinePayOptions.value[0]?.value ?? 'ALIPAY') as 'WECHAT' | 'ALIPAY';
      return;
    }
    const { data } = await api.createWalletRechargePayment({
      activityId: form.activityId ?? undefined,
      amount: form.amount,
      payMethod: form.payMethod,
    });
    paymentCreate.value = data.data;
    cashierVisible.value = true;
    await refreshPaymentStatus();
  } catch {
    paymentCreate.value = null;
    paymentStatus.value = null;
  } finally {
    submitting.value = false;
  }
}

async function refreshPaymentStatus() {
  if (!paymentCreate.value) return;
  paymentActionLoading.value = true;
  try {
    const { data } = await api.paymentStatus(paymentCreate.value.paymentNo);
    paymentStatus.value = data.data;
  } catch {
    // 婵犵數濮烽弫鍛婃叏閻戣棄鏋侀柟闂寸绾剧粯绻涢幋娆忕仾闁稿鍊濋弻鏇熺箾閻愵剚鐝旈梺鍛婅壘缂嶅﹪鐛弽顬ュ酣顢楅埀顒佷繆閼测晝妫柟瑙勫姦濞兼劕菐閸パ嶈含濠碘€崇埣瀹曘劑顢橀悢鍓插敳闂傚倷鐒﹂幃鍫曞礉瀹ュ洦宕查柛宀€鍋炵粻鎺楁⒒娴ｄ警鐒剧紒缁樺姍瀹曘儳鈧綆鍓涚粈濠囨煙鐎电啸缁炬儳銈搁弻宥堫檨闁告挾鍠庨悾鐑藉箳濡や礁鈧兘鎮楅悽娈跨劸閻㈩垱鍎抽埞鎴︽偐閸偅姣勯梺绋款儐閻╊垶骞冨Ο琛℃斀閻庯綆浜為ˇ顕€姊洪棃娑氬婵☆偅绋撳褔鍩€椤掑嫭鈷戦梺顐ゅ仜閼活垱鏅堕鐐村€靛ù锝呭暙娴滃綊鏌嶈閸撴氨绮欓幒妞烩偓锕傚炊椤掆偓閸屻劑鏌ｉ姀鐘冲暈闁抽攱鍨块弻娑樷槈濮楀牆浼愰梺璇茬箳閸犳牠寮诲☉娆戠瘈闁告劦浜為悾闈涱渻閵堝骸澧柨鏇ㄤ邯閵嗕礁顫濈捄铏瑰姦濡炪倖甯掔€氼剟鎷戦悢鍝ョ闁瑰瓨鐟ラ悘鈺呮煟閹惧鈽夐棁澶愭煥濠靛棙澶勯柛銈傚亾婵犵數鍋涢悧濠囥€冩繝鍥х畺婵°倕鍟崰鍡涙煕閺囥劌澧版い锔哄妽缁绘繈鎮介棃娑楃捕闂佹寧娲忛崕鑼矚鏉堛劎绡€闁搞儜鍜佸晣濠电偠鎻紞鈧い顐㈩槸閳诲秹宕堕浣叉嫼闂備緡鍋嗛崑娑㈡嚐椤栨稒娅犻柟娈垮枓閸嬫捇宕归锝囧嚒闂佹寧娲︽禍婊堟偩閻戣姤鍊荤紒娑橆儐閺咃綁姊虹紒姗嗙劷妞わ綆鍠氬Σ鎰板蓟閵夛腹鎷绘繛鎾村焹閸嬫捇鏌嶈閸撴盯宕戝☉銏″殣妞ゆ牗绋掑▍鐘炽亜閺囨浜鹃梺璇″櫙缁绘繃淇婇崼鏇炲耿闁归偊浜為惄搴ㄦ⒒婵犲骸浜滄繛璇х畵楠炴劖绻濆顒勬７闂佺粯鍨堕…鍥╃不妤ｅ啯鐓曟繛鎴濆船閺嬬喓鈧娲栭惌鍌炲蓟閳ュ磭鏆嗛悗锝庡墰琚﹂梻浣筋嚃閸犳帡寮查悩鑼殾闁挎繂妫楃欢鐐烘倶閻愰鍤欏ù婊冪秺濮婂宕掑▎鎴濆闂佽鍠栭悥鐓庣暦濠靛洦鍎熼柕濞垮劤閺屟冾渻閵堝棗濮€闁圭绻濋弫鎰緞婵犲嫮鏉告俊鐐€栭崹鍏兼叏閵堝妫橀柍褜鍓熷缁樻媴鐟欏嫬浠╅梺绋垮瘨閸ㄨ泛鐣峰┑鍫氬亾閿濆簼鎲炬繛灏栨櫊閺屻劑寮村Δ鈧禍楣冩⒑鐎圭媭娼愰柛銊ユ健楠炲啫鈻庨幘宕囩厬婵犮垼鍩栬摫闁挎稐绶氬缁樻媴閻戞ê娈岄梺纭呭Г缁捇鍨鹃敃鍌氶唶闁靛鍎抽崢娲倵楠炲灝鍔氭い锔垮嵆瀵煡骞栨担鍦幗濠碘槅鍨靛▍锝夋晬瀹ュ鐓曟俊顖氭惈閼稿綊鏌嶇憴鍕伌闁诡喗鐟╁畷锝嗗緞濡紮绱掗梻浣规た娴滄粓藝闂堟侗娼栨繛宸憾閺佸洨鎲稿澶嬪€堕柣妯肩帛椤ュ棗顭块懜闈涘闁稿﹤鐖奸悡顐﹀炊閵婏腹鎷绘繝鈷€灞藉缂佺粯鐩畷銊╊敇閵娿儱顫犻梻渚€鈧偛鑻晶鍙夈亜椤愩埄妲搁悡銈嗘叏濡炶浜鹃梺闈涙閸熸潙鐣烽妸鈺佺妞ゆ挾鍠愬▍宥呪攽閻樼粯娑ч柛濠傤煼閳ワ箓宕奸妷褍绁﹂梺闈涚箚閳ь剝灏欑粻姘渻閵堝棛澧紒璇插暣椤㈡棃鎮╁顔惧數闁荤姴鎼幖顐︻敂閳哄懏鐓冮悷娆忓閻忔挳鏌熼瑙勬珚妞ゃ垺鐟╅幊鏍煛閳ь剛妲愬Ο鑽ょ瘈闁汇垽娼ф禒婊勩亜閿旇棄顥嬬紒顔界懇楠炴鎷犻懠顒€濮︽俊鐐€栫敮濠勭矆娓氣偓瀹曠敻顢楅崟顒傚幈闂佹寧绻傜换鎺旂矓椤曗偓閺岋絽鈽夐崡鐐寸彎閻庤娲橀敃銏ゅ春閿熺姴绀冩い蹇撴椤矂姊婚崒娆掑厡妞ゎ厼鐗忛幃顕€顢曢敃鈧壕褰掓煛閸モ晛浜归柡瀣閺屾洘绻涢悙顒佺彆闂佹娊鏀遍崹鍦閹烘柡鍋撻敐搴′簻妞ゆ洘绮嶇换婵嬪焵椤掑嫭鍋嬮柛顐ゅ枎瀵灝鈹戦埥鍡楃仯妞ゎ厼鐗婇幈銊╁磼濠ф儳浜鹃悷娆忓缁€鈧梺鍦拡閸嬪鎮橀崘顔解拺闁告稑锕ゆ慨锕€霉濠婂懎浠遍柡浣哥Т閳规垿宕堕埡鍐惧晭闂佸搫顦悧鍡樻櫠娴犲绀嗗ù鐓庣摠閻撶喐淇婇妶鍌氫壕濠碘槅鍋呯换鍫ユ偘椤旂晫绡€闁搞儜鍛澑闂備礁鎼ˇ鎵偓绗涘洦鍊垫い鎺嶆缁诲棝鏌曢崼婵堢闁告帊鍗抽弻娑㈡偆娴ｅ摜浠搁梺璇″櫙缁绘繂顕ｉ幘顔碱潊闁挎稑瀚獮宥夋⒒閸屾瑧璐伴柛娆忕箳缁骞嬮敂鎯хウ闂佸搫绋侀崑鍡欏閸忛棿绻嗛柕鍫濆椤︼箓鏌涘┑鍥ㄣ仢闁?
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
    ElMessage.success('充值已确认');
    cashierVisible.value = false;
    await loadData();
  } catch {
    // noop
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

function resetTransactionFilters() {
  transactionKeyword.value = '';
  transactionType.value = '';
  amountDirection.value = '';
  pager.pageNum = 1;
}

async function markPaymentFail() {
  if (!paymentCreate.value) return;
  paymentActionLoading.value = true;
  try {
    const { data } = await api.cancelPayment(paymentCreate.value.paymentNo);
    paymentStatus.value = data.data;
    ElMessage.warning('支付已取消');
  } catch {
    // noop
  } finally {
    paymentActionLoading.value = false;
  }
}

function txnTypeLabel(type: string) {
  switch (type) {
    case 'RECHARGE':
      return '充值';
    case 'GIFT':
      return '赠送';
    case 'ORDER_PAY':
      return '订单支付';
    case 'REFUND':
      return '退款';
    case 'ADJUST':
      return '人工调整';
    default:
      return type;
  }
}

onMounted(() => {
  void loadPaymentCapabilities();
  void loadData();
});
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section class="metric-grid">
      <article class="metric-tile">
        <div class="metric-label">钱包余额</div>
        <div class="metric-value">{{ '￥' + balanceText }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">累计充值</div>
        <div class="metric-value">{{ '￥' + rechargeText }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">累计赠送</div>
        <div class="metric-value">{{ '￥' + giftText }}</div>
      </article>
    </section>

    <section class="section-grid">
      <article class="panel wallet-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">钱包充值</h2>
            <p class="panel-subtitle">选择充值金额后进入收银台，完成在线支付。</p>
          </div>
        </div>

        <div class="activity-grid">
          <button
            v-for="activity in activities"
            :key="activity.id"
            class="activity-card"
            :class="{ active: form.activityId === activity.id }"
            @click="pickActivity(activity)"
          >
            <strong>{{ activity.title }}</strong>
            <p>充值 {{ '￥' + Number(activity.rechargeAmount).toFixed(2) }}</p>
            <p>赠送 {{ '￥' + Number(activity.giftAmount).toFixed(2) }}</p>
            <p>{{ activityEndText(activity) }}</p>
          </button>
        </div>

        <el-form label-position="top" class="recharge-form">
          <el-form-item label="充值金额">
            <el-input-number v-model="form.amount" :min="1" :step="50" @change="clearActivity" />
          </el-form-item>
          <el-form-item label="支付方式">
            <el-radio-group v-model="form.payMethod">
              <el-radio-button v-for="option in onlinePayOptions" :key="option.value" :value="option.value">{{ option.label }}</el-radio-button>
            </el-radio-group>
          </el-form-item>
          <div class="toolbar">
            <el-button type="warning" :loading="submitting" @click="submitRecharge">打开收银台</el-button>
          </div>
        </el-form>
      </article>

      <article class="panel wallet-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">钱包流水</h2>
            <p class="panel-subtitle">充值、赠送、退款和订单支付都会记录在这里，方便随时查看。</p>
          </div>
        </div>

        <div class="toolbar filter-bar">
          <el-input
            v-model="transactionKeyword"
            placeholder="搜索说明 / 类型 / 时间"
            clearable
            @input="pager.pageNum = 1"
            @clear="pager.pageNum = 1"
          />
          <el-select v-model="transactionType" placeholder="流水类型" clearable @change="pager.pageNum = 1">
            <el-option
              v-for="item in transactionTypeOptions"
              :key="item.value"
              :label="item.label"
              :value="item.value"
            />
          </el-select>
          <el-select v-model="amountDirection" placeholder="收支方向" clearable @change="pager.pageNum = 1">
            <el-option label="收入" value="IN" />
            <el-option label="支出" value="OUT" />
          </el-select>
          <el-button text @click="resetTransactionFilters">重置</el-button>
        </div>

        <el-table :data="pagedTransactions" stripe>
          <el-table-column label="类型" width="130">
            <template #default="{ row }">{{ txnTypeLabel(row.txnType) }}</template>
          </el-table-column>
          <el-table-column label="金额" width="120">
            <template #default="{ row }">
              <span :class="Number(row.amount) >= 0 ? 'amount-plus' : 'amount-minus'">
                {{ Number(row.amount) >= 0 ? '+' : '' }}{{ Number(row.amount).toFixed(2) }}
              </span>
            </template>
          </el-table-column>
          <el-table-column label="变动后余额" width="140">
            <template #default="{ row }">{{ '￥' + Number(row.balanceAfter).toFixed(2) }}</template>
          </el-table-column>
          <el-table-column prop="remark" label="说明" min-width="220" />
          <el-table-column prop="createdAt" label="创建时间" min-width="180" />
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="pager.pageNum"
            :page-size="pager.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="filteredTransactions.length"
            @current-change="handlePageChange"
            @size-change="handleSizeChange"
          />
        </div>
      </article>
    </section>

    <PaymentCashierDialog
      v-model="cashierVisible"
      :payment="paymentCreate"
      :status="paymentStatus"
      :loading="paymentActionLoading"
      @refresh="refreshPaymentStatus"
      @complete="markPaymentSuccess"
      @cancel="markPaymentFail"
    />
  </div>
</template>

<style scoped>
.wallet-panel {
  padding: 26px;
}

.activity-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  gap: 14px;
  margin-bottom: 22px;
}

.filter-bar {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: min(280px, 100%);
}

.filter-bar .el-select {
  width: 160px;
}

.activity-card {
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.88);
  border-radius: 22px;
  padding: 18px;
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

.activity-card.active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
}

.activity-card strong,
.activity-card p {
  margin: 0;
}

.activity-card p + p {
  margin-top: 8px;
}

.recharge-form {
  max-width: 420px;
}

.amount-plus {
  color: var(--success);
  font-weight: 600;
}

.amount-minus {
  color: #d05e39;
  font-weight: 600;
}
</style>
