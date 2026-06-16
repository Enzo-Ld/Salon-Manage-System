<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductCategory, ProductDetail } from '../../api/types';

const categories = ref<ProductCategory[]>([]);
const products = ref<ProductDetail[]>([]);
const pager = reactive({
  pageNum: 1,
  pageSize: 10,
  total: 0,
});
const filters = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  status: '',
});
const form = reactive<Partial<ProductDetail>>({
  categoryId: undefined,
  name: '',
  price: 0,
  stock: 0,
  status: 'ON',
});

const activeProducts = computed(() => products.value.filter((item) => item.status === 'ON').length);
const filteredProducts = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return products.value.filter((item) => {
    const matchKeyword = !keyword || [item.id, item.name, item.categoryName].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchCategory = !filters.categoryId || item.categoryId === filters.categoryId;
    const matchStatus = !filters.status || item.status === filters.status;
    return matchKeyword && matchCategory && matchStatus;
  });
});
const pagedProducts = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredProducts.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  const [categoryRes, productRes] = await Promise.all([
    api.productCategories(),
    api.products(),
  ]);
  categories.value = categoryRes.data.data;
  products.value = productRes.data.data;
  pager.total = productRes.data.data.length;
}

async function saveProduct() {
  await api.saveProduct(form);
  ElMessage.success('商品信息已保存');
  resetForm();
  await loadData();
}

function editProduct(row: ProductDetail) {
  Object.assign(form, {
    id: row.id,
    categoryId: row.categoryId,
    name: row.name,
    price: row.price,
    stock: row.stock,
    status: row.status,
  });
}

function resetForm() {
  Object.assign(form, { id: undefined, categoryId: undefined, name: '', price: 0, stock: 0, status: 'ON' });
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
  Object.assign(filters, { keyword: '', categoryId: undefined, status: '' });
  pager.pageNum = 1;
}

onMounted(loadData);
</script>

<template>
  <div class="page-shell">
    <section class="panel product-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">商品管理</h2>
          <p class="panel-subtitle">管理员维护商品名称、分类、库存、价格与上下架状态。</p>
        </div>
      </div>

      <div class="metric-grid product-metrics">
        <div class="metric-tile">
          <div class="metric-label">商品总数</div>
          <div class="metric-value">{{ pager.total }}</div>
        </div>
        <div class="metric-tile">
          <div class="metric-label">当前页上架</div>
          <div class="metric-value">{{ activeProducts }}</div>
        </div>
      </div>

      <div class="editor-grid">
        <el-form label-position="top">
          <el-form-item label="商品分类">
            <el-select v-model="form.categoryId">
              <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
          </el-form-item>
          <el-form-item label="商品名称"><el-input v-model="form.name" /></el-form-item>
          <el-form-item label="商品价格"><el-input v-model="form.price" /></el-form-item>
          <el-form-item label="库存"><el-input v-model="form.stock" /></el-form-item>
          <el-form-item label="状态"><el-input v-model="form.status" placeholder="ON / OFF" /></el-form-item>
          <div class="toolbar">
            <el-button type="warning" @click="saveProduct">保存商品</el-button>
            <el-button @click="resetForm">清空表单</el-button>
          </div>
        </el-form>

        <div class="table-block">
          <div class="toolbar filter-bar">
            <el-input v-model="filters.keyword" placeholder="商品 ID / 名称 / 分类" clearable @keyup.enter="handleSearch" />
            <el-select v-model="filters.categoryId" placeholder="商品分类" clearable @change="handleSearch">
              <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-select v-model="filters.status" placeholder="上下架状态" clearable @change="handleSearch">
              <el-option label="上架中" value="ON" />
              <el-option label="已下架" value="OFF" />
            </el-select>
            <el-button plain @click="handleSearch">查询</el-button>
            <el-button text @click="resetFilters">重置</el-button>
          </div>

          <el-table :data="pagedProducts" stripe>
          <el-table-column prop="id" label="ID" width="80" />
          <el-table-column prop="name" label="商品名称" min-width="150" />
          <el-table-column prop="categoryName" label="分类" min-width="120" />
          <el-table-column prop="price" label="价格" width="110" />
          <el-table-column prop="stock" label="库存" width="90" />
          <el-table-column prop="statusLabel" label="状态" width="110" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="editProduct(row)">编辑</el-button>
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
              :total="filteredProducts.length"
              @current-change="handlePageChange"
              @size-change="handleSizeChange"
            />
          </div>
        </div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.product-panel {
  padding: 26px;
}

.product-metrics {
  margin-bottom: 20px;
}

.editor-grid {
  display: grid;
  grid-template-columns: 320px 1fr;
  gap: 20px;
}

.table-block {
  display: grid;
  gap: 18px;
}

.filter-bar {
  flex-wrap: wrap;
}

.filter-bar .el-input,
.filter-bar .el-select {
  width: 190px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .editor-grid {
    grid-template-columns: 1fr;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}
</style>
