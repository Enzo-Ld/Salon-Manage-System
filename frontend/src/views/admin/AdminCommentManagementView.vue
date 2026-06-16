<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { AdminComment } from '../../api/types';

const loading = ref(false);
const comments = ref<AdminComment[]>([]);
const total = ref(0);
const dateRange = ref<[string, string] | []>([]);

const filters = reactive({
  commentType: '',
  keyword: '',
});

const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});

async function loadComments(pageNum = pager.pageNum) {
  loading.value = true;
  try {
    const { data } = await api.adminCommentsPage({
      pageNum,
      pageSize: pager.pageSize,
      commentType: filters.commentType || undefined,
      keyword: filters.keyword.trim() || undefined,
      startDate: dateRange.value[0],
      endDate: dateRange.value[1],
    });
    comments.value = data.data.records;
    total.value = data.data.total;
    pager.pageNum = data.data.pageNum;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pager.pageNum = 1;
  void loadComments(1);
}

function handlePageChange(pageNum: number) {
  void loadComments(pageNum);
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
  void loadComments(1);
}

async function removeComment(row: AdminComment) {
  try {
    await ElMessageBox.confirm('删除后前台对应评论会立即消失，确认继续吗？', '删除评论', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await api.deleteAdminComment(row.id);
    ElMessage.success('评论已删除');
    await loadComments();
  } catch {
    // noop
  }
}

onMounted(() => {
  void loadComments();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel comment-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">评论管理</h2>
          <p class="panel-subtitle">统一展示商品评论和预约评价，只保留筛选、分页与删除，不再引入审核流或点赞信息。</p>
        </div>
      </div>

      <div class="toolbar filter-bar">
        <el-select v-model="filters.commentType" placeholder="评论类型" clearable>
          <el-option label="商品评论" value="PRODUCT" />
          <el-option label="预约评价" value="APPOINTMENT" />
        </el-select>
        <el-input v-model="filters.keyword" placeholder="按评论人、内容、目标名称搜索" clearable />
        <el-date-picker v-model="dateRange" type="daterange" value-format="YYYY-MM-DD" start-placeholder="开始日期" end-placeholder="结束日期" />
        <el-button plain @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="comments" stripe>
        <el-table-column prop="commentTypeLabel" label="类型" min-width="120" />
        <el-table-column prop="username" label="评论人" min-width="140" />
        <el-table-column prop="targetTitle" label="关联对象" min-width="180" />
        <el-table-column label="评分" min-width="120">
          <template #default="{ row }">
            <el-rate :model-value="row.rating" disabled />
          </template>
        </el-table-column>
        <el-table-column prop="content" label="评论内容" min-width="320" show-overflow-tooltip />
        <el-table-column prop="createdAt" label="评论时间" min-width="180" />
        <el-table-column label="操作" min-width="120" fixed="right">
          <template #default="{ row }">
            <el-button type="danger" plain size="small" @click="removeComment(row)">删除</el-button>
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
          :total="total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>
  </div>
</template>

<style scoped>
.comment-panel {
  padding: 26px;
}

.filter-bar {
  display: grid;
  grid-template-columns: 180px 1fr 320px auto;
  gap: 12px;
  margin-bottom: 18px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 1080px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}
</style>
