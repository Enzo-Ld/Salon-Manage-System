<script setup lang="ts">
import { onMounted, reactive, ref } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { AdminUser, RoleCode } from '../../api/types';
import { resolveAvatar } from '../../utils/avatar';
import { isValidMainlandPhone, normalizePhone } from '../../utils/validators';

const loading = ref(false);
const dialogVisible = ref(false);
const submitting = ref(false);
const users = ref<AdminUser[]>([]);
const total = ref(0);

const filters = reactive({
  keyword: '',
  roleCode: '',
  enabled: '',
});

const pager = reactive({
  pageNum: 1,
  pageSize: 10,
});

const createForm = reactive({
  username: '',
  password: '',
  nickname: '',
  phone: '',
  roleCode: 'USER' as RoleCode,
});

const roleOptions: { label: string; value: RoleCode }[] = [
  { label: '普通用户', value: 'USER' },
  { label: '技师', value: 'TECH' },
  { label: '管理员', value: 'ADMIN' },
];

function roleLabel(roleCode: RoleCode) {
  return roleOptions.find((item) => item.value === roleCode)?.label || roleCode;
}

function enabledParam() {
  if (filters.enabled === 'true') return true;
  if (filters.enabled === 'false') return false;
  return undefined;
}

async function loadUsers(pageNum = pager.pageNum) {
  loading.value = true;
  try {
    const { data } = await api.usersPage({
      pageNum,
      pageSize: pager.pageSize,
      keyword: filters.keyword.trim() || undefined,
      roleCode: filters.roleCode || undefined,
      enabled: enabledParam(),
    });
    users.value = data.data.records;
    total.value = data.data.total;
    pager.pageNum = data.data.pageNum;
  } finally {
    loading.value = false;
  }
}

function handleSearch() {
  pager.pageNum = 1;
  void loadUsers(1);
}

function handlePageChange(pageNum: number) {
  void loadUsers(pageNum);
}

function handleSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
  void loadUsers(1);
}

function openCreateDialog() {
  createForm.username = '';
  createForm.password = '';
  createForm.nickname = '';
  createForm.phone = '';
  createForm.roleCode = 'USER';
  dialogVisible.value = true;
}

async function createUser() {
  if (!createForm.username.trim()) {
    ElMessage.warning('请输入用户名');
    return;
  }
  if (!createForm.password.trim()) {
    ElMessage.warning('请输入初始密码');
    return;
  }
  if (!isValidMainlandPhone(createForm.phone)) {
    ElMessage.warning('手机号需为 11 位大陆手机号');
    return;
  }

  submitting.value = true;
  try {
    await api.createUser({
      username: createForm.username.trim(),
      password: createForm.password.trim(),
      nickname: createForm.nickname.trim() || undefined,
      phone: normalizePhone(createForm.phone) || undefined,
      roleCode: createForm.roleCode,
    });
    ElMessage.success('账号已创建');
    dialogVisible.value = false;
    await loadUsers(1);
  } finally {
    submitting.value = false;
  }
}

async function changeStatus(row: AdminUser, enabled: boolean) {
  await api.updateUserStatus(row.id, enabled);
  ElMessage.success(enabled ? '账号已启用' : '账号已禁用');
  await loadUsers();
}

async function changeRole(row: AdminUser, roleCode: RoleCode) {
  await api.updateUserRole(row.id, roleCode);
  ElMessage.success('角色已调整');
  await loadUsers();
}

function handleRoleSelect(row: AdminUser, value: string) {
  void changeRole(row, value as RoleCode);
}

function handleStatusSwitch(row: AdminUser, value: boolean | string | number) {
  void changeStatus(row, Boolean(value));
}

async function resetPassword(row: AdminUser) {
  try {
    await ElMessageBox.confirm(`确认将 ${row.username} 的密码重置为 123456 吗？`, '重置密码', {
      type: 'warning',
      confirmButtonText: '确认重置',
      cancelButtonText: '取消',
    });
    await api.resetUserPassword(row.id);
    ElMessage.success('密码已重置为 123456');
  } catch {
    // noop
  }
}

onMounted(() => {
  void loadUsers();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel user-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">用户管理</h2>
          <p class="panel-subtitle">公开注册仅开放普通用户；管理员可在此创建账号、调整角色、启停账号并重置密码。</p>
        </div>
        <el-button type="warning" @click="openCreateDialog">新增账号</el-button>
      </div>

      <div class="toolbar filter-bar">
        <el-input v-model="filters.keyword" placeholder="按用户名 / 昵称 / 手机号搜索" clearable />
        <el-select v-model="filters.roleCode" placeholder="角色筛选" clearable>
          <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
        </el-select>
        <el-select v-model="filters.enabled" placeholder="状态筛选" clearable>
          <el-option label="启用" value="true" />
          <el-option label="禁用" value="false" />
        </el-select>
        <el-button plain @click="handleSearch">查询</el-button>
      </div>

      <el-table :data="users" stripe>
        <el-table-column label="头像 / 用户" min-width="240">
          <template #default="{ row }">
            <div class="user-cell">
              <img :src="resolveAvatar(row.avatarUrl, row.roleCode, row.nickname || row.username)" alt="avatar" class="user-avatar" />
              <div>
                <strong>{{ row.nickname || row.username }}</strong>
                <p>{{ row.username }}</p>
              </div>
            </div>
          </template>
        </el-table-column>
        <el-table-column prop="phone" label="手机号" min-width="140" />
        <el-table-column label="角色" min-width="160">
          <template #default="{ row }">
            <el-select :model-value="row.roleCode" @change="(value: string) => handleRoleSelect(row, value)">
              <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
          </template>
        </el-table-column>
        <el-table-column label="状态" min-width="140">
          <template #default="{ row }">
            <el-switch :model-value="row.enabled === 1" @change="(value: boolean | string | number) => handleStatusSwitch(row, value)" />
          </template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="180">
          <template #default="{ row }">
            {{ row.createdAt || '--' }}
          </template>
        </el-table-column>
        <el-table-column label="操作" min-width="160" fixed="right">
          <template #default="{ row }">
            <div class="toolbar">
              <el-tag effect="plain">{{ roleLabel(row.roleCode) }}</el-tag>
              <el-button size="small" plain @click="resetPassword(row)">重置密码</el-button>
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
          :page-sizes="[10, 20, 50]"
          :total="total"
          @current-change="handlePageChange"
          @size-change="handleSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" title="新增账号" width="520px">
      <el-form label-position="top">
        <el-form-item label="用户名">
          <el-input v-model="createForm.username" maxlength="30" />
        </el-form-item>
        <el-form-item label="初始密码">
          <el-input v-model="createForm.password" type="password" show-password maxlength="30" />
        </el-form-item>
        <el-form-item label="昵称">
          <el-input v-model="createForm.nickname" maxlength="30" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="createForm.phone" maxlength="11" placeholder="11 位大陆手机号" />
        </el-form-item>
        <el-form-item label="角色">
          <el-select v-model="createForm.roleCode">
            <el-option v-for="item in roleOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-alert title="创建技师账号时会自动生成技师档案，后续可在基础资料页继续维护等级和展示名称。" type="info" :closable="false" />
      </el-form>

      <template #footer>
        <div class="toolbar">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="warning" :loading="submitting" @click="createUser">创建账号</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.user-panel {
  padding: 26px;
}

.filter-bar {
  display: grid;
  grid-template-columns: 1.4fr 180px 180px auto;
  gap: 12px;
  margin-bottom: 18px;
}

.user-cell {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-cell p {
  margin: 6px 0 0;
  color: var(--muted);
}

.user-avatar {
  width: 44px;
  height: 44px;
  border-radius: 50%;
  object-fit: cover;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 960px) {
  .filter-bar {
    grid-template-columns: 1fr;
  }

  .pagination-wrap {
    justify-content: flex-start;
  }
}
</style>
