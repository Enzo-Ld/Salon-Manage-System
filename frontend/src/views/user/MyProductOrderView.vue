<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductOrderSummary } from '../../api/types';

const router = useRouter();
const list = ref<ProductOrderSummary[]>([]);
const loading = ref(false);
const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});
const filters = reactive({
  keyword: '',
  status: '',
});

const stats = computed(() => ({
  total: list.value.length,
  pendingPayment: list.value.filter((item) => item.status === 'CREATED').length,
  shipping: list.value.filter((item) => ['PAID', 'SHIPPED'].includes(item.status)).length,
  waitingAfterSale: list.value.filter((item) => item.afterSaleStatus === 'REQUESTED').length,
}));

const filteredList = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return list.value.filter((item) => {
    const matchKeyword = !keyword || [
      item.id,
      item.orderSummary,
      item.productName,
      item.statusLabel,
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchStatus = !filters.status || item.status === filters.status;
    return matchKeyword && matchStatus;
  });
});

const pagedList = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredList.value.slice(start, start + pager.pageSize);
});

async function loadOrders() {
  loading.value = true;
  try {
    const { data } = await api.myProductOrders();
    list.value = data.data;
  } finally {
    loading.value = false;
  }
}

function openDetail(id: number, autopay = false) {
  router.push(autopay ? `/shop/orders/${id}?autopay=1` : `/shop/orders/${id}`);
}

async function cancelOrder(id: number) {
  try {
    await ElMessageBox.confirm('确认取消这笔待支付订单吗？', '取消订单', {
      type: 'warning',
      confirmButtonText: '确认取消',
      cancelButtonText: '暂不取消',
    });
    await api.cancelProductOrder(id);
    ElMessage.success('订单已取消');
    await loadOrders();
  } catch {
    // noop
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
  filters.keyword = '';
  filters.status = '';
  pager.pageNum = 1;
}

onMounted(loadOrders);
</script>

<template>
  <div class="page-shell">
    <section class="metric-grid">
      <article class="metric-tile">
        <div class="metric-label">订单总数</div>
        <div class="metric-value">{{ stats.total }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">待支付</div>
        <div class="metric-value">{{ stats.pendingPayment }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">履约中</div>
        <div class="metric-value">{{ stats.shipping }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">待处理退款/售后</div>
        <div class="metric-value">{{ stats.waitingAfterSale }}</div>
      </article>
    </section>

    <section class="panel order-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">我的商品订单</h2>
          <p class="panel-subtitle">这里可以查看多商品订单、地址快照、物流信息、支付状态和售后进度。</p>
        </div>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="filters.keyword" placeholder="订单号 / 商品 / 订单摘要" clearable @keyup.enter="handleSearch" />
        <el-select v-model="filters.status" placeholder="订单状态" clearable @change="handleSearch">
          <el-option label="待支付" value="CREATED" />
          <el-option label="待发货" value="PAID" />
          <el-option label="已发货" value="SHIPPED" />
          <el-option label="已完成" value="COMPLETED" />
          <el-option label="已取消" value="CANCELLED" />
          <el-option label="已退款" value="REFUNDED" />
        </el-select>
        <el-button plain @click="handleSearch">查询</el-button>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="pagedList" stripe>
        <el-table-column prop="orderSummary" label="订单摘要" min-width="220" />
        <el-table-column prop="itemCount" label="商品件数" width="100" />
        <el-table-column label="应付金额" width="120">
          <template #default="{ row }">¥ {{ Number(row.payAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="receiverName" label="收货人" width="110" />
        <el-table-column prop="fullAddress" label="收货地址" min-width="240" show-overflow-tooltip />
        <el-table-column prop="statusLabel" label="订单状态" width="120" />
        <el-table-column prop="latestPaymentStatusLabel" label="最近支付" width="120" />
        <el-table-column prop="afterSaleStatusLabel" label="售后状态" width="120" />
        <el-table-column prop="createdAt" label="下单时间" min-width="180" />
        <el-table-column label="操作" min-width="320">
          <template #default="{ row }">
            <div class="toolbar">
              <el-button size="small" @click="openDetail(row.id)">详情</el-button>
              <el-button v-if="row.canPay" size="small" type="warning" plain @click="openDetail(row.id, true)">去支付</el-button>
              <el-button v-if="row.canConfirmReceipt" size="small" type="success" plain @click="openDetail(row.id)">
                确认收货
              </el-button>
              <el-button v-if="row.canRequestAfterSale" size="small" type="danger" plain @click="openDetail(row.id)">
                {{ row.status === 'PAID' ? '申请退款' : '申请售后' }}
              </el-button>
              <el-button v-if="row.canCancel" size="small" type="danger" plain @click="cancelOrder(row.id)">取消订单</el-button>
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
  </div>
</template>

<style scoped>
.order-panel {
  padding: 26px;
}

.filter-bar {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-bar .el-input,
.filter-bar .el-select {
  width: 220px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
</style>
