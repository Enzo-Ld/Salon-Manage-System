<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { ShoppingCartItem, UserAddress } from '../../api/types';

const router = useRouter();
const cartItems = ref<ShoppingCartItem[]>([]);
const addresses = ref<UserAddress[]>([]);
const loading = ref(false);
const submitting = ref(false);
const selectedAddressId = ref<number | null>(null);
const keyword = ref('');
const selectedOnly = ref(false);

const selectedItems = computed(() => cartItems.value.filter((item) => item.selected));
const filteredCartItems = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return cartItems.value.filter((item) => {
    const keywordMatch = !normalizedKeyword || [item.productName, item.categoryName].some((value) =>
      String(value ?? '').toLowerCase().includes(normalizedKeyword),
    );
    const selectedMatch = !selectedOnly.value || item.selected;
    return keywordMatch && selectedMatch;
  });
});
const totalAmount = computed(() => selectedItems.value.reduce((sum, item) => sum + Number(item.amount), 0));
const totalCount = computed(() => selectedItems.value.reduce((sum, item) => sum + Number(item.quantity), 0));

async function loadData() {
  loading.value = true;
  try {
    const [cartRes, addressRes] = await Promise.all([api.cartItems(), api.userAddresses()]);
    cartItems.value = cartRes.data.data;
    addresses.value = addressRes.data.data;
    const defaultAddress = addresses.value.find((item) => item.isDefault === 1) ?? addresses.value[0];
    selectedAddressId.value = defaultAddress?.id ?? null;
  } finally {
    loading.value = false;
  }
}

async function updateQuantity(item: ShoppingCartItem, nextQuantity: number) {
  if (nextQuantity < 1) {
    return;
  }
  await api.updateCartItem(item.id, { quantity: nextQuantity });
  item.quantity = nextQuantity;
  item.amount = Number(item.price) * nextQuantity;
}

function handleQuantityChange(item: ShoppingCartItem, value: number | string | undefined) {
  void updateQuantity(item, Number(value || 1));
}

async function toggleSelected(item: ShoppingCartItem, selected: boolean) {
  await api.updateCartItem(item.id, { selected });
  item.selected = selected;
}

async function removeItem(item: ShoppingCartItem) {
  try {
    await ElMessageBox.confirm(`确认移除“${item.productName}”吗？`, '移出购物车', {
      type: 'warning',
      confirmButtonText: '确认移除',
      cancelButtonText: '取消',
    });
    await api.deleteCartItem(item.id);
    ElMessage.success('已移出购物车');
    await loadData();
  } catch {
    // noop
  }
}

async function submitOrder() {
  if (!selectedItems.value.length) {
    ElMessage.warning('请先勾选要结算的商品');
    return;
  }
  if (!selectedAddressId.value) {
    ElMessage.warning('请先选择收货地址');
    return;
  }

  submitting.value = true;
  try {
    const { data } = await api.createProductOrder({
      addressId: selectedAddressId.value,
      cartItemIds: selectedItems.value.map((item) => item.id),
    });
    ElMessage.success('订单已创建，正在进入支付流程');
    router.push(`/shop/orders/${data.data.id}?autopay=1`);
  } finally {
    submitting.value = false;
  }
}

function resetFilters() {
  keyword.value = '';
  selectedOnly.value = false;
}

onMounted(loadData);
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section class="panel cart-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">购物车</h2>
          <p class="panel-subtitle">在这里勾选多个商品统一提交订单，并绑定本次结算使用的收货地址。</p>
        </div>
        <div class="toolbar">
          <el-button plain @click="router.push('/shop')">继续购物</el-button>
          <el-button plain @click="router.push('/profile')">管理资料与地址</el-button>
        </div>
      </div>

      <el-empty v-if="cartItems.length === 0" description="购物车还是空的，先去挑选商品吧" />

      <div v-else class="cart-layout">
        <div class="cart-list">
          <div class="toolbar filter-bar">
            <el-input v-model="keyword" placeholder="搜索商品名称 / 分类" clearable />
            <el-switch v-model="selectedOnly" active-text="只看已选" />
            <el-button text @click="resetFilters">重置</el-button>
          </div>

          <el-empty v-if="filteredCartItems.length === 0" description="当前筛选条件下暂无购物车商品" />

          <article v-for="item in filteredCartItems" :key="item.id" class="cart-card">
            <div class="cart-main">
              <el-checkbox :model-value="item.selected" @change="toggleSelected(item, Boolean($event))" />
              <div class="cart-copy">
                <h3>{{ item.productName }}</h3>
                <p>{{ item.categoryName }} ｜ 库存 {{ item.stock }} 件</p>
              </div>
            </div>

            <div class="cart-actions">
              <el-input-number
                :model-value="item.quantity"
                :min="1"
                :max="item.stock || 1"
                @change="handleQuantityChange(item, $event)"
              />
              <strong>￥{{ Number(item.amount).toFixed(2) }}</strong>
              <el-button text type="danger" @click="removeItem(item)">删除</el-button>
            </div>
          </article>
        </div>

        <aside class="summary-box">
          <div class="summary-section">
            <h3>选择收货地址</h3>
            <el-empty v-if="addresses.length === 0" description="还没有收货地址">
              <el-button type="warning" plain @click="router.push('/profile')">去个人资料新增地址</el-button>
            </el-empty>
            <el-radio-group v-else v-model="selectedAddressId" class="address-group">
              <el-radio v-for="item in addresses" :key="item.id" :value="item.id" class="address-card">
                <div>
                  <strong>{{ item.receiverName }} {{ item.receiverPhone }}</strong>
                  <p>{{ item.province }}{{ item.city }}{{ item.district }}{{ item.detailAddress }}</p>
                  <p>{{ item.tagName || '常用地址' }}{{ item.isDefault === 1 ? ' ｜ 默认地址' : '' }}</p>
                </div>
              </el-radio>
            </el-radio-group>
          </div>

          <div class="summary-section">
            <h3>结算信息</h3>
            <div class="summary-row"><span>已选商品</span><strong>{{ totalCount }} 件</strong></div>
            <div class="summary-row"><span>合计金额</span><strong>￥{{ totalAmount.toFixed(2) }}</strong></div>
            <el-alert
              type="info"
              :closable="false"
              title="提交订单后会进入支付页，后续可继续完成发货、收货和售后演示。"
            />
            <el-button type="warning" size="large" :loading="submitting" @click="submitOrder">提交多商品订单</el-button>
          </div>
        </aside>
      </div>
    </section>
  </div>
</template>

<style scoped>
.cart-panel {
  padding: 26px;
}

.cart-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(360px, 0.9fr);
  gap: 20px;
  align-items: start;
}

.cart-list,
.summary-box,
.summary-section,
.cart-card {
  display: grid;
  gap: 14px;
  min-width: 0;
}

.cart-card,
.summary-section {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.84);
}

.cart-main,
.cart-actions,
.summary-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  min-width: 0;
}

.cart-copy {
  flex: 1;
  min-width: 0;
}

.cart-copy h3,
.cart-copy p,
.summary-row {
  margin: 0;
}

.cart-copy p,
.address-card p {
  color: var(--muted);
}

.cart-copy h3,
.cart-copy p,
.address-card strong,
.address-card p {
  overflow-wrap: anywhere;
}

.filter-bar {
  margin-bottom: 4px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: min(320px, 100%);
}

.address-group {
  display: grid;
  gap: 12px;
  width: 100%;
  min-width: 0;
}

.address-card {
  margin-right: 0;
  padding: 12px 14px;
  border-radius: 16px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  background: rgba(255, 255, 255, 0.55);
  width: 100%;
  height: auto;
  min-width: 0;
  align-items: flex-start;
  white-space: normal;
}

.address-card :deep(.el-radio__label) {
  min-width: 0;
  white-space: normal;
  line-height: 1.5;
}

@media (max-width: 960px) {
  .cart-layout {
    grid-template-columns: 1fr;
  }
}
</style>
