<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../api/modules';
import type { Announcement } from '../api/types';
import { resolveAssetUrl } from '../utils/avatar';

const router = useRouter();
const loading = ref(false);
const announcements = ref<Announcement[]>([]);
const favoriteIds = ref<number[]>([]);
const keyword = ref('');
const favoriteOnly = ref(false);
const pager = reactive({
  pageNum: 1,
  pageSize: 9,
});

const filteredAnnouncements = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return announcements.value.filter((item) => {
    const keywordMatch =
      !normalizedKeyword ||
      [item.title, item.summary, item.content, item.publishTime, item.createdAt].some((value) =>
        String(value ?? '').toLowerCase().includes(normalizedKeyword),
      );
    const favoriteMatch = !favoriteOnly.value || isFavorited(item.id);
    return keywordMatch && favoriteMatch;
  });
});

const pagedAnnouncements = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredAnnouncements.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  loading.value = true;
  try {
    const [announcementRes, favoriteRes] = await Promise.all([api.announcements(), api.favoriteAnnouncementIds()]);
    announcements.value = announcementRes.data.data;
    favoriteIds.value = favoriteRes.data.data;
  } finally {
    loading.value = false;
  }
}

function isFavorited(id: number) {
  return favoriteIds.value.includes(id);
}

async function toggleFavorite(item: Announcement) {
  const { data } = await api.toggleAnnouncementFavorite(item.id);
  if (data.data.active) {
    favoriteIds.value = [...new Set([...favoriteIds.value, item.id])];
  } else {
    favoriteIds.value = favoriteIds.value.filter((id) => id !== item.id);
  }
  ElMessage.success(data.data.active ? '已收藏资讯' : '已取消收藏');
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
  favoriteOnly.value = false;
  pager.pageNum = 1;
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel list-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">资讯中心</h2>
          <p class="panel-subtitle">展示后台已发布的新闻资讯、活动说明和系统公告，可直接收藏。</p>
        </div>
      </div>

      <div class="toolbar filter-bar">
        <el-input
          v-model="keyword"
          placeholder="搜索标题 / 摘要 / 内容"
          clearable
          @input="pager.pageNum = 1"
          @keyup.enter="pager.pageNum = 1"
          @clear="pager.pageNum = 1"
        />
        <el-switch v-model="favoriteOnly" active-text="只看已收藏" @change="pager.pageNum = 1" />
        <el-button text @click="resetFilters">重置</el-button>
      </div>

      <div class="news-grid" v-loading="loading">
        <article v-for="item in pagedAnnouncements" :key="item.id" class="news-item panel" @click="router.push(`/news/${item.id}`)">
          <div class="news-cover" :style="{ backgroundImage: item.coverImage ? `linear-gradient(120deg, rgba(36, 21, 9, 0.12), rgba(36, 21, 9, 0.42)), url(${resolveAssetUrl(item.coverImage)})` : 'linear-gradient(135deg, rgba(186, 140, 66, 0.22), rgba(122, 86, 37, 0.28))' }" />
          <div class="news-copy">
            <small>{{ item.publishTime || item.createdAt }}</small>
            <h3>{{ item.title }}</h3>
            <p>{{ item.summary }}</p>
            <div class="toolbar">
              <el-button type="warning" plain>查看详情</el-button>
              <el-button @click.stop="toggleFavorite(item)">{{ isFavorited(item.id) ? '已收藏' : '收藏资讯' }}</el-button>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-if="!loading && filteredAnnouncements.length === 0" description="暂无匹配的资讯" />

      <div v-if="filteredAnnouncements.length > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pager.pageNum"
          :page-size="pager.pageSize"
          :page-sizes="[9, 18, 36]"
          :total="filteredAnnouncements.length"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.list-panel {
  padding: 26px;
}

.filter-bar {
  margin-bottom: 20px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: min(340px, 100%);
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 20px;
}

.news-item {
  overflow: hidden;
  cursor: pointer;
}

.news-cover {
  min-height: 180px;
  background-size: cover;
  background-position: center;
}

.news-copy {
  display: grid;
  gap: 12px;
  padding: 20px;
}

.news-copy small {
  color: var(--muted);
}

.news-copy h3,
.news-copy p {
  margin: 0;
}

.news-copy p {
  color: var(--muted);
  line-height: 1.7;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
</style>
