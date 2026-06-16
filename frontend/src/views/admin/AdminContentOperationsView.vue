<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { Announcement, Banner } from '../../api/types';

type TabName = 'banners' | 'news';

const route = useRoute();
const router = useRouter();
const activeTab = ref<TabName>((route.query.tab as TabName) || 'banners');
const loading = ref(false);
const bannerDialogVisible = ref(false);
const newsDrawerVisible = ref(false);
const previewVisible = ref(false);
const currentPreview = ref<Announcement | null>(null);

const banners = ref<Banner[]>([]);
const announcements = ref<Announcement[]>([]);
const pager = reactive({ pageNum: 1, pageSize: 8 });
const newsPager = reactive({ pageNum: 1, pageSize: 8 });
const bannerFilters = reactive({ keyword: '', status: '' });
const newsFilters = reactive({ keyword: '', publishStatus: '' });

const bannerForm = reactive<Partial<Banner>>({
  title: '',
  subtitle: '',
  imageUrl: '',
  linkUrl: '',
  sortNo: 1,
  status: 'ON',
});
const newsForm = reactive<Partial<Announcement>>({
  title: '',
  summary: '',
  coverImage: '',
  content: '',
  publishStatus: 'PUBLISHED',
  publishTime: '',
});

const filteredBanners = computed(() => {
  const keyword = bannerFilters.keyword.trim().toLowerCase();
  return banners.value.filter((item) => {
    const matchKeyword = !keyword || [item.title, item.subtitle, item.linkUrl].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchStatus = !bannerFilters.status || item.status === bannerFilters.status;
    return matchKeyword && matchStatus;
  });
});
const pagedBanners = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredBanners.value.slice(start, start + pager.pageSize);
});
const filteredNews = computed(() => {
  const keyword = newsFilters.keyword.trim().toLowerCase();
  return announcements.value.filter((item) => {
    const matchKeyword = !keyword || [item.title, item.summary, item.content].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchStatus = !newsFilters.publishStatus || item.publishStatus === newsFilters.publishStatus;
    return matchKeyword && matchStatus;
  });
});
const pagedNews = computed(() => {
  const start = (newsPager.pageNum - 1) * newsPager.pageSize;
  return filteredNews.value.slice(start, start + newsPager.pageSize);
});

function statusLabel(status?: string) {
  if (status === 'ON') return '启用';
  if (status === 'OFF') return '停用';
  if (status === 'PUBLISHED') return '已发布';
  if (status === 'DRAFT') return '草稿';
  return status || '-';
}

function statusType(status?: string) {
  return status === 'ON' || status === 'PUBLISHED' ? 'success' : 'info';
}

function syncTabToRoute(tab: TabName) {
  if (route.query.tab === tab) return;
  void router.replace({ path: '/admin/content', query: { tab } });
}

async function loadData() {
  loading.value = true;
  try {
    const [bannerRes, newsRes] = await Promise.all([api.adminBanners(), api.adminAnnouncements()]);
    banners.value = bannerRes.data.data;
    announcements.value = newsRes.data.data;
  } finally {
    loading.value = false;
  }
}

function openBannerDialog(row?: Banner) {
  Object.assign(bannerForm, {
    id: row?.id,
    title: row?.title || '',
    subtitle: row?.subtitle || '',
    imageUrl: row?.imageUrl || '',
    linkUrl: row?.linkUrl || '',
    sortNo: row?.sortNo || 1,
    status: row?.status || 'ON',
  });
  bannerDialogVisible.value = true;
}

async function saveBanner() {
  await api.saveBanner(bannerForm);
  ElMessage.success('轮播位已保存');
  bannerDialogVisible.value = false;
  await loadData();
}

async function toggleBanner(row: Banner) {
  if (!row.id) return;
  const status = row.status === 'ON' ? 'OFF' : 'ON';
  await api.updateBannerStatus(row.id, status);
  ElMessage.success(status === 'ON' ? '轮播位已启用' : '轮播位已停用');
  await loadData();
}

async function removeBanner(row: Banner) {
  if (!row.id) return;
  await ElMessageBox.confirm('删除后首页不再展示该轮播位，确认继续吗？', '删除轮播位', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  });
  await api.deleteBanner(row.id);
  ElMessage.success('轮播位已删除');
  await loadData();
}

function openNewsDrawer(row?: Announcement) {
  Object.assign(newsForm, {
    id: row?.id,
    title: row?.title || '',
    summary: row?.summary || '',
    coverImage: row?.coverImage || '',
    content: row?.content || '',
    publishStatus: row?.publishStatus || 'PUBLISHED',
    publishTime: row?.publishTime || '',
  });
  newsDrawerVisible.value = true;
}

async function saveNews() {
  await api.saveAnnouncement({
    ...newsForm,
    publishTime: newsForm.publishTime || null,
  });
  ElMessage.success('资讯已保存');
  newsDrawerVisible.value = false;
  await loadData();
}

async function toggleNews(row: Announcement) {
  const status = row.publishStatus === 'PUBLISHED' ? 'DRAFT' : 'PUBLISHED';
  await api.updateAnnouncementStatus(row.id, status);
  ElMessage.success(status === 'PUBLISHED' ? '资讯已发布' : '资讯已设为草稿');
  await loadData();
}

async function removeNews(row: Announcement) {
  await ElMessageBox.confirm('删除后前台资讯详情将不可访问，确认继续吗？', '删除资讯', {
    type: 'warning',
    confirmButtonText: '确认删除',
    cancelButtonText: '取消',
  });
  await api.deleteAnnouncement(row.id);
  ElMessage.success('资讯已删除');
  await loadData();
}

function openPreview(row: Announcement) {
  currentPreview.value = row;
  previewVisible.value = true;
}

function resetBannerFilters() {
  Object.assign(bannerFilters, { keyword: '', status: '' });
  pager.pageNum = 1;
}

function resetNewsFilters() {
  Object.assign(newsFilters, { keyword: '', publishStatus: '' });
  newsPager.pageNum = 1;
}

watch(activeTab, (tab) => syncTabToRoute(tab));
watch(
  () => route.query.tab,
  (tab) => {
    if (tab && tab !== activeTab.value) activeTab.value = tab as TabName;
  },
);

onMounted(loadData);
</script>

<template>
  <div class="admin-page">
    <section class="admin-toolbar">
      <div>
        <p class="admin-kicker">内容运营</p>
        <h2>首页轮播和资讯内容集中维护</h2>
      </div>
      <div class="toolbar">
        <el-button type="primary" @click="openBannerDialog()">新增轮播</el-button>
        <el-button plain @click="openNewsDrawer()">新增资讯</el-button>
      </div>
    </section>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="轮播位" name="banners">
        <section class="admin-panel" v-loading="loading">
          <div class="table-toolbar">
            <el-input v-model="bannerFilters.keyword" placeholder="标题 / 副标题 / 链接" clearable />
            <el-select v-model="bannerFilters.status" placeholder="状态" clearable>
              <el-option label="启用" value="ON" />
              <el-option label="停用" value="OFF" />
            </el-select>
            <el-button text @click="resetBannerFilters">重置</el-button>
          </div>

          <el-table :data="pagedBanners" stripe>
            <el-table-column label="预览" width="132">
              <template #default="{ row }">
                <img v-if="row.imageUrl" :src="row.imageUrl" alt="banner" class="thumb" />
                <span v-else class="muted">无图片</span>
              </template>
            </el-table-column>
            <el-table-column prop="sortNo" label="排序" width="90" />
            <el-table-column label="文案" min-width="260">
              <template #default="{ row }">
                <div class="name-cell">
                  <strong>{{ row.title }}</strong>
                  <span>{{ row.subtitle || '暂无副标题' }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column prop="linkUrl" label="跳转链接" min-width="180" show-overflow-tooltip />
            <el-table-column label="状态" width="100">
              <template #default="{ row }">
                <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column label="操作" width="230" fixed="right">
              <template #default="{ row }">
                <div class="toolbar nowrap">
                  <el-button size="small" @click="openBannerDialog(row)">编辑</el-button>
                  <el-button size="small" plain @click="toggleBanner(row)">{{ row.status === 'ON' ? '停用' : '启用' }}</el-button>
                  <el-button size="small" type="danger" plain @click="removeBanner(row)">删除</el-button>
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
              :page-sizes="[8, 12, 20]"
              :total="filteredBanners.length"
              @current-change="(page: number) => pager.pageNum = page"
              @size-change="(size: number) => { pager.pageSize = size; pager.pageNum = 1; }"
            />
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="资讯内容" name="news">
        <section class="admin-panel" v-loading="loading">
          <div class="table-toolbar">
            <el-input v-model="newsFilters.keyword" placeholder="标题 / 摘要 / 正文" clearable />
            <el-select v-model="newsFilters.publishStatus" placeholder="发布状态" clearable>
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="草稿" value="DRAFT" />
            </el-select>
            <el-button text @click="resetNewsFilters">重置</el-button>
          </div>

          <el-table :data="pagedNews" stripe>
            <el-table-column label="封面" width="120">
              <template #default="{ row }">
                <img v-if="row.coverImage" :src="row.coverImage" alt="cover" class="cover" />
                <span v-else class="muted">无封面</span>
              </template>
            </el-table-column>
            <el-table-column label="资讯" min-width="300">
              <template #default="{ row }">
                <div class="name-cell">
                  <strong>{{ row.title }}</strong>
                  <span>{{ row.summary }}</span>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="状态" width="110">
              <template #default="{ row }">
                <el-tag :type="statusType(row.publishStatus)" effect="plain">{{ statusLabel(row.publishStatus) }}</el-tag>
              </template>
            </el-table-column>
            <el-table-column prop="publishTime" label="发布时间" min-width="170" />
            <el-table-column label="操作" width="280" fixed="right">
              <template #default="{ row }">
                <div class="toolbar nowrap">
                  <el-button size="small" @click="openPreview(row)">预览</el-button>
                  <el-button size="small" @click="openNewsDrawer(row)">编辑</el-button>
                  <el-button size="small" plain @click="toggleNews(row)">{{ row.publishStatus === 'PUBLISHED' ? '转草稿' : '发布' }}</el-button>
                  <el-button size="small" type="danger" plain @click="removeNews(row)">删除</el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>

          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="newsPager.pageNum"
              :page-size="newsPager.pageSize"
              :page-sizes="[8, 12, 20]"
              :total="filteredNews.length"
              @current-change="(page: number) => newsPager.pageNum = page"
              @size-change="(size: number) => { newsPager.pageSize = size; newsPager.pageNum = 1; }"
            />
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="bannerDialogVisible" :title="bannerForm.id ? '编辑轮播位' : '新增轮播位'" width="560px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="bannerForm.title" maxlength="128" /></el-form-item>
        <el-form-item label="副标题"><el-input v-model="bannerForm.subtitle" type="textarea" :rows="3" maxlength="255" show-word-limit /></el-form-item>
        <el-form-item label="图片地址"><el-input v-model="bannerForm.imageUrl" /></el-form-item>
        <el-form-item label="跳转链接"><el-input v-model="bannerForm.linkUrl" placeholder="/news 或 https://example.com" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="排序"><el-input-number v-model="bannerForm.sortNo" :min="1" :step="1" /></el-form-item>
          <el-form-item label="状态">
            <el-select v-model="bannerForm.status">
              <el-option label="启用" value="ON" />
              <el-option label="停用" value="OFF" />
            </el-select>
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="toolbar end">
          <el-button @click="bannerDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveBanner">保存</el-button>
        </div>
      </template>
    </el-dialog>

    <el-drawer v-model="newsDrawerVisible" :title="newsForm.id ? '编辑资讯' : '新增资讯'" size="560px">
      <el-form label-position="top">
        <el-form-item label="标题"><el-input v-model="newsForm.title" maxlength="128" /></el-form-item>
        <el-form-item label="摘要"><el-input v-model="newsForm.summary" type="textarea" :rows="3" maxlength="255" show-word-limit /></el-form-item>
        <el-form-item label="封面图片"><el-input v-model="newsForm.coverImage" /></el-form-item>
        <el-form-item label="正文"><el-input v-model="newsForm.content" type="textarea" :rows="10" /></el-form-item>
        <div class="form-grid">
          <el-form-item label="发布状态">
            <el-select v-model="newsForm.publishStatus">
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="草稿" value="DRAFT" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布时间">
            <el-date-picker v-model="newsForm.publishTime" type="datetime" value-format="YYYY-MM-DDTHH:mm:ss" placeholder="为空则发布时取当前时间" />
          </el-form-item>
        </div>
      </el-form>
      <template #footer>
        <div class="toolbar end">
          <el-button @click="newsDrawerVisible = false">取消</el-button>
          <el-button type="primary" @click="saveNews">保存</el-button>
        </div>
      </template>
    </el-drawer>

    <el-dialog v-model="previewVisible" title="资讯预览" width="680px">
      <article v-if="currentPreview" class="news-preview">
        <img v-if="currentPreview.coverImage" :src="currentPreview.coverImage" alt="cover" />
        <h2>{{ currentPreview.title }}</h2>
        <p class="muted">{{ currentPreview.summary }}</p>
        <div class="preview-body">{{ currentPreview.content }}</div>
      </article>
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

.admin-kicker,
.muted,
.name-cell span {
  color: var(--muted);
}

.admin-kicker {
  margin: 0;
  font-size: 13px;
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
  width: 260px;
}

.table-toolbar .el-select {
  width: 150px;
}

.thumb,
.cover {
  display: block;
  width: 96px;
  height: 54px;
  object-fit: cover;
  border-radius: 6px;
  border: 1px solid var(--line);
}

.cover {
  width: 76px;
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

.news-preview {
  display: grid;
  gap: 12px;
}

.news-preview img {
  width: 100%;
  max-height: 260px;
  object-fit: cover;
  border-radius: 8px;
}

.news-preview h2,
.news-preview p {
  margin: 0;
}

.preview-body {
  white-space: pre-wrap;
  line-height: 1.8;
}

@media (max-width: 900px) {
  .admin-toolbar {
    align-items: flex-start;
    flex-direction: column;
  }

  .table-toolbar .el-input,
  .table-toolbar .el-select,
  .form-grid {
    width: 100%;
    grid-template-columns: 1fr;
  }
}
</style>
