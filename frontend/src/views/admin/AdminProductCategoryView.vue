<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { ProductCategory } from '../../api/types';

const categories = ref<ProductCategory[]>([]);
const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});
const form = reactive<ProductCategory>({ name: '' });
const pagedCategories = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return categories.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  const { data } = await api.productCategories();
  categories.value = data.data;
  const maxPage = Math.max(1, Math.ceil(categories.value.length / pager.pageSize));
  if (pager.pageNum > maxPage) {
    pager.pageNum = maxPage;
  }
}

async function saveCategory() {
  await api.saveProductCategory(form);
  ElMessage.success('商品分类已保存');
  resetForm();
  await loadData();
}

function editCategory(row: ProductCategory) {
  form.id = row.id;
  form.name = row.name;
}

function resetForm() {
  form.id = undefined;
  form.name = '';
}

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

onMounted(loadData);
</script>

<template>
  <div class="page-shell">
    <section class="panel category-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">商品分类管理</h2>
          <p class="panel-subtitle">商城业务线的基础数据独立维护，不与服务分类共用。</p>
        </div>
      </div>

      <div class="editor-grid">
        <el-form label-position="top">
          <el-form-item label="分类名称">
            <el-input v-model="form.name" />
          </el-form-item>
          <div class="toolbar">
            <el-button type="warning" @click="saveCategory">保存分类</el-button>
            <el-button @click="resetForm">清空表单</el-button>
          </div>
        </el-form>

        <div class="table-block">
          <el-table :data="pagedCategories" stripe>
          <el-table-column prop="id" label="ID" width="100" />
          <el-table-column prop="name" label="分类名称" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="editCategory(row)">编辑</el-button>
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
              :total="categories.length"
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
.category-panel {
  padding: 26px;
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
