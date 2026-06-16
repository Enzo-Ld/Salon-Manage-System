<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductCategory, ProductDetail } from '../../api/types';
import AdminDashboardView from './AdminDashboardView.vue';
import AdminProductOrderView from './AdminProductOrderView.vue';

type TabName = 'products' | 'categories' | 'orders' | 'analytics';

const route = useRoute();
const router = useRouter();
const activeTab = ref<TabName>((route.query.tab as TabName) || 'products');
const loading = ref(false);
const categoryLoading = ref(false);
const drawerVisible = ref(false);
const categoryDialogVisible = ref(false);
const selectedProductIds = ref<number[]>([]);

const categories = ref<ProductCategory[]>([]);
const products = ref<ProductDetail[]>([]);
const productTotal = ref(0);
const categoryTotal = ref(0);

const productPager = reactive({ pageNum: 1, pageSize: 10 });
const categoryPager = reactive({ pageNum: 1, pageSize: 10 });
const productFilters = reactive({
  keyword: '',
  categoryId: undefined as number | undefined,
  status: '',
  lowStock: false,
});
const categoryFilters = reactive({
  keyword: '',
  status: '',
});

const productForm = reactive<Partial<ProductDetail>>({
  categoryId: undefined,
  name: '',
  intro: '',
  imageUrl: '',
  price: 0,
  stock: 0,
  status: 'ON',
  sortNo: 0,
});
const categoryForm = reactive<Partial<ProductCategory>>({
  name: '',
  sortNo: 0,
  status: 'ON',
});

const productStats = computed(() => ({
  total: productTotal.value,
  active: products.value.filter((item) => item.status === 'ON').length,
  lowStock: products.value.filter((item) => Number(item.stock || 0) <= 10).length,
  off: products.value.filter((item) => item.status === 'OFF').length,
}));

function statusType(status?: string) {
  return status === 'ON' || status === 'PUBLISHED' ? 'success' : 'info';
}

function money(value?: number) {
  return `¥ ${Number(value || 0).toFixed(2)}`;
}

function syncTabToRoute(tab: TabName) {
  if (route.query.tab === tab) return;
  void router.replace({ path: '/admin/catalog', query: { tab } });
}

async function loadCategories() {
  categoryLoading.value = true;
  try {
    const { data } = await api.adminProductCategoriesPage({
      pageNum: categoryPager.pageNum,
      pageSize: categoryPager.pageSize,
      keyword: categoryFilters.keyword.trim() || undefined,
      status: categoryFilters.status || undefined,
    });
    categories.value = data.data.records;
    categoryTotal.value = data.data.total;
  } finally {
    categoryLoading.value = false;
  }
}

async function loadAllCategories() {
  const { data } = await api.adminProductCategories();
  categories.value = data.data;
}

async function loadProducts() {
  loading.value = true;
  try {
    const { data } = await api.productsPage({
      pageNum: productPager.pageNum,
      pageSize: productPager.pageSize,
      keyword: productFilters.keyword.trim() || undefined,
      categoryId: productFilters.categoryId,
      status: productFilters.status || undefined,
      lowStock: productFilters.lowStock || undefined,
    });
    products.value = data.data.records;
    productTotal.value = data.data.total;
  } finally {
    loading.value = false;
  }
}

async function refreshCatalog() {
  await Promise.all([loadAllCategories(), loadProducts()]);
}

function openProductDrawer(row?: ProductDetail) {
  Object.assign(productForm, {
    id: row?.id,
    categoryId: row?.categoryId,
    name: row?.name || '',
    intro: row?.intro || '',
    imageUrl: row?.imageUrl || '',
    price: row?.price || 0,
    stock: row?.stock || 0,
    status: row?.status || 'ON',
    sortNo: row?.sortNo || 0,
  });
  drawerVisible.value = true;
}

async function saveProduct() {
  await api.saveProduct(productForm);
  ElMessage.success('商品信息已保存');
  drawerVisible.value = false;
  await refreshCatalog();
}

async function changeProductStatus(row: ProductDetail, status: string) {
  if (!row.id) return;
  await api.updateProductStatus(row.id, status);
  ElMessage.success(status === 'ON' ? '商品已上架' : '商品已下架');
  await loadProducts();
}

async function batchStatus(status: string) {
  if (!selectedProductIds.value.length) {
    ElMessage.warning('请先选择商品');
    return;
  }
  await api.updateProductStatuses(selectedProductIds.value, status);
  ElMessage.success(status === 'ON' ? '已批量上架' : '已批量下架');
  selectedProductIds.value = [];
  await loadProducts();
}

async function removeProduct(row: ProductDetail) {
  if (!row.id) return;
  await ElMessageBox.confirm('若商品已有订单记录，系统会执行保护性下架；否则会删除商品。确认继续吗？', '删除商品', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  });
  await api.deleteProduct(row.id);
  ElMessage.success('商品已处理');
  await loadProducts();
}

function openCategoryDialog(row?: ProductCategory) {
  Object.assign(categoryForm, {
    id: row?.id,
    name: row?.name || '',
    sortNo: row?.sortNo || 0,
    status: row?.status || 'ON',
  });
  categoryDialogVisible.value = true;
}

async function saveCategory() {
  await api.saveProductCategory(categoryForm);
  ElMessage.success('商品分类已保存');
  categoryDialogVisible.value = false;
  await Promise.all([loadCategories(), loadAllCategories()]);
}

async function removeCategory(row: ProductCategory) {
  if (!row.id) return;
  await ElMessageBox.confirm('仅空分类可以删除；已有商品的分类会被后端保护。确认继续吗？', '删除分类', {
    type: 'warning',
    confirmButtonText: '确认',
    cancelButtonText: '取消',
  });
  await api.deleteProductCategory(row.id);
  ElMessage.success('分类已删除');
  await Promise.all([loadCategories(), loadAllCategories()]);
}

function handleProductSelection(rows: ProductDetail[]) {
  selectedProductIds.value = rows.map((item) => item.id).filter((id): id is number => Boolean(id));
}

function resetProductFilters() {
  Object.assign(productFilters, { keyword: '', categoryId: undefined, status: '', lowStock: false });
  productPager.pageNum = 1;
  void loadProducts();
}

function resetCategoryFilters() {
  Object.assign(categoryFilters, { keyword: '', status: '' });
  categoryPager.pageNum = 1;
  void loadCategories();
}

watch(activeTab, (tab) => syncTabToRoute(tab));
watch(
  () => route.query.tab,
  (tab) => {
    if (tab && tab !== activeTab.value) {
      activeTab.value = tab as TabName;
    }
  },
);

onMounted(async () => {
  await Promise.all([loadAllCategories(), loadProducts(), loadCategories()]);
});
</script>

<template>
  <div class="admin-page">
    <section class="admin-toolbar">
      <div>
        <p class="admin-kicker">商品运营</p>
        <h2>商品、分类、库存、订单和销售分析集中处理</h2>
      </div>
      <div class="toolbar">
        <el-button type="primary" @click="openProductDrawer()">新增商品</el-button>
        <el-button plain @click="openCategoryDialog()">新增分类</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab" class="ops-tabs">
      <el-tab-pane label="商品列表" name="products">
        <section class="admin-panel">
          <div class="stat-row">
            <div class="stat-item"><span>商品总数</span><strong>{{ productStats.total }}</strong></div>
            <div class="stat-item"><span>本页上架</span><strong>{{ productStats.active }}</strong></div>
            <div class="stat-item"><span>本页低库存</span><strong>{{ productStats.lowStock }}</strong></div>
            <div class="stat-item"><span>本页下架</span><strong>{{ productStats.off }}</strong></div>
          </div>

          <div class="table-toolbar">
            <el-input v-model="productFilters.keyword" placeholder="商品名称 / 简介" clearable @keyup.enter="loadProducts" />
            <el-select v-model="productFilters.categoryId" placeholder="分类" clearable @change="loadProducts">
              <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
            </el-select>
            <el-select v-model="productFilters.status" placeholder="状态" clearable @change="loadProducts">
              <el-option label="上架中" value="ON" />
              <el-option label="已下架" value="OFF" />
            </el-select>
            <el-checkbox v-model="productFilters.lowStock" @change="loadProducts">低库存</el-checkbox>
            <el-button plain @click="loadProducts">查询</el-button>
            <el-button text @click="resetProductFilters">重置</el-button>
          </div>

          <div class="bulk-bar" v-if="selectedProductIds.length">
            已选择 {{ selectedProductIds.length }} 个商品
            <el-button size="small" type="success" plain @click="batchStatus('ON')">批量上架</el-button>
            <el-button size="small" type="warning" plain @click="batchStatus('OFF')">批量下架</el-button>
          </div>

          <el-table :data="products" stripe v-loading="loading" @selection-change="handleProductSelection">
            <el-table-column type="selection" width="44" />
            <el-table-column prop="id" label="ID" width="76" />
            <el-table-column label="商品" min-width="220">
              <template #default="{ row }">
                <div class="name-cell">
                  <strong>{{ row.name }}</strong>
                  <span>{{ row.intro || '暂无简介' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="categoryName" label="分类" min-width="120" />
            <el-table-column label="价格" width="120">
              <template #default="{ row }">{{ money(row.price) }}</template>
            </el-table-column>
            <el-table-column prop="stock" label="库存" width="100" />
            <el-table-column prop="sortNo" label="排序" width="90" />
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="plain">{{ row.statusLabel || row.status }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="250" fixed="right">
              <template #default="{ row }">
                <div class="toolbar nowrap">
                  <el-button size="small" @click="openProductDrawer(row)">编辑</el-button>
                  <el-button v-if="row.status === 'ON'" size="small" type="warning" plain @click="changeProductStatus(row, 'OFF')">下架</el-button>
                  <el-button v-else size="small" type="success" plain @click="changeProductStatus(row, 'ON')">上架</el-button>
                  <el-button size="small" type="danger" plain @click="removeProduct(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="productPager.pageNum"
              :page-size="productPager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="productTotal"
              @current-change="(page: number) => { productPager.pageNum = page; loadProducts(); }"
              @size-change="(size: number) => { productPager.pageSize = size; productPager.pageNum = 1; loadProducts(); }"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="分类管理" name="categories">
        <section class="admin-panel">
          <div class="table-toolbar">
            <el-input v-model="categoryFilters.keyword" placeholder="分类名称" clearable @keyup.enter="loadCategories" />
            <el-select v-model="categoryFilters.status" placeholder="状态" clearable @change="loadCategories">
              <el-option label="启用" value="ON" />
              <el-option label="停用" value="OFF" />
            </el-select>
            <el-button plain @click="loadCategories">查询</el-button>
            <el-button text @click="resetCategoryFilters">重置</el-button>
            <el-button type="primary" @click="openCategoryDialog()">新增分类</el-button>
          </div>

          <el-table :data="categories" stripe v-loading="categoryLoading">
            <el-table-column prop="id" label="ID" width="90" />
            <el-table-column prop="name" label="分类名称" min-width="180" />
            <el-table-column prop="sortNo" label="排序" width="120" />
            <el-table-column label="状态" width="120">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="plain">{{ row.status === 'OFF' ? '停用' : '启用' }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="180" fixed="right">
              <template #default="{ row }">
                <div class="toolbar nowrap">
                  <el-button size="small" @click="openCategoryDialog(row)">编辑</el-button>
                  <el-button size="small" type="danger" plain @click="removeCategory(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="categoryPager.pageNum"
              :page-size="categoryPager.pageSize"
              :page-sizes="[10, 20, 50]"
              :total="categoryTotal"
              @current-change="(page: number) => { categoryPager.pageNum = page; loadCategories(); }"
              @size-change="(size: number) => { categoryPager.pageSize = size; categoryPager.pageNum = 1; loadCategories(); }"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="订单售后" name="orders">
        <AdminProductOrderView />
      </el-tab-pane>

      <el-tab-pane label="销售分析" name="analytics">
        <AdminDashboardView />
      </el-tab-pane>
    </el-tabs>

    <el-drawer v-model="drawerVisible" :title="productForm.id ? '编辑商品' : '新增商品'" size="520px">
      <el-form label-position="top">
        <el-form-item label="商品分类">
          <el-select v-model="productForm.categoryId" filterable>
            <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
        </el-form-item>
        <el-form-item label="商品名称"><el-input v-model="productForm.name" maxlength="80" /></el-form-item>
        <el-form-item label="商品简介"><el-input v-model="productForm.intro" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
        <el-form-item label="图片地址"><el-input v-model="productForm.imageUrl" placeholder="/uploads/... 或 https://..." /></el-form-item>
        <div class="form-grid">
          <el-form-item label="价格"><el-input-number v-model="productForm.price" :min="0" :precision="2" /></el-form-item>
          <el-form-item label="库存"><el-input-number v-model="productForm.stock" :min="0" :step="1" /></el-form-item>
        </div>
        <div class="form-grid">
          <el-form-item label="排序"><el-input-number v-model="productForm.sortNo" :min="0" :step="1" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="productForm.status">
              <el-option label="上架中" value="ON" />
              <el-option label="已下架" value="OFF" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="toolbar end">
          <el-button @click="drawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveProduct">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="categoryDialogVisible" :title="categoryForm.id ? '编辑分类' : '新增分类'" width="420px">
      <el-form label-position="top">
        <el-form-item label="分类名称"><el-input v-model="categoryForm.name" maxlength="64" /></el-form-item>
        <el-form-item label="排序"><el-input-number v-model="categoryForm.sortNo" :min="0" :step="1" /></el-form-item>
        <el-form-item label="状态">
          <el-select v-model="categoryForm.status">
            <el-option label="启用" value="ON" />
            <el-option label="停用" value="OFF" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar end">
          <el-button @click="categoryDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveCategory">保存</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.admin-page {
  display: grid;
  gap: 16px;
}

.admin-toolbar,
.admin-panel {
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-strong);
  padding: 18px;
}

.admin-toolbar {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 16px;
}

.admin-toolbar h2 {
  margin: 4px 0 0;
  font-size: 22px;
}

.admin-kicker {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.ops-tabs {
  min-width: 0;
}

.stat-row {
  display: grid;
  grid-template-columns: repeat(4, minmax(120px, 1fr));
  gap: 12px;
  margin-bottom: 14px;
}

.stat-item {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 12px 14px;
  background: var(--surface);
}

.stat-item span,
.name-cell span {
  display: block;
  color: var(--muted);
  font-size: 13px;
}

.stat-item strong {
  display: block;
  margin-top: 4px;
  font-size: 24px;
}

.table-toolbar,
.toolbar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}

.table-toolbar {
  margin-bottom: 14px;
}

.table-toolbar .el-input {
  width: 240px;
}

.table-toolbar .el-select {
  width: 160px;
}

.bulk-bar {
  display: flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 12px;
  padding: 10px 12px;
  border: 1px solid #b6d4fe;
  border-radius: 8px;
  background: #f4f8ff;
  color: #274c77;
}

.name-cell {
  display: grid;
  gap: 2px;
}

.nowrap {
  flex-wrap: nowrap;
}

.pagination-wrap {
  margin-top: 14px;
  display: flex;
  justify-content: flex-end;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.end {
  justify-content: flex-end;
}

@media (max-width: 900px) {
  .admin-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .stat-row,
  .form-grid {
    grid-template-columns: 1fr;
  }

  .table-toolbar .el-input,
  .table-toolbar .el-select {
    width: 100%;
  }
}
</style>
