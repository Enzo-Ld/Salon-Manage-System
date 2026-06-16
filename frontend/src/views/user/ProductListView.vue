<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductCategory, ProductDetail } from '../../api/types';

const router = useRouter();
const categories = ref<ProductCategory[]>([]);
const products = ref<ProductDetail[]>([]);
const favoriteIds = ref<number[]>([]);
const selectedCategory = ref<number | null>(null);
const favoritesOnly = ref(false);
const loading = ref(false);
const keyword = ref('');

const pager = reactive({
  pageNum: 1,
  pageSize: 12,
});

const filteredProducts = computed(() =>
  products.value.filter((item) => {
    const normalizedKeyword = keyword.value.trim().toLowerCase();
    const keywordMatch = !normalizedKeyword || [item.name, item.categoryName].some((value) => String(value ?? '').toLowerCase().includes(normalizedKeyword));
    const categoryMatch = !selectedCategory.value || item.categoryId === selectedCategory.value;
    const favoriteMatch = !favoritesOnly.value || favoriteIds.value.includes(item.id);
    return keywordMatch && categoryMatch && favoriteMatch;
  }),
);

const pagedProducts = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredProducts.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  loading.value = true;
  try {
    const [categoryRes, productRes, favoriteRes] = await Promise.all([
      api.productCategories(),
      api.products(),
      api.favoriteProductIds(),
    ]);
    categories.value = categoryRes.data.data;
    products.value = productRes.data.data.filter((item) => item.status === 'ON');
    favoriteIds.value = favoriteRes.data.data;
  } finally {
    loading.value = false;
  }
}

async function toggleFavorite(productId: number) {
  const { data } = await api.toggleProductFavorite(productId);
  if (data.data.active) {
    favoriteIds.value = Array.from(new Set([...favoriteIds.value, productId]));
    ElMessage.success('已加入收藏');
    return;
  }
  favoriteIds.value = favoriteIds.value.filter((id) => id !== productId);
  ElMessage.success('已取消收藏');
}

async function addToCart(product: ProductDetail) {
  await api.addCartItem({ productId: product.id, quantity: 1 });
  ElMessage.success(`已将“${product.name}”加入购物车`);
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

function resetFilters() {
  keyword.value = '';
  selectedCategory.value = null;
  favoritesOnly.value = false;
  pager.pageNum = 1;
}

watch([keyword, selectedCategory, favoritesOnly], () => {
  pager.pageNum = 1;
});

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel marketplace-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">商品商城</h2>
          <p class="panel-subtitle">支持收藏、加入购物车、多商品结算、地址选择和订单支付的完整商城闭环。</p>
        </div>
        <div class="toolbar">
          <el-switch v-model="favoritesOnly" active-text="只看收藏" />
          <el-button plain @click="router.push('/shop/cart')">进入购物车</el-button>
          <el-button plain @click="router.push('/profile')">管理资料与地址</el-button>
        </div>
      </div>

      <div class="category-strip">
        <button class="category-chip" :class="{ active: selectedCategory === null }" @click="selectedCategory = null">
          全部商品
        </button>
        <button
          v-for="item in categories"
          :key="item.id"
          class="category-chip"
          :class="{ active: selectedCategory === item.id }"
          @click="selectedCategory = item.id ?? null"
        >
          {{ item.name }}
        </button>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="keyword" placeholder="搜索商品名称 / 分类" clearable />
        <el-button text @click="resetFilters">重置筛选</el-button>
      </div>

      <el-empty v-if="filteredProducts.length === 0" description="当前筛选条件下暂无商品" />

      <div v-else class="product-grid">
        <article v-for="item in pagedProducts" :key="item.id" class="product-card">
          <div class="card-top">
            <div class="product-kicker">{{ item.categoryName }}</div>
            <el-tag v-if="favoriteIds.includes(item.id)" type="warning" effect="plain">已收藏</el-tag>
          </div>

          <h3>{{ item.name }}</h3>
          <p class="product-copy">库存 {{ item.stock }} 件，状态 {{ item.statusLabel }}</p>
          <p class="product-copy">评分 {{ item.averageRating || 0 }}，评价 {{ item.commentCount }} 条</p>
          <strong>¥{{ Number(item.price).toFixed(2) }}</strong>

          <div class="toolbar">
            <el-button type="warning" plain @click="router.push(`/shop/${item.id}`)">查看详情</el-button>
            <el-button @click="addToCart(item)">加入购物车</el-button>
            <el-button text @click="toggleFavorite(item.id)">
              {{ favoriteIds.includes(item.id) ? '取消收藏' : '收藏商品' }}
            </el-button>
          </div>
        </article>
      </div>

      <div v-if="filteredProducts.length > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pager.pageNum"
          :page-size="pager.pageSize"
          :page-sizes="[12, 24, 48]"
          :total="filteredProducts.length"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.marketplace-panel {
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
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: 280px;
}

.product-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 16px;
}

.product-card {
  display: grid;
  gap: 12px;
  padding: 22px;
  border-radius: 24px;
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.88);
}

.card-top {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.product-card h3,
.product-card p,
.product-card strong {
  margin: 0;
}

.product-copy {
  color: var(--muted);
  line-height: 1.6;
}

.product-kicker {
  font-size: 12px;
  color: var(--accent-deep);
  text-transform: uppercase;
  letter-spacing: 0.14em;
}

.pagination-wrap {
  margin-top: 20px;
  display: flex;
  justify-content: flex-end;
}
</style>
