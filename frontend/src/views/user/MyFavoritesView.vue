<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../../api/modules';
import type { Announcement, ServiceItem } from '../../api/types';

const router = useRouter();
const loading = ref(false);
const services = ref<ServiceItem[]>([]);
const announcements = ref<Announcement[]>([]);
const serviceKeyword = ref('');
const announcementKeyword = ref('');

const servicePager = reactive({
  pageNum: 1,
  pageSize: 6,
});

const announcementPager = reactive({
  pageNum: 1,
  pageSize: 6,
});

const filteredServices = computed(() => {
  const keyword = serviceKeyword.value.trim().toLowerCase();
  return services.value.filter((item) => !keyword || [item.name, item.status].some((value) => String(value ?? '').toLowerCase().includes(keyword)));
});

const filteredAnnouncements = computed(() => {
  const keyword = announcementKeyword.value.trim().toLowerCase();
  return announcements.value.filter((item) => !keyword || [item.title, item.summary, item.content].some((value) => String(value ?? '').toLowerCase().includes(keyword)));
});

const pagedServices = computed(() => {
  const start = (servicePager.pageNum - 1) * servicePager.pageSize;
  return filteredServices.value.slice(start, start + servicePager.pageSize);
});

const pagedAnnouncements = computed(() => {
  const start = (announcementPager.pageNum - 1) * announcementPager.pageSize;
  return filteredAnnouncements.value.slice(start, start + announcementPager.pageSize);
});

function handleServicePageChange(pageNum: number) {
  servicePager.pageNum = pageNum;
}

function handleServicePageSizeChange(pageSize: number) {
  servicePager.pageSize = pageSize;
  servicePager.pageNum = 1;
}

function handleServiceSearch() {
  servicePager.pageNum = 1;
}

function resetServiceSearch() {
  serviceKeyword.value = '';
  handleServiceSearch();
}

function handleAnnouncementPageChange(pageNum: number) {
  announcementPager.pageNum = pageNum;
}

function handleAnnouncementPageSizeChange(pageSize: number) {
  announcementPager.pageSize = pageSize;
  announcementPager.pageNum = 1;
}

function handleAnnouncementSearch() {
  announcementPager.pageNum = 1;
}

function resetAnnouncementSearch() {
  announcementKeyword.value = '';
  handleAnnouncementSearch();
}

async function loadData() {
  loading.value = true;
  try {
    const [serviceRes, serviceFavoriteRes, announcementRes, announcementFavoriteRes] = await Promise.all([
      api.serviceItems(),
      api.favoriteServiceItemIds(),
      api.announcements(),
      api.favoriteAnnouncementIds(),
    ]);

    const serviceIdSet = new Set(serviceFavoriteRes.data.data);
    const announcementIdSet = new Set(announcementFavoriteRes.data.data);
    services.value = serviceRes.data.data.filter((item) => serviceIdSet.has(item.id));
    announcements.value = announcementRes.data.data.filter((item) => announcementIdSet.has(item.id));
  } finally {
    loading.value = false;
  }
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section class="section-grid">
      <article class="panel favorite-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">服务项目收藏</h2>
            <p class="panel-subtitle">这里保留收藏的服务项目。</p>
          </div>
          <el-button plain @click="router.push('/services')">去服务页</el-button>
        </div>

        <div class="toolbar filter-bar">
          <el-input v-model="serviceKeyword" placeholder="搜索收藏服务" clearable @keyup.enter="handleServiceSearch" />
          <el-button text @click="resetServiceSearch">重置</el-button>
        </div>

        <div class="soft-list">
          <div v-for="item in pagedServices" :key="item.id" class="soft-list-item">
            <strong>{{ item.name }}</strong>
            <p>{{ item.durationMin }} 分钟 · ¥{{ Number(item.price).toFixed(2) }}</p>
          </div>
          <el-empty v-if="filteredServices.length === 0" description="暂无匹配的收藏服务" />
        </div>

        <div v-if="filteredServices.length > 0" class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="servicePager.pageNum"
            :page-size="servicePager.pageSize"
            :page-sizes="[6, 12, 24]"
            :total="filteredServices.length"
            @current-change="handleServicePageChange"
            @size-change="handleServicePageSizeChange"
          />
        </div>
      </article>

      <article class="panel favorite-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">资讯收藏</h2>
            <p class="panel-subtitle">可从资讯列表或资讯详情页收藏，便于回看运营内容。</p>
          </div>
          <el-button plain @click="router.push('/news')">去资讯页</el-button>
        </div>

        <div class="toolbar filter-bar">
          <el-input v-model="announcementKeyword" placeholder="搜索收藏资讯" clearable @keyup.enter="handleAnnouncementSearch" />
          <el-button text @click="resetAnnouncementSearch">重置</el-button>
        </div>

        <div class="soft-list">
          <button v-for="item in pagedAnnouncements" :key="item.id" class="favorite-news" @click="router.push(`/news/${item.id}`)">
            <strong>{{ item.title }}</strong>
            <p>{{ item.summary }}</p>
          </button>
          <el-empty v-if="filteredAnnouncements.length === 0" description="暂无匹配的收藏资讯" />
        </div>

        <div v-if="filteredAnnouncements.length > 0" class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="announcementPager.pageNum"
            :page-size="announcementPager.pageSize"
            :page-sizes="[6, 12, 24]"
            :total="filteredAnnouncements.length"
            @current-change="handleAnnouncementPageChange"
            @size-change="handleAnnouncementPageSizeChange"
          />
        </div>
      </article>
    </section>
  </div>
</template>

<style scoped>
.favorite-panel {
  padding: 26px;
}

.filter-bar {
  margin-bottom: 16px;
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: 220px;
}

.favorite-news {
  width: 100%;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  background: rgba(255, 250, 244, 0.84);
  text-align: left;
  cursor: pointer;
}

.favorite-news strong,
.favorite-news p {
  margin: 0;
}

.favorite-news p {
  margin-top: 10px;
  color: var(--muted);
  line-height: 1.7;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}
</style>
