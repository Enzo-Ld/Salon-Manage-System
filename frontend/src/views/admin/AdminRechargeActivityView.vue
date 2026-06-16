<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { RechargeActivity } from '../../api/types';

const activities = ref<RechargeActivity[]>([]);
const pager = reactive({
  pageNum: 1,
  pageSize: 8,
});
const form = reactive<RechargeActivity>({
  title: '',
  rechargeAmount: 1000,
  giftAmount: 100,
  status: 'ACTIVE',
  startTime: '',
  endTime: '',
});
const pagedActivities = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return activities.value.slice(start, start + pager.pageSize);
});

async function loadData() {
  const { data } = await api.adminRechargeActivities();
  activities.value = data.data;
  const maxPage = Math.max(1, Math.ceil(activities.value.length / pager.pageSize));
  if (pager.pageNum > maxPage) {
    pager.pageNum = maxPage;
  }
}

async function saveActivity() {
  if (form.startTime && form.endTime && new Date(form.endTime).getTime() <= new Date(form.startTime).getTime()) {
    ElMessage.warning('结束时间必须晚于开始时间');
    return;
  }

  await api.saveRechargeActivity({
    ...form,
    startTime: form.startTime || null,
    endTime: form.endTime || null,
  });
  ElMessage.success('充值活动已保存');
  resetForm();
  await loadData();
}

function editActivity(activity: RechargeActivity) {
  form.id = activity.id;
  form.title = activity.title;
  form.rechargeAmount = Number(activity.rechargeAmount);
  form.giftAmount = Number(activity.giftAmount);
  form.status = activity.status;
  form.startTime = activity.startTime ?? '';
  form.endTime = activity.endTime ?? '';
}

function resetForm() {
  form.id = undefined;
  form.title = '';
  form.rechargeAmount = 1000;
  form.giftAmount = 100;
  form.status = 'ACTIVE';
  form.startTime = '';
  form.endTime = '';
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
    <section class="panel editor-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">充值活动管理</h2>
          <p class="panel-subtitle">用于维护类似“充1000送100”的钱包充值活动，前台用户会直接看到这里配置的活动。</p>
        </div>
      </div>

      <div class="editor-grid">
        <el-form label-position="top">
          <el-form-item label="活动名称">
            <el-input v-model="form.title" />
          </el-form-item>
          <el-form-item label="充值金额">
            <el-input-number v-model="form.rechargeAmount" :min="1" :step="50" />
          </el-form-item>
          <el-form-item label="赠送金额">
            <el-input-number v-model="form.giftAmount" :min="0" :step="10" />
          </el-form-item>
          <el-form-item label="状态">
            <el-select v-model="form.status">
              <el-option label="启用" value="ACTIVE" />
              <el-option label="停用" value="INACTIVE" />
            </el-select>
          </el-form-item>
          <el-form-item label="开始时间">
            <el-date-picker
              v-model="form.startTime"
              class="activity-time-picker"
              type="datetime"
              placeholder="请选择开始时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              clearable
            />
          </el-form-item>
          <el-form-item label="结束时间">
            <el-date-picker
              v-model="form.endTime"
              class="activity-time-picker"
              type="datetime"
              placeholder="请选择结束时间"
              value-format="YYYY-MM-DDTHH:mm:ss"
              format="YYYY-MM-DD HH:mm"
              clearable
            />
          </el-form-item>
          <div class="toolbar">
            <el-button type="warning" @click="saveActivity">保存活动</el-button>
            <el-button @click="resetForm">清空表单</el-button>
          </div>
        </el-form>

        <div class="table-block">
          <el-table :data="pagedActivities" stripe>
          <el-table-column prop="title" label="活动名称" min-width="180" />
          <el-table-column prop="rechargeAmount" label="充值金额" width="120" />
          <el-table-column prop="giftAmount" label="赠送金额" width="120" />
          <el-table-column prop="status" label="状态" width="120" />
          <el-table-column prop="startTime" label="开始时间" min-width="180" />
          <el-table-column prop="endTime" label="结束时间" min-width="180" />
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button size="small" @click="editActivity(row)">编辑</el-button>
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
              :total="activities.length"
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
  grid-template-columns: 320px 1fr;
  gap: 20px;
}

.table-block {
  display: grid;
  gap: 18px;
}

.activity-time-picker {
  width: 100%;
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
