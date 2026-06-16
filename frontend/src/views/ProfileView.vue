<script setup lang="ts">
import { computed, nextTick, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import { updateProfile as authUpdateProfile, uploadAvatar } from '../api/auth';
import { api } from '../api/modules';
import type { UserAddress } from '../api/types';
import { useAuthStore } from '../store/auth';
import { resolveAvatar } from '../utils/avatar';
import { isValidMainlandPhone, normalizePhone } from '../utils/validators';

type ProfileSection = 'basic' | 'security' | 'addresses';

const auth = useAuthStore();

const loading = ref(false);
const profileSubmitting = ref(false);
const securitySubmitting = ref(false);
const avatarSubmitting = ref(false);
const addressList = ref<UserAddress[]>([]);
const dialogVisible = ref(false);
const addressSubmitting = ref(false);
const activeSection = ref<ProfileSection>('basic');
const fileInputRef = ref<HTMLInputElement | null>(null);
const addressPager = reactive({
  pageNum: 1,
  pageSize: 6,
});

const profileForm = reactive({
  nickname: '',
  phone: '',
  avatarUrl: '' as string | null,
  currentPassword: '',
  newPassword: '',
  confirmPassword: '',
});

const addressForm = reactive<UserAddress>({
  receiverName: '',
  receiverPhone: '',
  province: '',
  city: '',
  district: '',
  detailAddress: '',
  tagName: '常用',
  isDefault: 0,
});

const isUserRole = computed(() => auth.user?.roleCode === 'USER');
const avatarPreview = computed(() => resolveAvatar(profileForm.avatarUrl, auth.user?.roleCode, auth.displayName));
const defaultAddress = computed(() => addressList.value.find((item) => item.isDefault === 1));
const pagedAddressList = computed(() => {
  const start = (addressPager.pageNum - 1) * addressPager.pageSize;
  return addressList.value.slice(start, start + addressPager.pageSize);
});

const roleLabel = computed(() => {
  switch (auth.user?.roleCode) {
    case 'ADMIN':
      return '管理员';
    case 'TECH':
      return '技师';
    default:
      return '普通用户';
  }
});

const phoneStatus = computed(() => (auth.user?.phone ? '已绑定手机号' : '未绑定手机号'));
const accountMeta = computed(() => [
  { label: '用户 ID', value: auth.user?.userId ?? '-' },
  { label: '登录账号', value: auth.user?.username ?? '-' },
  { label: '当前角色', value: roleLabel.value },
  { label: '手机号状态', value: phoneStatus.value },
]);

const sectionItems = computed(() => {
  const items: Array<{ key: ProfileSection; title: string; desc: string }> = [
    {
      key: 'basic' as const,
      title: '基本资料',
      desc: '头像、昵称与联系方式',
    },
    {
      key: 'security' as const,
      title: '账号安全',
      desc: '修改登录密码',
    },
  ];
  if (isUserRole.value) {
    items.push({
      key: 'addresses' as const,
      title: '收货地址',
      desc: '商城订单配送信息',
    });
  }
  return items;
});

function syncProfileForm() {
  profileForm.nickname = auth.user?.nickname || '';
  profileForm.phone = auth.user?.phone || '';
  profileForm.avatarUrl = auth.user?.avatarUrl || null;
  profileForm.currentPassword = '';
  profileForm.newPassword = '';
  profileForm.confirmPassword = '';
}

function validateProfilePhone() {
  if (!isValidMainlandPhone(profileForm.phone)) {
    ElMessage.warning('手机号需为 11 位大陆手机号');
    return false;
  }
  return true;
}

function validateSecurityForm() {
  const hasAnyPassword = Boolean(profileForm.currentPassword || profileForm.newPassword || profileForm.confirmPassword);
  if (!hasAnyPassword) {
    ElMessage.info('未填写新密码，本次不会修改登录密码');
    return false;
  }
  if (!profileForm.currentPassword) {
    ElMessage.warning('修改密码前请输入当前密码');
    return false;
  }
  if (!profileForm.newPassword) {
    ElMessage.warning('请输入新密码');
    return false;
  }
  if (profileForm.newPassword !== profileForm.confirmPassword) {
    ElMessage.warning('两次输入的新密码不一致');
    return false;
  }
  return true;
}

async function loadAddresses() {
  if (!isUserRole.value) {
    addressList.value = [];
    return;
  }
  const { data } = await api.userAddresses();
  addressList.value = data.data || [];
  const maxPage = Math.max(1, Math.ceil(addressList.value.length / addressPager.pageSize));
  if (addressPager.pageNum > maxPage) {
    addressPager.pageNum = maxPage;
  }
}

async function refreshProfileAndAddresses() {
  loading.value = true;
  try {
    await auth.loadMe();
    syncProfileForm();
    await loadAddresses();
  } finally {
    loading.value = false;
  }
}

async function saveBasicProfile() {
  if (!validateProfilePhone()) {
    return;
  }

  profileSubmitting.value = true;
  try {
    const { data } = await authUpdateProfile({
      nickname: profileForm.nickname.trim(),
      phone: normalizePhone(profileForm.phone) || undefined,
      avatarUrl: profileForm.avatarUrl,
    });
    auth.setUser(data.data);
    syncProfileForm();
    ElMessage.success('个人资料已更新');
  } finally {
    profileSubmitting.value = false;
  }
}

async function saveSecurity() {
  if (!validateSecurityForm() || !validateProfilePhone()) {
    return;
  }

  securitySubmitting.value = true;
  try {
    const { data } = await authUpdateProfile({
      nickname: profileForm.nickname.trim(),
      phone: normalizePhone(profileForm.phone) || undefined,
      avatarUrl: profileForm.avatarUrl,
      currentPassword: profileForm.currentPassword,
      newPassword: profileForm.newPassword,
    });
    auth.setUser(data.data);
    syncProfileForm();
    ElMessage.success('登录密码已更新');
  } finally {
    securitySubmitting.value = false;
  }
}

function triggerAvatarInput() {
  fileInputRef.value?.click();
}

async function handleAvatarChange(event: Event) {
  const input = event.target as HTMLInputElement;
  const file = input.files?.[0];
  input.value = '';
  if (!file) {
    return;
  }

  const allowedTypes = ['image/jpeg', 'image/png', 'image/webp'];
  if (!allowedTypes.includes(file.type)) {
    ElMessage.warning('头像仅支持 jpg、png、webp 图片');
    return;
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.warning('头像大小不能超过 5MB');
    return;
  }
  if (!validateProfilePhone()) {
    return;
  }

  avatarSubmitting.value = true;
  try {
    const { data: uploadData } = await uploadAvatar(file);
    profileForm.avatarUrl = uploadData.data;
    const { data: profileData } = await authUpdateProfile({
      nickname: profileForm.nickname.trim(),
      phone: normalizePhone(profileForm.phone) || undefined,
      avatarUrl: uploadData.data,
    });
    auth.setUser(profileData.data);
    syncProfileForm();
    ElMessage.success('头像已更新');
  } finally {
    avatarSubmitting.value = false;
  }
}

function resetAddressForm() {
  addressForm.id = undefined;
  addressForm.receiverName = '';
  addressForm.receiverPhone = '';
  addressForm.province = '';
  addressForm.city = '';
  addressForm.district = '';
  addressForm.detailAddress = '';
  addressForm.tagName = '常用';
  addressForm.isDefault = 0;
}

function openCreateAddress() {
  resetAddressForm();
  dialogVisible.value = true;
}

function openEditAddress(item: UserAddress) {
  Object.assign(addressForm, { ...item });
  dialogVisible.value = true;
}

function validateAddressForm() {
  if (!addressForm.receiverName.trim()) {
    ElMessage.warning('请输入收货人');
    return false;
  }
  if (!isValidMainlandPhone(addressForm.receiverPhone)) {
    ElMessage.warning('请输入 11 位大陆联系电话');
    return false;
  }
  if (!addressForm.province.trim() || !addressForm.city.trim() || !addressForm.district.trim() || !addressForm.detailAddress.trim()) {
    ElMessage.warning('请完整填写省市区和详细地址');
    return false;
  }
  return true;
}

async function saveAddress() {
  if (!validateAddressForm()) {
    return;
  }
  addressSubmitting.value = true;
  try {
    await api.saveUserAddress({
      ...addressForm,
      receiverPhone: normalizePhone(addressForm.receiverPhone) || addressForm.receiverPhone,
    });
    ElMessage.success(addressForm.id ? '地址已更新' : '地址已新增');
    dialogVisible.value = false;
    await loadAddresses();
  } finally {
    addressSubmitting.value = false;
  }
}

async function setDefaultAddress(id?: number) {
  if (!id) {
    return;
  }
  await api.setDefaultUserAddress(id);
  ElMessage.success('已设为默认地址');
  await loadAddresses();
}

async function removeAddress(item: UserAddress) {
  if (!item.id) {
    return;
  }
  try {
    await ElMessageBox.confirm(`确认删除 ${item.receiverName} 的收货地址吗？`, '删除地址', {
      type: 'warning',
      confirmButtonText: '确认删除',
      cancelButtonText: '取消',
    });
    await api.deleteUserAddress(item.id);
    ElMessage.success('地址已删除');
    await loadAddresses();
  } catch {
    // 用户取消删除时不提示。
  }
}

function handleAddressPageChange(pageNum: number) {
  addressPager.pageNum = pageNum;
}

function handleAddressSizeChange(pageSize: number) {
  addressPager.pageSize = pageSize;
  addressPager.pageNum = 1;
}

function switchSection(section: ProfileSection) {
  activeSection.value = section;
}

watch(
  () => auth.user,
  () => {
    syncProfileForm();
  },
  { immediate: true },
);

watch(isUserRole, (value) => {
  if (!value && activeSection.value === 'addresses') {
    activeSection.value = 'basic';
  }
});

onMounted(async () => {
  await nextTick();
  await refreshProfileAndAddresses();
});
</script>

<template>
  <div class="profile-page" v-loading="loading">
    <section class="panel profile-hero">
      <div class="hero-identity">
        <div class="avatar-wrap">
          <img :src="avatarPreview" alt="用户头像" class="profile-avatar" />
          <button class="avatar-action" type="button" :disabled="avatarSubmitting" @click="triggerAvatarInput">
            {{ avatarSubmitting ? '上传中' : '更换头像' }}
          </button>
          <input ref="fileInputRef" class="hidden-input" type="file" accept=".jpg,.jpeg,.png,.webp" @change="handleAvatarChange" />
        </div>
        <div>
          <p class="section-kicker">ACCOUNT CENTER</p>
          <h1>{{ auth.displayName }}</h1>
          <p class="hero-copy">
            管理你在 BFSM 中展示的头像、昵称、联系方式和收货地址。这些信息会用于预约记录、在线沟通和商城订单。
          </p>
          <div class="identity-tags">
            <el-tag type="warning">{{ roleLabel }}</el-tag>
            <el-tag :type="auth.user?.phone ? 'success' : 'info'">{{ phoneStatus }}</el-tag>
            <el-tag effect="plain">账号 {{ auth.user?.username || '-' }}</el-tag>
          </div>
        </div>
      </div>

      <div class="hero-actions">
        <el-button plain @click="syncProfileForm">重置资料</el-button>
        <el-button type="warning" :loading="profileSubmitting" @click="saveBasicProfile">保存资料</el-button>
      </div>
    </section>

    <section class="profile-layout">
      <aside class="profile-sidebar">
        <article class="panel account-card">
          <div class="account-card-header">
            <span>账户总览</span>
            <strong>{{ roleLabel }}</strong>
          </div>
          <div class="account-meta">
            <div v-for="item in accountMeta" :key="item.label">
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </article>

        <nav class="panel section-nav" aria-label="个人资料分区">
          <button
            v-for="item in sectionItems"
            :key="item.key"
            type="button"
            :class="{ active: activeSection === item.key }"
            @click="switchSection(item.key)"
          >
            <strong>{{ item.title }}</strong>
            <span>{{ item.desc }}</span>
          </button>
        </nav>
      </aside>

      <main class="profile-content">
        <article v-show="activeSection === 'basic'" class="panel content-panel">
          <div class="panel-header">
            <div>
              <p class="section-kicker">PUBLIC PROFILE</p>
              <h2 class="panel-title">基本资料</h2>
              <p class="panel-subtitle">昵称和头像会展示在工作台、聊天消息、预约和订单记录中。</p>
            </div>
          </div>

          <el-form label-position="top">
            <div class="form-grid">
              <el-form-item label="昵称">
                <el-input v-model="profileForm.nickname" maxlength="30" placeholder="请输入展示昵称" />
              </el-form-item>
              <el-form-item label="手机号">
                <el-input v-model="profileForm.phone" maxlength="11" placeholder="11 位大陆手机号" />
              </el-form-item>
            </div>
          </el-form>

          <div class="profile-note">
            <strong>资料展示说明</strong>
            <p>手机号用于预约、订单和门店联系；头像和昵称用于顶部账户区、在线沟通和后台用户列表。</p>
          </div>

          <div class="toolbar">
            <el-button plain @click="syncProfileForm">取消修改</el-button>
            <el-button type="warning" :loading="profileSubmitting" @click="saveBasicProfile">保存基本资料</el-button>
          </div>
        </article>

        <article v-show="activeSection === 'security'" class="panel content-panel">
          <div class="panel-header">
            <div>
              <p class="section-kicker">SIGN-IN SECURITY</p>
              <h2 class="panel-title">账号安全</h2>
              <p class="panel-subtitle">修改登录密码时需要输入当前密码；留空则不会修改密码。</p>
            </div>
          </div>

          <div class="security-banner">
            <strong>安全建议</strong>
            <span>建议使用不低于 8 位且包含字母和数字的密码，避免与其他网站复用。</span>
          </div>

          <el-form label-position="top">
            <div class="form-grid">
              <el-form-item label="当前密码">
                <el-input v-model="profileForm.currentPassword" type="password" show-password maxlength="30" placeholder="修改密码时必填" />
              </el-form-item>
              <el-form-item label="新密码">
                <el-input v-model="profileForm.newPassword" type="password" show-password maxlength="30" placeholder="留空表示不修改" />
              </el-form-item>
            </div>
            <el-form-item label="确认新密码">
              <el-input v-model="profileForm.confirmPassword" type="password" show-password maxlength="30" placeholder="再次输入新密码" />
            </el-form-item>
          </el-form>

          <div class="toolbar">
            <el-button plain @click="syncProfileForm">清空密码输入</el-button>
            <el-button type="warning" :loading="securitySubmitting" @click="saveSecurity">更新密码</el-button>
          </div>
        </article>

        <article v-if="isUserRole" v-show="activeSection === 'addresses'" class="panel content-panel">
          <div class="panel-header">
            <div>
              <p class="section-kicker">SHIPPING DETAILS</p>
              <h2 class="panel-title">收货地址</h2>
              <p class="panel-subtitle">商城下单会保存地址快照，这里维护后续下单可选的常用地址。</p>
            </div>
            <el-button type="warning" @click="openCreateAddress">新增地址</el-button>
          </div>

          <div v-if="defaultAddress" class="default-address">
            <span>默认配送地址</span>
            <strong>{{ defaultAddress.receiverName }} · {{ defaultAddress.receiverPhone }}</strong>
            <p>{{ defaultAddress.province }}{{ defaultAddress.city }}{{ defaultAddress.district }}{{ defaultAddress.detailAddress }}</p>
          </div>

          <el-empty v-if="addressList.length === 0" description="还没有收货地址，先新增一条吧" />

          <div v-else class="address-section">
            <div class="address-grid">
              <article v-for="item in pagedAddressList" :key="item.id" class="address-card">
                <div class="address-card-top">
                  <div>
                    <h3>{{ item.receiverName }}</h3>
                    <p>{{ item.receiverPhone }}</p>
                  </div>
                  <el-tag v-if="item.isDefault === 1" type="warning">默认</el-tag>
                  <el-tag v-else effect="plain">{{ item.tagName || '常用地址' }}</el-tag>
                </div>

                <p class="address-text">{{ item.province }}{{ item.city }}{{ item.district }}{{ item.detailAddress }}</p>

                <div class="address-actions">
                  <el-button size="small" @click="openEditAddress(item)">编辑</el-button>
                  <el-button v-if="item.isDefault !== 1" size="small" type="warning" plain @click="setDefaultAddress(item.id)">设为默认</el-button>
                  <el-button size="small" type="danger" plain @click="removeAddress(item)">删除</el-button>
                </div>
              </article>
            </div>
            <div class="pagination-wrap">
              <el-pagination
                background
                layout="total, sizes, prev, pager, next"
                :current-page="addressPager.pageNum"
                :page-size="addressPager.pageSize"
                :page-sizes="[6, 12, 24]"
                :total="addressList.length"
                @current-change="handleAddressPageChange"
                @size-change="handleAddressSizeChange"
              />
            </div>
          </div>
        </article>
      </main>
    </section>

    <el-dialog v-model="dialogVisible" :title="addressForm.id ? '编辑收货地址' : '新增收货地址'" width="640px">
      <el-form label-position="top">
        <div class="form-grid">
          <el-form-item label="收货人">
            <el-input v-model="addressForm.receiverName" maxlength="30" />
          </el-form-item>
          <el-form-item label="联系电话">
            <el-input v-model="addressForm.receiverPhone" maxlength="11" />
          </el-form-item>
          <el-form-item label="省份">
            <el-input v-model="addressForm.province" />
          </el-form-item>
          <el-form-item label="城市">
            <el-input v-model="addressForm.city" />
          </el-form-item>
          <el-form-item label="区县">
            <el-input v-model="addressForm.district" />
          </el-form-item>
          <el-form-item label="地址标签">
            <el-input v-model="addressForm.tagName" placeholder="如：家、公司、宿舍" />
          </el-form-item>
        </div>

        <el-form-item label="详细地址">
          <el-input v-model="addressForm.detailAddress" type="textarea" :rows="3" />
        </el-form-item>

        <el-form-item>
          <el-checkbox v-model="addressForm.isDefault" :true-value="1" :false-value="0">设为默认地址</el-checkbox>
        </el-form-item>
      </el-form>

      <template #footer>
        <div class="toolbar dialog-toolbar">
          <el-button @click="dialogVisible = false">取消</el-button>
          <el-button type="warning" :loading="addressSubmitting" @click="saveAddress">保存地址</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.profile-page {
  display: grid;
  gap: 22px;
}

.profile-hero {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 24px;
  padding: 28px;
  overflow: hidden;
  background:
    linear-gradient(135deg, rgba(255, 253, 248, 0.96), rgba(247, 238, 222, 0.9)),
    radial-gradient(circle at top right, rgba(186, 140, 66, 0.22), transparent 44%);
}

.hero-identity {
  display: flex;
  align-items: center;
  min-width: 0;
  gap: 22px;
}

.avatar-wrap {
  position: relative;
  flex: 0 0 auto;
}

.profile-avatar {
  width: 118px;
  height: 118px;
  display: block;
  border: 4px solid rgba(255, 255, 255, 0.86);
  border-radius: 32px;
  object-fit: cover;
  box-shadow: 0 18px 38px rgba(122, 86, 37, 0.18);
}

.avatar-action {
  position: absolute;
  right: -10px;
  bottom: -10px;
  border: 1px solid rgba(122, 86, 37, 0.18);
  border-radius: 999px;
  padding: 8px 12px;
  color: #fff;
  font: inherit;
  font-size: 14px;
  cursor: pointer;
  background: linear-gradient(135deg, var(--accent), var(--accent-deep));
  box-shadow: 0 12px 26px rgba(68, 43, 13, 0.18);
}

.avatar-action:disabled {
  cursor: not-allowed;
  opacity: 0.72;
}

.hidden-input {
  display: none;
}

.section-kicker {
  margin: 0 0 10px;
  color: var(--accent-deep);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.profile-hero h1 {
  margin: 0;
  font-size: 38px;
  line-height: 1.18;
}

.hero-copy {
  max-width: 720px;
  margin: 12px 0 16px;
  color: var(--muted);
  line-height: 1.8;
}

.identity-tags,
.hero-actions,
.toolbar,
.address-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.hero-actions {
  justify-content: flex-end;
  flex: 0 0 auto;
}

.profile-layout {
  display: grid;
  grid-template-columns: 300px minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.profile-sidebar {
  display: grid;
  gap: 16px;
  position: sticky;
  top: 20px;
}

.account-card,
.section-nav,
.content-panel {
  padding: 24px;
}

.account-card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  gap: 12px;
  margin-bottom: 16px;
}

.account-card-header span,
.account-meta span {
  color: var(--muted);
}

.account-card-header strong {
  color: var(--accent-deep);
}

.account-meta {
  display: grid;
  gap: 12px;
}

.account-meta div {
  padding: 14px;
  border-radius: 18px;
  border: 1px solid rgba(122, 86, 37, 0.1);
  background: rgba(255, 250, 242, 0.76);
}

.account-meta strong,
.account-meta span {
  display: block;
}

.account-meta strong {
  margin-top: 6px;
  font-size: 18px;
  word-break: break-word;
}

.section-nav {
  display: grid;
  gap: 10px;
}

.section-nav button {
  border: 1px solid transparent;
  border-radius: 18px;
  padding: 16px;
  color: var(--text);
  text-align: left;
  font: inherit;
  cursor: pointer;
  background: transparent;
  transition: transform 0.18s ease, background 0.18s ease, border-color 0.18s ease;
}

.section-nav button:hover,
.section-nav button.active {
  transform: translateX(4px);
  border-color: rgba(186, 140, 66, 0.28);
  background: rgba(255, 250, 242, 0.9);
}

.section-nav strong,
.section-nav span {
  display: block;
}

.section-nav span {
  margin-top: 4px;
  color: var(--muted);
  font-size: 15px;
}

.profile-content {
  min-width: 0;
}

.content-panel {
  min-height: 560px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.profile-note,
.security-banner,
.default-address,
.address-card {
  border: 1px solid rgba(122, 86, 37, 0.12);
  border-radius: 22px;
  background: rgba(255, 250, 242, 0.82);
}

.profile-note,
.security-banner,
.default-address {
  padding: 18px;
  margin: 6px 0 22px;
}

.profile-note strong,
.security-banner strong,
.default-address strong {
  display: block;
}

.profile-note p,
.default-address p {
  margin: 8px 0 0;
  color: var(--muted);
  line-height: 1.75;
}

.security-banner {
  display: grid;
  gap: 8px;
  color: var(--muted);
}

.security-banner strong {
  color: var(--text);
}

.default-address span {
  color: var(--accent-deep);
  font-size: 14px;
  font-weight: 700;
}

.address-section {
  display: grid;
  gap: 18px;
}

.address-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 16px;
}

.address-card {
  display: grid;
  gap: 16px;
  padding: 18px;
}

.address-card-top {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 12px;
}

.address-card h3,
.address-card p {
  margin: 0;
}

.address-card-top p,
.address-text {
  color: var(--muted);
}

.address-card h3 {
  font-size: 22px;
}

.address-text {
  line-height: 1.7;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.dialog-toolbar {
  justify-content: flex-end;
}

@media (max-width: 1120px) {
  .profile-hero,
  .hero-identity {
    align-items: flex-start;
    flex-direction: column;
  }

  .hero-actions {
    justify-content: flex-start;
  }

  .profile-layout {
    grid-template-columns: 1fr;
  }

  .profile-sidebar {
    position: static;
  }

  .section-nav {
    grid-template-columns: repeat(auto-fit, minmax(180px, 1fr));
  }

  .section-nav button:hover,
  .section-nav button.active {
    transform: translateY(-2px);
  }
}

@media (max-width: 720px) {
  .profile-hero,
  .account-card,
  .section-nav,
  .content-panel {
    padding: 20px;
  }

  .profile-avatar {
    width: 100px;
    height: 100px;
    border-radius: 26px;
  }

  .profile-hero h1 {
    font-size: 30px;
  }

  .form-grid {
    grid-template-columns: 1fr;
  }

  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }

  .pagination-wrap {
    justify-content: flex-start;
    overflow-x: auto;
  }
}
</style>
