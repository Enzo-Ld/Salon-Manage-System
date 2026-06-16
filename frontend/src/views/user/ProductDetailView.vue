<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductComment, ProductDetail } from '../../api/types';

const route = useRoute();
const router = useRouter();

const product = ref<ProductDetail | null>(null);
const comments = ref<ProductComment[]>([]);
const quantity = ref(1);
const loading = ref(false);
const directBuySubmitting = ref(false);
const commentSubmitting = ref(false);
const commentSectionRef = ref<HTMLElement | null>(null);
const commentPager = reactive({
  pageNum: 1,
  pageSize: 5,
});
const commentForm = ref({
  rating: 5,
  content: '',
});

const total = computed(() => {
  if (!product.value) return 0;
  return Number(product.value.price) * quantity.value;
});

const visibleComments = computed(() => comments.value.filter((item) => item.status === 'APPROVED' || item.mine));
const pagedComments = computed(() => {
  const start = (commentPager.pageNum - 1) * commentPager.pageSize;
  return visibleComments.value.slice(start, start + commentPager.pageSize);
});
const canSubmitComment = computed(() => product.value?.canComment ?? false);
const commentTip = computed(() => {
  if (!product.value) return '';
  if (product.value.canComment) return '你已有已完成订单，现在可以评价这个商品。';
  if (product.value.hasCommented) return '你已经评价过这个商品了。';
  return '商品评价会在订单完成后开放。';
});

async function loadData() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    const [detailRes, commentRes] = await Promise.all([api.productDetail(id), api.productComments(id)]);
    product.value = detailRes.data.data;
    comments.value = commentRes.data.data;
    quantity.value = Math.max(1, Math.min(quantity.value, product.value.stock || 1));
    const maxPage = Math.max(1, Math.ceil(visibleComments.value.length / commentPager.pageSize));
    if (commentPager.pageNum > maxPage) {
      commentPager.pageNum = maxPage;
    }

    if (route.query.action === 'comment') {
      await nextTick();
      commentSectionRef.value?.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  } finally {
    loading.value = false;
  }
}

async function addToCart() {
  if (!product.value) return;
  await api.addCartItem({ productId: product.value.id, quantity: quantity.value });
  ElMessage.success('已加入购物车');
}

async function directBuy() {
  if (!product.value) return;
  if ((product.value.stock || 0) < quantity.value) {
    ElMessage.warning('当前库存不足，请调整购买数量');
    return;
  }

  directBuySubmitting.value = true;
  try {
    const { data: addressData } = await api.userAddresses();
    const addresses = addressData.data;
    const targetAddress = addresses.find((item) => item.isDefault === 1) ?? addresses[0];
    if (!targetAddress?.id) {
      ElMessage.warning('请先到个人资料完善收货地址，再直接购买');
      router.push('/profile');
      return;
    }

    const { data } = await api.createProductOrder({
      addressId: targetAddress.id,
      items: [{ productId: product.value.id, quantity: quantity.value }],
    });
    ElMessage.success('订单已创建，正在进入支付页');
    router.push(`/shop/orders/${data.data.id}?autopay=1`);
  } finally {
    directBuySubmitting.value = false;
  }
}

async function toggleFavorite() {
  if (!product.value) return;
  const { data } = await api.toggleProductFavorite(product.value.id);
  product.value.favorited = data.data.active;
  product.value.favoriteCount = data.data.count;
  ElMessage.success(data.data.active ? '已加入收藏' : '已取消收藏');
}

async function submitComment() {
  if (!product.value) return;
  if (!product.value.canComment) {
    ElMessage.warning(commentTip.value);
    return;
  }
  if (!commentForm.value.content.trim()) {
    ElMessage.warning('请先填写评价内容');
    return;
  }
  commentSubmitting.value = true;
  try {
    await api.submitProductComment(product.value.id, {
      rating: commentForm.value.rating,
      content: commentForm.value.content.trim(),
    });
    ElMessage.success('评价提交成功');
    commentForm.value = { rating: 5, content: '' };
    await loadData();
  } finally {
    commentSubmitting.value = false;
  }
}

function startConsult() {
  if (!product.value?.consultantUserId) {
    ElMessage.warning('当前暂无可用顾问');
    return;
  }
  router.push({
    path: '/chat',
    query: {
      receiverId: String(product.value.consultantUserId),
      productId: String(product.value.id),
      draft: `你好，我想咨询一下商品：${product.value.name}`,
    },
  });
}

function handleCommentPageChange(pageNum: number) {
  commentPager.pageNum = pageNum;
}

function handleCommentSizeChange(pageSize: number) {
  commentPager.pageSize = pageSize;
  commentPager.pageNum = 1;
}

onMounted(loadData);

watch(
  () => route.params.id,
  async (value, oldValue) => {
    if (value !== oldValue) {
      await loadData();
    }
  },
);
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section v-if="product" class="panel detail-panel">
      <div class="detail-copy">
        <p class="detail-kicker">{{ product.categoryName }}</p>
        <h2>{{ product.name }}</h2>
        <p class="detail-body">商品详情页已和收藏、购物车、售前咨询、订单评价串联起来，用户可从这里直接完成完整商城流程。</p>

        <div class="detail-meta">
          <div>
            <span>状态</span>
            <strong>{{ product.statusLabel }}</strong>
          </div>
          <div>
            <span>库存</span>
            <strong>{{ product.stock }}</strong>
          </div>
          <div>
            <span>评分</span>
            <strong>{{ product.averageRating || 0 }}</strong>
          </div>
          <div>
            <span>收藏</span>
            <strong>{{ product.favoriteCount }}</strong>
          </div>
          <div>
            <span>评论数</span>
            <strong>{{ product.commentCount }}</strong>
          </div>
          <div>
            <span>顾问</span>
            <strong>{{ product.consultantName || '待分配' }}</strong>
          </div>
        </div>
      </div>

      <div class="purchase-box">
        <h3>购买操作</h3>
        <p>支持直接下单或先加入购物车，后续可统一结算多个商品和收货地址。</p>
        <el-form label-position="top">
          <el-form-item label="购买数量">
            <el-input-number v-model="quantity" :min="1" :max="product.stock || 1" />
          </el-form-item>
        </el-form>
        <div class="purchase-total">预计金额：¥{{ total.toFixed(2) }}</div>
        <div class="toolbar">
          <el-button type="warning" :loading="directBuySubmitting" @click="directBuy">直接购买</el-button>
          <el-button type="warning" plain @click="addToCart">加入购物车</el-button>
          <el-button plain @click="router.push('/shop/cart')">前往购物车</el-button>
          <el-button @click="toggleFavorite">{{ product.favorited ? '取消收藏' : '收藏商品' }}</el-button>
          <el-button plain @click="startConsult">售前咨询</el-button>
          <el-button @click="router.push('/shop')">返回商品列表</el-button>
        </div>
      </div>
    </section>

    <section v-if="product" ref="commentSectionRef" class="panel comment-panel">
      <div class="panel-header">
        <div>
          <h3 class="panel-title">商品评价</h3>
          <p class="panel-subtitle">{{ commentTip }}</p>
        </div>
      </div>

      <div class="section-grid">
        <article class="comment-form-box">
          <h4>写评价</h4>
          <el-alert :title="commentTip" :type="canSubmitComment ? 'success' : 'info'" :closable="false" />
          <el-form label-position="top">
            <el-form-item label="评分">
              <el-rate v-model="commentForm.rating" :disabled="!canSubmitComment" />
            </el-form-item>
            <el-form-item label="评价内容">
              <el-input v-model="commentForm.content" type="textarea" :rows="5" maxlength="500" show-word-limit :disabled="!canSubmitComment" placeholder="分享一下你的使用感受、推荐场景或商品建议" />
            </el-form-item>
          </el-form>
          <div class="toolbar">
            <el-button type="warning" :loading="commentSubmitting" :disabled="!canSubmitComment" @click="submitComment">提交评价</el-button>
          </div>
        </article>

        <article class="comment-feed">
          <el-empty v-if="visibleComments.length === 0" description="暂时还没有商品评价" />
          <div v-else class="comment-list">
            <div v-for="comment in pagedComments" :key="comment.id" class="comment-card">
              <div class="comment-top">
                <div>
                  <strong>{{ comment.username }}</strong>
                  <p>{{ comment.createdAt }}</p>
                </div>
                <div class="comment-badges">
                  <el-rate :model-value="comment.rating" disabled />
                  <el-tag v-if="comment.mine" type="success" effect="plain">我的评价</el-tag>
                </div>
              </div>
              <p class="comment-content">{{ comment.content }}</p>
            </div>
            <div class="pagination-wrap">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :current-page="commentPager.pageNum"
                :page-size="commentPager.pageSize"
                :page-sizes="[5, 10, 20]"
                :total="visibleComments.length"
                @current-change="handleCommentPageChange"
                @size-change="handleCommentSizeChange"
              />
            </div>
          </div>
        </article>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-panel,
.comment-panel {
  padding: 28px;
}

.detail-panel {
  display: grid;
  grid-template-columns: 1.2fr 0.8fr;
  gap: 22px;
}

.detail-kicker {
  margin: 0 0 10px;
  color: var(--accent-deep);
  letter-spacing: 0.16em;
  text-transform: uppercase;
  font-size: 12px;
}

.detail-copy h2,
.detail-copy p,
.purchase-box h3,
.purchase-box p {
  margin: 0;
}

.detail-body {
  margin-top: 16px;
  color: var(--muted);
  line-height: 1.8;
  max-width: 48ch;
}

.detail-meta {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  margin-top: 22px;
}

.detail-meta div,
.purchase-box,
.comment-form-box,
.comment-card {
  padding: 18px;
  border-radius: 22px;
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.84);
}

.detail-meta span {
  display: block;
  color: var(--muted);
  margin-bottom: 10px;
  font-size: 13px;
}

.purchase-box {
  display: grid;
  gap: 18px;
}

.purchase-box p {
  color: var(--muted);
  line-height: 1.7;
}

.purchase-total {
  font-size: 24px;
  font-weight: 700;
}

.comment-form-box h4,
.comment-content {
  margin: 0;
}

.comment-feed,
.comment-list {
  display: grid;
  gap: 14px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.comment-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 14px;
}

.comment-top p {
  margin: 8px 0 0;
  color: var(--muted);
  font-size: 13px;
}

.comment-badges {
  display: grid;
  justify-items: end;
  gap: 10px;
}

.comment-content {
  margin-top: 14px;
  line-height: 1.8;
  color: var(--text);
}

@media (max-width: 960px) {
  .detail-panel,
  .section-grid,
  .detail-meta {
    grid-template-columns: 1fr;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}
</style>
