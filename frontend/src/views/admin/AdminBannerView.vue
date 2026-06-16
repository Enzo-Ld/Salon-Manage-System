<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { Banner } from '../../api/types';

const banners = ref<Banner[]>([]);
const pager = reactive({
  pageNum: 1,
  pageSize: 8,
});
const form = reactive<Banner>({
  title: '',
  subtitle: '',
  imageUrl: '',
  linkUrl: '',
  sortNo: 1,
  status: 'ON',
});
const pagedBanners = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return banners.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  const { data } = await api.adminBanners();
  banners.value = data.data;
  const maxPage = Math.max(1, Math.ceil(banners.value.length / pager.pageSize));
  if (pager.pageNum > maxPage) {
    pager.pageNum = maxPage;
  }
}

async function saveBanner() {
  await api.saveBanner(form);
  ElMessage.success('轮播图已保存');
  resetForm();
  await loadData();
}

function editBanner(row: Banner) {
  Object.assign(form, {
    id: row.id,
    title: row.title,
    subtitle: row.subtitle || '',
    imageUrl: row.imageUrl,
    linkUrl: row.linkUrl || '',
    sortNo: row.sortNo,
    status: row.status,
  });
}

function resetForm() {
  Object.assign(form, {
    id: undefined,
    title: '',
    subtitle: '',
    imageUrl: '',
    linkUrl: '',
    sortNo: 1,
    status: 'ON',
  });
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
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
          <h2 class="panel-title">轮播图管理</h2>
          <p class="panel-subtitle">维护首页轮播图的文案、图片、跳转链接、排序和启停状态。</p>
        </div>
      </div>

      <div class="editor-grid">
        <el-form label-position="top">
          <el-form-item label="标题">
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="副标题">
            <el-input v-model="form.subtitle" type="textarea" :rows="3" />
          </el-form-item>
          <el-form-item label="图片地址">
            <el-input v-model="form.imageUrl" />
          </el-form-item>
          <el-form-item label="跳转链接">
            <el-input v-model="form.linkUrl" placeholder="/news 或 https://example.com" />
          </el-form-item>
          <el-form-item label="排序">
            <el-input-number v-model="form.sortNo" :min="1" :step="1" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="启用" value="ON" />
              <el-option label="停用" value="OFF" />
            </el-select>
          </el-form-item>
          <div class="toolbar">
            <el-button type="warning" @click="saveBanner">保存轮播图</el-button>
            <el-button @click="resetForm">清空表单</el-button>
          </div>
        </el-form>

        <div class="table-block">
          <el-table :data="pagedBanners" stripe>
          <el-table-column prop="sortNo" label="排序" width="80" />
          <el-table-column prop="title" label="标题" min-width="180" />
          <el-table-column prop="subtitle" label="副标题" min-width="220" />
          <el-table-column prop="status" label="状态" width="100" />
          <el-table-column prop="linkUrl" label="跳转链接" min-width="180" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="editBanner(row)">编辑</el-button>
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
              :total="banners.length"
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
  grid-template-columns: 340px 1fr;
  gap: 20px;
}

.table-block {
  display: grid;
  gap: 18px;
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
