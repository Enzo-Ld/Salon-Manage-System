<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { Announcement } from '../../api/types';

const announcements = ref<Announcement[]>([]);
const pager = reactive({
  pageNum: 1,
  pageSize: 8,
});
const filters = reactive({
  keyword: '',
  publishStatus: '',
});
const form = reactive<Partial<Announcement>>({
  title: '',
  summary: '',
  coverImage: '',
  content: '',
  publishStatus: 'PUBLISHED',
  publishTime: '',
});
const filteredAnnouncements = computed(() => {
  const keyword = filters.keyword.trim().toLowerCase();
  return announcements.value.filter((item) => {
    const matchKeyword = !keyword || [item.title, item.summary, item.content].some((value) => String(value ?? '').toLowerCase().includes(keyword));
    const matchStatus = !filters.publishStatus || item.publishStatus === filters.publishStatus;
    return matchKeyword && matchStatus;
  });
});
const pagedAnnouncements = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredAnnouncements.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  const { data } = await api.adminAnnouncements();
  announcements.value = data.data;
  const maxPage = Math.max(1, Math.ceil(filteredAnnouncements.value.length / pager.pageSize));
  if (pager.pageNum > maxPage) {
    pager.pageNum = maxPage;
  }
}

async function saveNews() {
  await api.saveAnnouncement({
    ...form,
    publishTime: form.publishTime || null,
  });
  ElMessage.success('资讯已保存');
  resetForm();
  await loadData();
}

function editNews(row: Announcement) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    summary: row.summary,
    coverImage: row.coverImage || '',
    content: row.content,
    publishStatus: row.publishStatus,
    publishTime: row.publishTime || '',
  });
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    title: '',
    summary: '',
    coverImage: '',
    content: '',
    publishStatus: 'PUBLISHED',
    publishTime: '',
  });
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
  filters.publishStatus = '';
  pager.pageNum = 1;
}

onMounted(() => {
  void loadData();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel editor-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">资讯管理</h2>
          <p class="panel-subtitle">维护首页资讯列表和详情内容，支持草稿与发布两种状态。</p>
        </div>
      </div>

      <div class="editor-grid">
        <el-form label-position="top">
          <el-form-item label="资讯标题">
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="资讯摘要">
            <el-input v-model="form.summary" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="封面图片地址">
            <el-input v-model="form.coverImage" />
          </el-form-item>
          <el-form-item label="正文内容">
            <el-input v-model="form.content" type="textarea" :rows="8" />
          </el-form-item>
          <el-form-item label="发布状态">
            <el-select v-model="form.publishStatus">
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="草稿" value="DRAFT" />
            </el-select>
          </el-form-item>
          <el-form-item label="发布时间">
            <el-date-picker
              v-model="form.publishTime"
              type="datetime"
              value-format="YYYY-MM-DDTHH:mm:ss"
              placeholder="不填写则发布时自动取当前时间"
            />
          </el-form-item>
          <div class="toolbar">
            <el-button type="warning" @click="saveNews">保存资讯</el-button>
            <el-button @click="resetForm">清空表单</el-button>
          </div>
        </el-form>

        <div class="table-block">
          <div class="toolbar filter-bar">
            <el-input v-model="filters.keyword" placeholder="标题 / 摘要 / 正文关键词" clearable @keyup.enter="handleSearch" />
            <el-select v-model="filters.publishStatus" placeholder="发布状态" clearable @change="handleSearch">
              <el-option label="已发布" value="PUBLISHED" />
              <el-option label="草稿" value="DRAFT" />
            </el-select>
            <el-button plain @click="handleSearch">查询</el-button>
            <el-button text @click="resetFilters">重置</el-button>
          </div>

          <el-table :data="pagedAnnouncements" stripe>
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="summary" label="摘要" min-width="220" />
          <el-table-column prop="publishStatus" label="状态" width="100" />
          <el-table-column prop="publishTime" label="发布时间" min-width="180" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="editNews(row)">编辑</el-button>
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
              :total="filteredAnnouncements.length"
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
.editor-panel {
  padding: 26px;
}

.editor-grid {
  display: grid;
  grid-template-columns: 360px 1fr;
  gap: 20px;
}

.table-block {
  display: grid;
  gap: 18px;
}

.filter-bar {
  flex-wrap: wrap;
}

.filter-bar .el-input {
  width: 260px;
}

.filter-bar .el-select {
  width: 140px;
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
