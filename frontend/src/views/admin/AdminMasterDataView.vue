<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { Announcement, ServiceCategory, ServiceItem, Technician } from '../../api/types';

const activeTab = ref('technicians');
const users = ref<any[]>([]);
const technicians = ref<Technician[]>([]);
const categories = ref<ServiceCategory[]>([]);
const serviceItems = ref<ServiceItem[]>([]);
const announcements = ref<Announcement[]>([]);
const pagers = reactive({
  users: { pageNum: 1, pageSize: 8 },
  technicians: { pageNum: 1, pageSize: 8 },
  categories: { pageNum: 1, pageSize: 8 },
  services: { pageNum: 1, pageSize: 8 },
  announcements: { pageNum: 1, pageSize: 8 },
});

const technicianForm = reactive<Partial<Technician>>({ status: 'ACTIVE' });
const categoryForm = reactive<Partial<ServiceCategory>>({ sortNo: 1 });
const serviceItemForm = reactive<Partial<ServiceItem>>({ status: 'ON', durationMin: 60, price: 0 });
const announcementForm = reactive<Partial<Announcement>>({ publishStatus: 'PUBLISHED' });
const pagedUsers = computed(() => paginate(users.value, pagers.users.pageNum, pagers.users.pageSize));
const pagedTechnicians = computed(() => paginate(technicians.value, pagers.technicians.pageNum, pagers.technicians.pageSize));
const pagedCategories = computed(() => paginate(categories.value, pagers.categories.pageNum, pagers.categories.pageSize));
const pagedServiceItems = computed(() => paginate(serviceItems.value, pagers.services.pageNum, pagers.services.pageSize));
const pagedAnnouncements = computed(() => paginate(announcements.value, pagers.announcements.pageNum, pagers.announcements.pageSize));

async function loadData() {
  const [userRes, technicianRes, categoryRes, serviceRes, announcementRes] = await Promise.all([
    api.users(),
    api.technicians(),
    api.categories(),
    api.serviceItems(),
    api.announcements(),
  ]);
  users.value = userRes.data.data;
  technicians.value = technicianRes.data.data;
  categories.value = categoryRes.data.data;
  serviceItems.value = serviceRes.data.data;
  announcements.value = announcementRes.data.data;
  resetPagerBounds();
}

async function saveTechnician() {
  await api.saveTechnician(technicianForm);
  Object.assign(technicianForm, { id: undefined, userId: undefined, name: '', levelName: '', score: 5, status: 'ACTIVE' });
  ElMessage.success('技师信息已保存');
  await loadData();
}

async function saveCategory() {
  await api.saveCategory(categoryForm);
  Object.assign(categoryForm, { id: undefined, name: '', sortNo: 1 });
  ElMessage.success('服务分类已保存');
  await loadData();
}

async function saveServiceItem() {
  await api.saveServiceItem(serviceItemForm);
  resetServiceItemForm();
  ElMessage.success('服务项目已保存');
  await loadData();
}

async function saveAnnouncement() {
  await api.saveAnnouncement(announcementForm);
  Object.assign(announcementForm, { id: undefined, title: '', summary: '', coverImage: '', content: '', publishStatus: 'PUBLISHED' });
  ElMessage.success('公告已保存');
  await loadData();
}

function editServiceItem(row: ServiceItem) {
  Object.assign(serviceItemForm, {
    id: row.id,
    categoryId: row.categoryId,
    name: row.name,
    intro: row.intro || '',
    imageUrl: row.imageUrl || '',
    durationMin: row.durationMin,
    price: row.price,
    status: row.status,
  });
}

function resetServiceItemForm() {
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

function paginate<T>(source: T[], pageNum: number, pageSize: number) {
  const start = (pageNum - 1) * pageSize;
  return source.slice(start, start + pageSize);
}

function handlePageChange(key: keyof typeof pagers, pageNum: number) {
  pagers[key].pageNum = pageNum;
}

function handleSizeChange(key: keyof typeof pagers, pageSize: number) {
  pagers[key].pageSize = pageSize;
  pagers[key].pageNum = 1;
  resetPagerBounds();
}

function resetPagerBounds() {
  (Object.keys(pagers) as (keyof typeof pagers)[]).forEach((key) => {
    const total =
      key === 'users'
        ? users.value.length
        : key === 'technicians'
          ? technicians.value.length
          : key === 'categories'
            ? categories.value.length
            : key === 'services'
              ? serviceItems.value.length
              : announcements.value.length;
    const maxPage = Math.max(1, Math.ceil(total / pagers[key].pageSize));
    if (pagers[key].pageNum > maxPage) {
      pagers[key].pageNum = maxPage;
    }
  });
}

onMounted(loadData);
</script>

<template>
  <div class="page-shell">
    <section class="panel master-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">基础资料管理</h2>
          <p class="panel-subtitle">维护用户查询、技师、服务分类、服务项目和公告资料。</p>
        </div>
      </div>

      <el-tabs v-model="activeTab">
        <el-tab-pane label="用户列表" name="users">
          <el-table :data="pagedUsers" stripe>
            <el-table-column prop="id" label="ID" width="80" />
            <el-table-column prop="username" label="用户名" />
            <el-table-column prop="nickname" label="昵称" />
            <el-table-column prop="phone" label="手机号" />
            <el-table-column prop="enabled" label="启用状态" />
          </el-table>
          <div class="pagination-wrap">
            <el-pagination
              background
              layout="total, sizes, prev, pager, next"
              :current-page="pagers.users.pageNum"
              :page-size="pagers.users.pageSize"
              :page-sizes="[8, 12, 20]"
              :total="users.length"
              @current-change="(pageNum: number) => handlePageChange('users', pageNum)"
              @size-change="(pageSize: number) => handleSizeChange('users', pageSize)"
            />
          </div>
        </el-tab-pane>

        <el-tab-pane label="技师管理" name="technicians">
          <div class="editor-grid">
            <el-form label-position="top">
              <el-form-item label="关联用户 ID"><el-input v-model="technicianForm.userId" /></el-form-item>
              <el-form-item label="技师名称"><el-input v-model="technicianForm.name" /></el-form-item>
              <el-form-item label="等级"><el-input v-model="technicianForm.levelName" /></el-form-item>
              <el-form-item label="评分"><el-input v-model="technicianForm.score" /></el-form-item>
              <el-form-item label="状态"><el-input v-model="technicianForm.status" /></el-form-item>
              <el-button type="warning" @click="saveTechnician">保存技师</el-button>
            </el-form>
            <div class="table-block">
              <el-table :data="pagedTechnicians" stripe>
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="名称" />
                <el-table-column prop="levelName" label="等级" />
                <el-table-column prop="score" label="评分" />
                <el-table-column prop="status" label="状态" />
              </el-table>
              <div class="pagination-wrap">
                <el-pagination
                  background
                  layout="total, sizes, prev, pager, next"
                  :current-page="pagers.technicians.pageNum"
                  :page-size="pagers.technicians.pageSize"
                  :page-sizes="[8, 12, 20]"
                  :total="technicians.length"
                  @current-change="(pageNum: number) => handlePageChange('technicians', pageNum)"
                  @size-change="(pageSize: number) => handleSizeChange('technicians', pageSize)"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="服务分类" name="categories">
          <div class="editor-grid">
            <el-form label-position="top">
              <el-form-item label="分类名称"><el-input v-model="categoryForm.name" /></el-form-item>
              <el-form-item label="排序"><el-input v-model="categoryForm.sortNo" /></el-form-item>
              <el-button type="warning" @click="saveCategory">保存分类</el-button>
            </el-form>
            <div class="table-block">
              <el-table :data="pagedCategories" stripe>
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="名称" />
                <el-table-column prop="sortNo" label="排序" />
              </el-table>
              <div class="pagination-wrap">
                <el-pagination
                  background
                  layout="total, sizes, prev, pager, next"
                  :current-page="pagers.categories.pageNum"
                  :page-size="pagers.categories.pageSize"
                  :page-sizes="[8, 12, 20]"
                  :total="categories.length"
                  @current-change="(pageNum: number) => handlePageChange('categories', pageNum)"
                  @size-change="(pageSize: number) => handleSizeChange('categories', pageSize)"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="服务项目" name="services">
          <div class="editor-grid">
            <el-form label-position="top">
              <el-form-item label="所属分类">
                <el-select v-model="serviceItemForm.categoryId">
                  <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
                </el-select>
              </el-form-item>
              <el-form-item label="项目名称"><el-input v-model="serviceItemForm.name" /></el-form-item>
              <el-form-item label="服务简介"><el-input v-model="serviceItemForm.intro" type="textarea" :rows="3" /></el-form-item>
              <el-form-item label="图片地址"><el-input v-model="serviceItemForm.imageUrl" placeholder="/uploads/... 或 https://..." /></el-form-item>
              <el-form-item label="服务时长（分钟）"><el-input v-model="serviceItemForm.durationMin" /></el-form-item>
              <el-form-item label="价格"><el-input v-model="serviceItemForm.price" /></el-form-item>
              <el-form-item label="状态"><el-input v-model="serviceItemForm.status" /></el-form-item>
              <div class="toolbar">
                <el-button type="warning" @click="saveServiceItem">保存项目</el-button>
                <el-button @click="resetServiceItemForm">清空表单</el-button>
              </div>
            </el-form>
            <div class="table-block">
              <el-table :data="pagedServiceItems" stripe>
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="name" label="项目名称" min-width="150" />
                <el-table-column prop="intro" label="服务简介" min-width="220" show-overflow-tooltip />
                <el-table-column prop="imageUrl" label="图片地址" min-width="220" show-overflow-tooltip />
                <el-table-column prop="durationMin" label="时长" width="90" />
                <el-table-column prop="price" label="价格" width="100" />
                <el-table-column label="操作" width="100">
                  <template #default="{ row }">
                    <el-button size="small" @click="editServiceItem(row)">编辑</el-button>
                  </template>
                </el-table-column>
              </el-table>
              <div class="pagination-wrap">
                <el-pagination
                  background
                  layout="total, sizes, prev, pager, next"
                  :current-page="pagers.services.pageNum"
                  :page-size="pagers.services.pageSize"
                  :page-sizes="[8, 12, 20]"
                  :total="serviceItems.length"
                  @current-change="(pageNum: number) => handlePageChange('services', pageNum)"
                  @size-change="(pageSize: number) => handleSizeChange('services', pageSize)"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>

        <el-tab-pane label="公告管理" name="announcements">
          <div class="editor-grid">
            <el-form label-position="top">
              <el-form-item label="公告标题"><el-input v-model="announcementForm.title" /></el-form-item>
              <el-form-item label="公告摘要"><el-input v-model="announcementForm.summary" /></el-form-item>
              <el-form-item label="封面图片"><el-input v-model="announcementForm.coverImage" /></el-form-item>
              <el-form-item label="公告内容"><el-input v-model="announcementForm.content" type="textarea" :rows="4" /></el-form-item>
              <el-form-item label="发布状态"><el-input v-model="announcementForm.publishStatus" /></el-form-item>
              <el-button type="warning" @click="saveAnnouncement">保存公告</el-button>
            </el-form>
            <div class="table-block">
              <el-table :data="pagedAnnouncements" stripe>
                <el-table-column prop="id" label="ID" width="80" />
                <el-table-column prop="title" label="标题" />
                <el-table-column prop="publishStatus" label="状态" />
                <el-table-column prop="createdAt" label="创建时间" />
              </el-table>
              <div class="pagination-wrap">
                <el-pagination
                  background
                  layout="total, sizes, prev, pager, next"
                  :current-page="pagers.announcements.pageNum"
                  :page-size="pagers.announcements.pageSize"
                  :page-sizes="[8, 12, 20]"
                  :total="announcements.length"
                  @current-change="(pageNum: number) => handlePageChange('announcements', pageNum)"
                  @size-change="(pageSize: number) => handleSizeChange('announcements', pageSize)"
                />
              </div>
            </div>
          </div>
        </el-tab-pane>
      </el-tabs>
    </section>
  </div>
</template>

<style scoped>
.master-panel {
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
  margin-top: 18px;
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
