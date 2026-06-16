<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductOrderSummary } from '../../api/types';

const orders = ref<ProductOrderSummary[]>([]);
const loading = ref(false);
const activeFilter = ref<'ALL' | string>('ALL');
const filters = reactive({
  keyword: '',
  dateRange: [] as string[],
});
const shipDialogVisible = ref(false);
const reviewDialogVisible = ref(false);
const currentOrderId = ref<number | null>(null);
const shipSubmitting = ref(false);
const reviewSubmitting = ref(false);

const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});

const shipForm = reactive({
  shippingCompany: '',
  trackingNo: '',
});

const reviewForm = reactive({
  approved: true,
  remark: '',
});

const filteredOrders = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return orders.value.filter((item) => {
    const matchStatus = activeFilter.value === 'ALL'
      || (activeFilter.value === 'AFTER_SALE' ? item.afterSaleStatus === 'REQUESTED' : item.status === activeFilter.value);
    const matchKeyword = !keyword || [
      item.id,
      item.username,
      item.orderSummary,
      item.receiverName,
      item.receiverPhone,
      item.trackingNo,
    ].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const day = item.createdAt?.slice(0, 10) || '';
    const matchDate = filters.dateRange.length !== 2 || (day >= filters.dateRange[0] && day <= filters.dateRange[1]);
    return matchStatus && matchKeyword && matchDate;
  });
});

const pagedOrders = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredOrders.value.slice(start, start + pager.pageSize);
});

async function loadOrders() {
  loading.value = true;
  try {
    const { data } = await api.allProductOrders();
    orders.value = data.data;
  } finally {
    loading.value = false;
  }
}

function setFilter(filter: string) {
  activeFilter.value = filter;
  pager.pageNum = 1;
}

function handleSearch() {
  pager.pageNum = 1;
}

function resetFilters() {
  filters.keyword = '';
  filters.dateRange = [];
  pager.pageNum = 1;
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

function openShipDialog(order: ProductOrderSummary) {
  currentOrderId.value = order.id;
  shipForm.shippingCompany = order.shippingCompany || '';
  shipForm.trackingNo = order.trackingNo || '';
  shipDialogVisible.value = true;
}

function openReviewDialog(order: ProductOrderSummary) {
  currentOrderId.value = order.id;
  reviewForm.approved = true;
  reviewForm.remark = order.afterSaleRemark || '';
  reviewDialogVisible.value = true;
}

async function submitShip() {
  if (!currentOrderId.value) return;
  if (!shipForm.shippingCompany.trim() || !shipForm.trackingNo.trim()) {
    ElMessage.warning('请完整填写物流公司和单号');
    return;
  }
  shipSubmitting.value = true;
  try {
    await api.shipProductOrder(currentOrderId.value, {
      shippingCompany: shipForm.shippingCompany.trim(),
      trackingNo: shipForm.trackingNo.trim(),
    });
    ElMessage.success('订单已发货');
    shipDialogVisible.value = false;
    await loadOrders();
  } finally {
    shipSubmitting.value = false;
  }
}

async function submitReview() {
  if (!currentOrderId.value) return;
  reviewSubmitting.value = true;
  try {
    await api.reviewProductAfterSale(currentOrderId.value, {
      approved: reviewForm.approved,
      remark: reviewForm.remark.trim() || undefined,
    });
    ElMessage.success(reviewForm.approved ? '退款/售后申请已通过并完成退款' : '退款/售后申请已驳回');
    reviewDialogVisible.value = false;
    await loadOrders();
  } finally {
    reviewSubmitting.value = false;
  }
}

onMounted(() => {
  void loadOrders();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel order-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">商品订单管理</h2>
          <p class="panel-subtitle">管理员可统一查看多商品订单、物流信息，以及退款 / 售后申请，完成发货与退款处理。</p>
        </div>
      </div>

      <div class="category-strip">
        <button class="category-chip" :class="{ active: activeFilter === 'ALL' }" @click="setFilter('ALL')">全部</button>
        <button class="category-chip" :class="{ active: activeFilter === 'CREATED' }" @click="setFilter('CREATED')">待支付</button>
        <button class="category-chip" :class="{ active: activeFilter === 'PAID' }" @click="setFilter('PAID')">待发货</button>
        <button class="category-chip" :class="{ active: activeFilter === 'SHIPPED' }" @click="setFilter('SHIPPED')">已发货</button>
        <button class="category-chip" :class="{ active: activeFilter === 'COMPLETED' }" @click="setFilter('COMPLETED')">已完成</button>
        <button class="category-chip" :class="{ active: activeFilter === 'REFUNDED' }" @click="setFilter('REFUNDED')">已退款</button>
        <button class="category-chip" :class="{ active: activeFilter === 'AFTER_SALE' }" @click="setFilter('AFTER_SALE')">待审核退款 / 售后</button>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="filters.keyword" placeholder="订单号 / 用户 / 收货人 / 物流单号" clearable @keyup.enter="handleSearch" />
        <el-date-picker v-model="filters.dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="下单开始" end-placeholder="下单结束" @change="handleSearch" />
        <el-button plain @click="handleSearch">查询</el-button>
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <el-table :data="pagedOrders" stripe>
        <el-table-column prop="id" label="订单号" width="90" />
        <el-table-column prop="username" label="用户" width="120" />
        <el-table-column prop="orderSummary" label="订单摘要" min-width="220" />
        <el-table-column prop="itemCount" label="件数" width="80" />
        <el-table-column label="应付金额" width="120">
          <template #default="{ row }">¥ {{ Number(row.payAmount).toFixed(2) }}</template>
        </el-table-column>
        <el-table-column prop="receiverName" label="收货人" width="110" />
        <el-table-column prop="fullAddress" label="收货地址" min-width="220" show-overflow-tooltip />
        <el-table-column prop="statusLabel" label="订单状态" width="110" />
        <el-table-column prop="afterSaleStatusLabel" label="售后状态" width="110" />
        <el-table-column prop="refundStatusLabel" label="退款状态" width="110" />
        <el-table-column prop="refundNo" label="退款单号" min-width="180" show-overflow-tooltip />
        <el-table-column prop="shippingCompany" label="物流公司" width="130" />
        <el-table-column prop="trackingNo" label="物流单号" min-width="170" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="创建时间" min-width="170" />
        <el-table-column label="操作" min-width="240">
          <template #default="{ row }">
            <div class="toolbar">
              <el-button v-if="row.status === 'PAID' && row.afterSaleStatus === 'NONE'" size="small" type="warning" plain @click="openShipDialog(row)">
                发货
              </el-button>
              <el-button v-if="row.afterSaleStatus === 'REQUESTED'" size="small" type="danger" plain @click="openReviewDialog(row)">
                审核退款 / 售后
              </el-button>
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
          :total="filteredOrders.length"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="shipDialogVisible" title="订单发货" width="420px">
      <el-form label-position="top">
        <el-form-item label="物流公司">
          <el-input v-model="shipForm.shippingCompany" placeholder="例如：顺丰速运" />
        </el-form-item>
        <el-form-item label="物流单号">
          <el-input v-model="shipForm.trackingNo" placeholder="请输入物流单号" />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar">
          <el-button @click="shipDialogVisible = false">取消</el-button>
          <el-button type="warning" :loading="shipSubmitting" @click="submitShip">确认发货</el-button>
        </div>
      </template>
    </el-dialog>

    <el-dialog v-model="reviewDialogVisible" title="审核退款 / 售后申请" width="520px">
      <el-form label-position="top">
        <el-form-item label="处理结果">
          <el-radio-group v-model="reviewForm.approved">
            <el-radio :value="true">同意退款</el-radio>
            <el-radio :value="false">驳回申请</el-radio>
          </el-radio-group>
        </el-form-item>
        <el-form-item label="处理备注">
          <el-input
            v-model="reviewForm.remark"
            type="textarea"
            :rows="4"
            maxlength="300"
            show-word-limit
            placeholder="可填写退款说明或驳回原因"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar">
          <el-button @click="reviewDialogVisible = false">取消</el-button>
          <el-button type="danger" :loading="reviewSubmitting" @click="submitReview">提交结果</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.order-panel {
  padding: 26px;
}

.category-strip {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-bottom: 20px;
}

.category-chip {
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.78);
  border-radius: 999px;
  padding: 10px 16px;
  cursor: pointer;
  font: inherit;
}

.category-chip.active {
  color: #fff;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
}

.filter-bar {
  margin-bottom: 18px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: 280px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
</style>
