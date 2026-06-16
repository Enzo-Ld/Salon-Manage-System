<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { AdminUser, ServiceCategory, ServiceItem, Technician } from '../../api/types';

const activeTab = ref('services');
const loading = ref(false);
const abilityDialogVisible = ref(false);
const currentTechnician = ref<Technician | null>(null);
const abilityIds = ref<number[]>([]);

const technicians = ref<Technician[]>([]);
const categories = ref<ServiceCategory[]>([]);
const serviceItems = ref<ServiceItem[]>([]);
const users = ref<AdminUser[]>([]);

const technicianForm = reactive<Partial<Technician>>({ status: 'ACTIVE', scoreOverride: null });
const categoryForm = reactive<Partial<ServiceCategory>>({ sortNo: 0 });
const serviceItemForm = reactive<Partial<ServiceItem>>({ status: 'ON', durationMin: 60, price: 0 });

const serviceKeyword = ref('');
const technicianKeyword = ref('');
const serviceStatus = ref('');
const technicianLevelOptions = ['中级技师', '高级技师', '首席造型师', '资深护理师', '发型设计师'];

const categoryMap = computed(() =>
  categories.value.reduce<Record<number, string>>((acc, item) => {
    acc[item.id] = item.name;
    return acc;
  }, {}),
);

const filteredTechnicians = computed(() => {
  const keyword = technicianKeyword.value.trim();
  return technicians.value.filter((item) => !keyword || [item.name, item.levelName, item.status, item.score].some((value) => String(value ?? '').includes(keyword)));
});

const technicianUserIds = computed(() => new Set(technicians.value.map((item) => item.userId)));
const userMap = computed(() =>
  users.value.reduce<Record<number, AdminUser>>((acc, item) => {
    acc[item.id] = item;
    return acc;
  }, {}),
);
const technicianUserOptions = computed(() =>
  users.value.filter((item) => ['USER', 'TECH'].includes(item.roleCode)),
);

const filteredServices = computed(() => {
  const keyword = serviceKeyword.value.trim();
  return serviceItems.value.filter((item) => {
    const matchKeyword = !keyword || [item.name, item.intro, categoryMap.value[item.categoryId]].some((value) => String(value ?? '').includes(keyword));
    const matchStatus = !serviceStatus.value || item.status === serviceStatus.value;
    return matchKeyword && matchStatus;
  });
});

function statusType(status?: string) {
  return status === 'ON' || status === 'ACTIVE' ? 'success' : 'info';
}

function statusLabel(status?: string) {
  if (status === 'ON') return '上架';
  if (status === 'OFF') return '下架';
  if (status === 'ACTIVE') return '在职';
  if (status === 'INACTIVE') return '停用';
  return status || '-';
}

function money(value?: number) {
  return `¥ ${Number(value || 0).toFixed(2)}`;
}

function scoreText(value?: number | null) {
  return value === null || value === undefined ? '-' : Number(value).toFixed(1);
}

async function loadData() {
  loading.value = true;
  try {
    const [technicianRes, categoryRes, serviceRes, userRes] = await Promise.all([
      api.technicians(),
      api.categories(),
      api.adminServiceItems(),
      api.users(),
    ]);
    technicians.value = technicianRes.data.data;
    categories.value = categoryRes.data.data;
    serviceItems.value = serviceRes.data.data;
    users.value = userRes.data.data;
  } finally {
    loading.value = false;
  }
}

function userOptionDisabled(user: AdminUser) {
  return technicianUserIds.value.has(user.id) && user.id !== technicianForm.userId;
}

function userOptionLabel(user: AdminUser) {
  const displayName = user.nickname || user.username;
  const phone = user.phone ? ` / ${user.phone}` : '';
  const role = user.roleCode === 'TECH' ? '技师账号' : '普通用户';
  const status = user.enabled === 1 ? '启用' : '禁用';
  return `${displayName}（${user.username}${phone}，${role}，${status}）`;
}

function handleTechnicianUserChange(userId: number | undefined) {
  const user = userId ? userMap.value[userId] : undefined;
  if (!technicianForm.id && user && !technicianForm.name) {
    technicianForm.name = user.nickname || user.username;
  }
}

function editTechnician(row: Technician) {
  Object.assign(technicianForm, {
    id: row.id,
    userId: row.userId,
    name: row.name,
    levelName: row.levelName || '',
    score: undefined,
    scoreOverride: row.scoreOverride ?? null,
    status: row.status || 'ACTIVE',
  });
}

function resetTechnicianForm() {
  Object.assign(technicianForm, { id: undefined, userId: undefined, name: '', levelName: '', score: undefined, scoreOverride: null, status: 'ACTIVE' });
}

async function saveTechnician() {
  const selectedUser = technicianForm.userId ? userMap.value[technicianForm.userId] : undefined;
  if (!selectedUser) {
    ElMessage.warning('请先选择要绑定的账号');
    return;
  }
  if (selectedUser.enabled !== 1) {
    ElMessage.warning('禁用账号不能绑定为技师');
    return;
  }
  if (userOptionDisabled(selectedUser)) {
    ElMessage.warning('该账号已绑定其他技师档案');
    return;
  }
  await api.saveTechnician(technicianForm);
  ElMessage.success('技师信息已保存');
  resetTechnicianForm();
  await loadData();
}

function editCategory(row: ServiceCategory) {
  Object.assign(categoryForm, { id: row.id, name: row.name, sortNo: row.sortNo });
}

function resetCategoryForm() {
  Object.assign(categoryForm, { id: undefined, name: '', sortNo: 0 });
}

async function saveCategory() {
  await api.saveCategory(categoryForm);
  ElMessage.success('服务分类已保存');
  resetCategoryForm();
  await loadData();
}

function editService(row: ServiceItem) {
  Object.assign(serviceItemForm, {
    id: row.id,
    categoryId: row.categoryId,
    name: row.name,
    intro: row.intro || '',
    imageUrl: row.imageUrl || '',
    durationMin: row.durationMin,
    price: row.price,
    status: row.status || 'ON',
  });
}

function resetServiceForm() {
  Object.assign(serviceItemForm, {
    id: undefined,
    categoryId: undefined,
    name: '',
    intro: '',
    imageUrl: '',
    durationMin: 60,
    price: 0,
    status: 'ON',
  });
}

async function saveServiceItem() {
  await api.saveServiceItem(serviceItemForm);
  ElMessage.success('服务项目已保存');
  resetServiceForm();
  await loadData();
}

async function openAbilityDialog(row: Technician) {
  currentTechnician.value = row;
  const { data } = await api.technicianServices(row.id);
  abilityIds.value = data.data;
  abilityDialogVisible.value = true;
}

async function saveAbility() {
  if (!currentTechnician.value) return;
  await api.saveTechnicianServices(currentTechnician.value.id, abilityIds.value);
  ElMessage.success('技师服务能力已保存');
  abilityDialogVisible.value = false;
}

onMounted(loadData);
</script>

<template>
  <div class="admin-page" v-loading="loading">
    <section class="admin-toolbar">
      <div>
        <p class="admin-kicker">服务运营</p>
        <h2>技师、服务分类、服务项目和可服务能力集中维护</h2>
      </div>
    </section>

    <el-tabs v-model="activeTab">
      <el-tab-pane label="服务项目" name="services">
        <section class="admin-panel">
          <div class="split-layout">
            <el-form label-position="top" class="edit-form">
              <h3>{{ serviceItemForm.id ? '编辑服务项目' : '新增服务项目' }}</h3>
              <el-form-item label="所属分类">
                <el-select v-model="serviceItemForm.categoryId" filterable>
                  <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="项目名称"><el-input v-model="serviceItemForm.name" maxlength="128" /></el-form-item>
              <el-form-item label="服务简介"><el-input v-model="serviceItemForm.intro" type="textarea" :rows="3" maxlength="500" show-word-limit /></el-form-item>
              <el-form-item label="图片地址"><el-input v-model="serviceItemForm.imageUrl" /></el-form-item>
              <div class="form-grid">
                <el-form-item label="时长（分钟）"><el-input-number v-model="serviceItemForm.durationMin" :min="1" /></el-form-item>
                <el-form-item label="价格"><el-input-number v-model="serviceItemForm.price" :min="0" :precision="2" /></el-form-item>
              </div>
              <el-form-item label="状态">
                <el-select v-model="serviceItemForm.status">
                  <el-option label="上架" value="ON" />
                  <el-option label="下架" value="OFF" />
                </el-select>
              </el-form-item>
              <div class="toolbar">
                <el-button type="primary" @click="saveServiceItem">保存项目</el-button>
                <el-button @click="resetServiceForm">清空</el-button>
              </div>
            </el-form>

            <div>
              <div class="table-toolbar">
                <el-input v-model="serviceKeyword" placeholder="服务 / 分类 / 简介" clearable />
                <el-select v-model="serviceStatus" placeholder="状态" clearable>
                  <el-option label="上架" value="ON" />
                  <el-option label="下架" value="OFF" />
                </el-select>
              </div>
              <el-table :data="filteredServices" stripe>
                <el-table-column prop="id" label="ID" width="76" />
                <el-table-column label="服务项目" min-width="220">
                  <template #default="{ row }">
                    <div class="name-cell">
                      <strong>{{ row.name }}</strong>
                      <span>{{ row.intro || '暂无简介' }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column label="分类" min-width="120">
                  <template #default="{ row }">{{ categoryMap[row.categoryId] || '-' }}</template>
                </el-table-column>
                <el-table-column prop="durationMin" label="时长" width="90" />
                <el-table-column label="价格" width="120">
                  <template #default="{ row }">{{ money(row.price) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="100" fixed="right">
                  <template #default="{ row }">
                    <el-button size="small" @click="editService(row)">编辑</el-button>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="服务分类" name="categories">
        <section class="admin-panel">
          <div class="split-layout">
            <el-form label-position="top" class="edit-form">
              <h3>{{ categoryForm.id ? '编辑服务分类' : '新增服务分类' }}</h3>
              <el-form-item label="分类名称"><el-input v-model="categoryForm.name" maxlength="64" /></el-form-item>
              <el-form-item label="排序"><el-input-number v-model="categoryForm.sortNo" :min="0" /></el-form-item>
              <div class="toolbar">
                <el-button type="primary" @click="saveCategory">保存分类</el-button>
                <el-button @click="resetCategoryForm">清空</el-button>
              </div>
            </el-form>
            <el-table :data="categories" stripe>
              <el-table-column prop="id" label="ID" width="90" />
              <el-table-column prop="name" label="分类名称" />
              <el-table-column prop="sortNo" label="排序" width="120" />
              <el-table-column label="操作" width="100">
                <template #default="{ row }">
                  <el-button size="small" @click="editCategory(row)">编辑</el-button>
                </template>
              </el-table-column>
            </el-table>
          </div>
        </section>
      </el-tab-pane>

      <el-tab-pane label="技师管理" name="technicians">
        <section class="admin-panel">
          <div class="split-layout">
            <el-form label-position="top" class="edit-form">
              <h3>{{ technicianForm.id ? '编辑技师' : '新增技师' }}</h3>
              <el-form-item label="绑定账号">
                <el-select
                  v-model="technicianForm.userId"
                  filterable
                  :disabled="Boolean(technicianForm.id)"
                  placeholder="搜索昵称 / 用户名 / 手机号，账号 ID 会保存为 userId"
                  @change="handleTechnicianUserChange"
                >
                  <el-option
                    v-for="user in technicianUserOptions"
                    :key="user.id"
                    :label="userOptionLabel(user)"
                    :value="user.id"
                    :disabled="userOptionDisabled(user) || user.enabled !== 1"
                  >
                    <div class="user-option">
                      <strong>{{ user.nickname || user.username }}</strong>
                      <span>
                        {{ user.username }} · {{ user.phone || '未填手机号' }} · {{ user.roleCode === 'TECH' ? '技师账号' : '普通用户' }}
                        · {{ user.enabled === 1 ? '启用' : '禁用' }}
                        <template v-if="userOptionDisabled(user)"> · 已绑定其他技师</template>
                      </span>
                    </div>
                  </el-option>
                </el-select>
              </el-form-item>
              <el-form-item label="技师名称"><el-input v-model="technicianForm.name" maxlength="64" /></el-form-item>
              <el-form-item label="等级">
                <el-select v-model="technicianForm.levelName" placeholder="请选择技师等级">
                  <el-option v-for="item in technicianLevelOptions" :key="item" :label="item" :value="item" />
                </el-select>
              </el-form-item>
              <el-form-item label="管理员覆盖分">
                <div class="score-editor">
                  <el-input-number v-model="technicianForm.scoreOverride" :min="0" :max="5" :precision="1" />
                  <el-button plain @click="technicianForm.scoreOverride = null">使用自动评分</el-button>
                </div>
              </el-form-item>
              <el-form-item label="状态">
                <el-select v-model="technicianForm.status">
                  <el-option label="在职" value="ACTIVE" />
                  <el-option label="停用" value="INACTIVE" />
                </el-select>
              </el-form-item>
              <div class="toolbar">
                <el-button type="primary" @click="saveTechnician">保存技师</el-button>
                <el-button @click="resetTechnicianForm">清空</el-button>
              </div>
            </el-form>

            <div>
              <div class="table-toolbar">
                <el-input v-model="technicianKeyword" placeholder="技师 / 等级 / 状态" clearable />
              </div>
              <el-table :data="filteredTechnicians" stripe>
                <el-table-column prop="id" label="ID" width="76" />
                <el-table-column prop="name" label="技师" min-width="120" />
                <el-table-column label="绑定账号" min-width="220">
                  <template #default="{ row }">
                    <div class="name-cell">
                      <strong>{{ userMap[row.userId]?.nickname || userMap[row.userId]?.username || `用户 #${row.userId}` }}</strong>
                      <span>{{ userMap[row.userId]?.username || '-' }} · {{ userMap[row.userId]?.phone || '未填手机号' }} · {{ userMap[row.userId]?.roleName || '-' }}</span>
                    </div>
                  </template>
                </el-table-column>
                <el-table-column prop="levelName" label="等级" min-width="120" />
                <el-table-column label="有效评分" width="100">
                  <template #default="{ row }">{{ scoreText(row.score) }}</template>
                </el-table-column>
                <el-table-column label="评价数" width="90">
                  <template #default="{ row }">{{ row.ratingCount || 0 }}</template>
                </el-table-column>
                <el-table-column label="覆盖分" width="100">
                  <template #default="{ row }">{{ scoreText(row.scoreOverride) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="100">
                  <template #default="{ row }">
                    <el-tag :type="statusType(row.status)" effect="plain">{{ statusLabel(row.status) }}</el-tag>
                  </template>
                </el-table-column>
                <el-table-column label="操作" width="210" fixed="right">
                  <template #default="{ row }">
                    <div class="toolbar nowrap">
                      <el-button size="small" @click="editTechnician(row)">编辑</el-button>
                      <el-button size="small" plain @click="openAbilityDialog(row)">服务能力</el-button>
                    </div>
                  </template>
                </el-table-column>
              </el-table>
            </div>
          </div>
        </section>
      </el-tab-pane>
    </el-tabs>

    <el-dialog v-model="abilityDialogVisible" :title="`服务能力 - ${currentTechnician?.name || ''}`" width="620px">
      <el-checkbox-group v-model="abilityIds" class="ability-grid">
        <el-checkbox v-for="item in serviceItems" :key="item.id" :label="item.id">
          {{ item.name }}
          <span>{{ categoryMap[item.categoryId] || '-' }}</span>
        </el-checkbox>
      </el-checkbox-group>
      <template #footer>
        <div class="toolbar end">
          <el-button @click="abilityDialogVisible = false">取消</el-button>
          <el-button type="primary" @click="saveAbility">保存</el-button>
        </div>
      </template>
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

.admin-toolbar h2 {
  margin: 4px 0 0;
  font-size: 22px;
}

.admin-kicker {
  margin: 0;
  color: var(--muted);
  font-size: 13px;
}

.split-layout {
  display: grid;
  grid-template-columns: 340px minmax(0, 1fr);
  gap: 18px;
}

.edit-form {
  border: 1px solid var(--line);
  border-radius: 8px;
  padding: 14px;
  background: var(--surface);
}

.edit-form h3 {
  margin: 0 0 12px;
  font-size: 17px;
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
  width: 140px;
}

.form-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}

.score-editor {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.name-cell {
  display: grid;
  gap: 2px;
}

.name-cell span,
.ability-grid span {
  color: var(--muted);
  font-size: 13px;
}

.nowrap {
  flex-wrap: nowrap;
}

.ability-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 8px 14px;
}

.ability-grid :deep(.el-checkbox) {
  height: auto;
  align-items: flex-start;
  white-space: normal;
}

.ability-grid :deep(.el-checkbox__label) {
  display: grid;
  line-height: 1.45;
}

.user-option {
  display: grid;
  line-height: 1.35;
}

.user-option span {
  color: var(--muted);
  font-size: 12px;
}

.end {
  justify-content: flex-end;
}

@media (max-width: 980px) {
  .split-layout,
  .form-grid,
  .ability-grid {
    grid-template-columns: 1fr;
  }

  .table-toolbar .el-input,
  .table-toolbar .el-select {
    width: 100%;
  }
}
</style>
