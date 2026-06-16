<script setup lang="ts">
import { onMounted, ref } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../api/modules';
import type { Announcement } from '../api/types';
import { resolveAssetUrl } from '../utils/avatar';

const route = useRoute();
const router = useRouter();
const loading = ref(false);
const article = ref<Announcement | null>(null);
const favorited = ref(false);

async function loadData() {
  loading.value = true;
  try {
    const id = Number(route.params.id);
    const [detailRes, favoriteRes] = await Promise.all([api.announcementDetail(id), api.favoriteAnnouncementIds()]);
    article.value = detailRes.data.data;
    favorited.value = favoriteRes.data.data.includes(id);
  } finally {
    loading.value = false;
  }
}

async function toggleFavorite() {
  if (!article.value) return;
  const { data } = await api.toggleAnnouncementFavorite(article.value.id);
  favorited.value = data.data.active;
  ElMessage.success(data.data.active ? '已收藏资讯' : '已取消收藏');
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section v-if="article" class="panel detail-panel">
      <div class="detail-cover" :style="{ backgroundImage: article.coverImage ? `linear-gradient(120deg, rgba(36, 21, 9, 0.16), rgba(36, 21, 9, 0.56)), url(${resolveAssetUrl(article.coverImage)})` : 'linear-gradient(135deg, rgba(186, 140, 66, 0.24), rgba(122, 86, 37, 0.32))' }">
        <div class="detail-cover-copy">
          <p>新闻资讯</p>
          <h1>{{ article.title }}</h1>
          <span>{{ article.publishTime || article.createdAt }}</span>
        </div>
      </div>

      <div class="detail-content">
        <div class="toolbar">
          <el-button plain @click="router.push('/news')">返回资讯列表</el-button>
          <el-button type="warning" plain @click="toggleFavorite">{{ favorited ? '已收藏' : '收藏资讯' }}</el-button>
        </div>
        <blockquote>{{ article.summary }}</blockquote>
        <div class="article-body">{{ article.content }}</div>
      </div>
    </section>
  </div>
</template>

<style scoped>
.detail-panel {
  overflow: hidden;
}

.detail-cover {
  min-height: 320px;
  background-size: cover;
  background-position: center;
}

.detail-cover-copy {
  min-height: 320px;
  display: flex;
  flex-direction: column;
  justify-content: flex-end;
  gap: 12px;
  padding: 32px;
  color: #fff;
}

.detail-cover-copy p,
.detail-cover-copy h1,
.detail-cover-copy span {
  margin: 0;
}

.detail-cover-copy p {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
}

.detail-cover-copy h1 {
  max-width: 18ch;
  font-size: 42px;
  line-height: 1.14;
}

.detail-content {
  display: grid;
  gap: 20px;
  padding: 28px 30px 32px;
}

.detail-content blockquote {
  margin: 0;
  padding: 18px 20px;
  border-radius: 20px;
  background: rgba(255, 250, 242, 0.82);
  border: 1px solid rgba(122, 86, 37, 0.12);
  color: var(--accent-deep);
  line-height: 1.8;
}

.article-body {
  white-space: pre-wrap;
  line-height: 1.9;
  color: var(--text);
}

@media (max-width: 720px) {
  .detail-cover-copy h1 {
    font-size: 32px;
  }
}
</style>
