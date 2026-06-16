<script setup lang="ts">
import { onMounted, reactive, ref, computed } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage, ElMessageBox } from 'element-plus';
import { api } from '../../api/modules';
import type { UserAddress } from '../../api/types';

const router = useRouter();
const list = ref<UserAddress[]>([]);
const loading = ref(false);
const dialogVisible = ref(false);
const submitting = ref(false);
const pager = reactive({
  pageNum: 1,
  pageSize: 6,
});

const pagedList = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return list.value.slice(start, start + pager.pageSize);
});

function handlePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function handlePageSizeChange(pageSize: number) {
  pager.pageSize = pageSize;
  pager.pageNum = 1;
}

const form = reactive<UserAddress>({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  tagName: '常用',
  isDefault: 0,
});

async function loadAddresses() {
  loading.value = true;
  try {
    const { data } = await api.userAddresses();
    list.value = data.data;
  } finally {
    loading.value = false;
  }
}

function resetForm() {
  form.id = undefined;
  form.receiverName = '';
  form.receiverPhone = '';
  form.province = '';
  form.city = '';
  form.district = '';
  form.detailAddress = '';
  form.tagName = '常用';
  form.isDefault = 0;
}

function openCreate() {
  resetForm();
  dialogVisible.value = true;
}

function openEdit(item: UserAddress) {
  form.id = item.id;
  form.receiverName = item.receiverName;
  form.receiverPhone = item.receiverPhone;
  form.province = item.province;
  form.city = item.city;
  form.district = item.district;
  form.detailAddress = item.detailAddress;
  form.tagName = item.tagName || '常用';
  form.isDefault = item.isDefault;
  dialogVisible.value = true;
}

async function saveAddress() {
  submitting.value = true;
  try {
    await api.saveUserAddress({ ...form });
    ElMessage.success(form.id ? '地址已更新' : '地址已新增');
    dialogVisible.value = false;
    await loadAddresses();
  } finally {
    submitting.value = false;
  }
}

async function setDefault(id?: number) {
  if (!id) return;
  await api.setDefaultUserAddress(id);
  ElMessage.success('已设为默认地址');
  await loadAddresses();
}

async function removeAddress(item: UserAddress) {
  if (!item.id) return;
  try {
    await ElMessageBox.confirm(`确认删除“${item.receiverName}”这条地址吗？`, '删除地址', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await api.deleteUserAddress(item.id);
    ElMessage.success('地址已删除');
    await loadAddresses();
  } catch {
    // noop
  }
}

onMounted(() => {
  void loadAddresses();
});
</script>

<template>
  <div class="page-shell">
    <section class="panel address-panel" v-loading="loading">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">收货地址</h2>
          <p class="panel-subtitle">收货地址会在下单时做快照保存，后续即使修改地址也不会影响历史订单展示。</p>
        </div>
        <div class="toolbar">
          <el-button plain @click="router.push('/shop/cart')">返回购物车</el-button>
          <el-button type="warning" @click="openCreate">新增地址</el-button>
        </div>
      </div>

      <el-empty v-if="list.length === 0" description="还没有收货地址，先新增一条吧" />

      <div v-else class="address-grid">
        <article v-for="item in pagedList" :key="item.id" class="address-card">
          <div class="toolbar">
            <div>
              <h3>{{ item.receiverName }} {{ item.receiverPhone }}</h3>
              <p>{{ item.tagName || '常用地址' }}</p>
            </div>
            <el-tag v-if="item.isDefault === 1" type="warning">默认地址</el-tag>
          </div>
          <p class="address-text">{{ item.province }}{{ item.city }}{{ item.district }}{{ item.detailAddress }}</p>
          <div class="toolbar">
            <el-button size="small" @click="openEdit(item)">编辑</el-button>
            <el-button v-if="item.isDefault !== 1" size="small" type="warning" plain @click="setDefault(item.id)">
              设为默认
            </el-button>
            <el-button size="small" type="danger" plain @click="removeAddress(item)">删除</el-button>
          </div>
        </article>
      </div>

      <div v-if="list.length > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="total, sizes, prev, pager, next"
          :current-page="pager.pageNum"
          :page-size="pager.pageSize"
          :page-sizes="[6, 12, 24]"
          :total="list.length"
          @current-change="handlePageChange"
          @size-change="handlePageSizeChange"
        />
      </div>
    </section>

    <el-dialog v-model="dialogVisible" :title="form.id ? '编辑地址' : '新增地址'" width="560px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="收货人">
            <el-input v-model="form.receiverName" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="form.receiverPhone" />
          </el-form-item>
          <el-form-item label="省份">
            <el-input v-model="form.province" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="form.city" />
          </el-form-item>
          <el-form-item label="区县">
            <el-input v-model="form.district" />
          </el-form-item>
          <el-form-item label="地址标签">
            <el-input v-model="form.tagName" placeholder="如：家、公司、宿舍" />
          </el-form-item>
        </div>
        <el-form-item label="详细地址">
          <el-input v-model="form.detailAddress" type="textarea" :rows="3" />
        </el-form-item>
        <el-form-item>
          <el-checkbox v-model="form.isDefault" :true-value="1" :false-value="0">设为默认地址</el-checkbox>
        </el-form-item>
      </el-form>
      <template #footer>
        <div class="toolbar">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="warning" :loading="submitting" @click="saveAddress">保存地址</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.address-panel {
  padding: 26px;
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.address-card {
  display: grid;
  gap: 16px;
  padding: 20px;
  border-radius: 24px;
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 250, 243, 0.88);
}

.address-card h3,
.address-card p {
  margin: 0;
}

.address-card p {
  color: var(--muted);
}

.address-text {
  line-height: 1.7;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.pagination-wrap {
  margin-top: 18px;
  display: flex;
  justify-content: flex-end;
}

@media (max-width: 720px) {
  .form-grid {
    grid-template-columns: 1fr;
  }
}
</style>
