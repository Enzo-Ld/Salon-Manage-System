<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { useRoute, useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type {
  AppointmentAvailableSlot,
  ServiceCategory,
  ServiceItem,
  Technician,
  TechnicianRecommendation,
} from '../../api/types';

const router = useRouter();
const route = useRoute();

const loading = ref(false);
const slotLoading = ref(false);
const bookingDialogVisible = ref(false);
const categories = ref<ServiceCategory[]>([]);
const serviceItems = ref<ServiceItem[]>([]);
const technicians = ref<Technician[]>([]);
const favoriteServiceIds = ref<number[]>([]);
const slots = ref<AppointmentAvailableSlot[]>([]);
const recommendation = ref<TechnicianRecommendation | null>(null);
const imageLoadErrors = ref(new Set<number>());
const keyword = ref('');
const selectedCategoryId = ref<number | null>(null);
const pager = reactive({
  pageNum: 1,
  pageSize: 16,
});

const form = reactive({
  serviceItemId: undefined as number | undefined,
  technicianId: undefined as number | undefined,
  appointmentDate: '',
  appointmentTime: '',
});

const selectedService = computed(() => serviceItems.value.find((item) => item.id === form.serviceItemId));
const activeTechnicians = computed(() => technicians.value.filter((item) => item.status === 'ACTIVE'));
const selectedSlot = computed(() => slots.value.find((item) => item.startTime === form.appointmentTime) || null);
const filteredServiceItems = computed(() => {
  const normalizedKeyword = keyword.value.trim().toLowerCase();
  return serviceItems.value.filter((item) => {
    const categoryName = getCategoryName(item.categoryId);
    const keywordMatch =
      !normalizedKeyword ||
      [item.name, item.intro, categoryName].some((value) => String(value ?? '').toLowerCase().includes(normalizedKeyword));
    const categoryMatch = !selectedCategoryId.value || item.categoryId === selectedCategoryId.value;
    return keywordMatch && categoryMatch;
  });
});
const pagedServiceItems = computed(() => {
  const start = (pager.pageNum - 1) * pager.pageSize;
  return filteredServiceItems.value.slice(start, start + pager.pageSize);
});

const dateOptions = computed(() =>
  Array.from({ length: 7 }, (_, index) => {
    const date = new Date();
    date.setDate(date.getDate() + index);
    const weekday = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()];
    const value = formatDate(date);
    return {
      value,
      title: index === 0 ? '今天' : weekday,
      subtitle: `${date.getMonth() + 1}/${date.getDate()}`,
      weekday,
    };
  }),
);

const slotGroups = computed(() => {
  const groups = [
    { key: 'morning', label: '上午可约', items: [] as AppointmentAvailableSlot[] },
    { key: 'afternoon', label: '下午可约', items: [] as AppointmentAvailableSlot[] },
    { key: 'evening', label: '晚间可约', items: [] as AppointmentAvailableSlot[] },
  ];

  for (const slot of slots.value) {
    const hour = Number(slot.startTime.slice(11, 13));
    if (hour < 12) {
      groups[0].items.push(slot);
    } else if (hour < 18) {
      groups[1].items.push(slot);
    } else {
      groups[2].items.push(slot);
    }
  }

  return groups;
});

function getCategoryName(categoryId: number) {
  return categories.value.find((category) => category.id === categoryId)?.name || '未分类';
}

function isFavorited(serviceItemId: number) {
  return favoriteServiceIds.value.includes(serviceItemId);
}

function hasServiceImage(item: ServiceItem) {
  return Boolean(item.imageUrl && !imageLoadErrors.value.has(item.id));
}

function markImageError(serviceItemId: number) {
  imageLoadErrors.value = new Set([...imageLoadErrors.value, serviceItemId]);
}

function fallbackTone(serviceItemId: number) {
  const tones = [
    'linear-gradient(135deg, #2563eb 0%, #1d4ed8 100%)',
    'linear-gradient(135deg, #0ea5e9 0%, #2563eb 100%)',
    'linear-gradient(135deg, #3b82f6 0%, #0f766e 100%)',
    'linear-gradient(135deg, #64748b 0%, #2563eb 100%)',
  ];
  return { background: tones[serviceItemId % tones.length] };
}

async function loadData() {
  loading.value = true;
  try {
    const [categoryRes, serviceRes, techRes, favoriteRes] = await Promise.all([
      api.categories(),
      api.serviceItems(),
      api.technicians(),
      api.favoriteServiceItemIds(),
    ]);
    categories.value = categoryRes.data.data;
    serviceItems.value = serviceRes.data.data;
    technicians.value = techRes.data.data;
    favoriteServiceIds.value = favoriteRes.data.data;

    if (!form.appointmentDate && dateOptions.value[0]) {
      form.appointmentDate = dateOptions.value[0].value;
    }
    syncFocusedService();
  } finally {
    loading.value = false;
  }
}

async function loadSlots() {
  if (!form.serviceItemId || !form.appointmentDate) {
    slots.value = [];
    return;
  }

  slotLoading.value = true;
  try {
    const { data } = await api.appointmentAvailableSlots(form.serviceItemId, form.appointmentDate, form.technicianId ?? null);
    slots.value = data.data;
  } finally {
    slotLoading.value = false;
  }
}

async function toggleFavorite(serviceItemId: number) {
  const { data } = await api.toggleServiceFavorite(serviceItemId);
  if (data.data.active) {
    favoriteServiceIds.value = [...new Set([...favoriteServiceIds.value, serviceItemId])];
  } else {
    favoriteServiceIds.value = favoriteServiceIds.value.filter((id) => id !== serviceItemId);
  }
  ElMessage.success(data.data.active ? '已收藏服务项目' : '已取消收藏');
}

async function openBookingDialog(item: ServiceItem) {
  form.serviceItemId = item.id;
  if (!form.appointmentDate && dateOptions.value[0]) {
    form.appointmentDate = dateOptions.value[0].value;
  }
  resetSlotSelection();
  bookingDialogVisible.value = true;
  await loadSlots();
}

function closeBookingDialog() {
  bookingDialogVisible.value = false;
  form.technicianId = undefined;
  resetSlotSelection();
}

function selectSlot(slot: AppointmentAvailableSlot) {
  form.appointmentTime = slot.startTime;
  if (form.technicianId) {
    recommendation.value = null;
    return;
  }
  recommendation.value = {
    technicianId: slot.recommendedTechnicianId || 0,
    technicianName: slot.recommendedTechnicianName || '系统自动分配',
    score: slot.availableTechnicianCount,
    reason: `该时段共有 ${slot.availableTechnicianCount} 位可服务技师，系统会优先推荐当前最合适的技师。`,
    appointmentTime: slot.startTime,
    appointmentEndTime: slot.endTime,
    durationMinSnapshot: selectedService.value?.durationMin,
  };
}

async function submitAppointment() {
  if (!form.serviceItemId || !form.appointmentTime) {
    ElMessage.warning('请先选择服务项目、日期和预约时间段');
    return;
  }

  const { data } = await api.createAppointment({
    serviceItemId: form.serviceItemId,
    technicianId: form.technicianId ?? null,
    appointmentTime: form.appointmentTime,
  });
  ElMessage.success('预约单已创建，请继续完成支付后等待技师确认');
  await router.push(`/appointments?autopay=${data.data.id}`);
}

function resetSlotSelection() {
  form.appointmentTime = '';
  recommendation.value = null;
}

function handleServicePageChange(pageNum: number) {
  pager.pageNum = pageNum;
}

function resetServiceFilters() {
  keyword.value = '';
  selectedCategoryId.value = null;
  pager.pageNum = 1;
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatDateTime(value?: string) {
  if (!value) return '-';
  return value.replace('T', ' ');
}

function syncFocusedService() {
  const raw = route.query.focusServiceId;
  const serviceItemId = typeof raw === 'string' ? Number(raw) : NaN;
  if (Number.isNaN(serviceItemId)) {
    return;
  }
  const matched = serviceItems.value.find((item) => item.id === serviceItemId);
  if (matched) {
    form.serviceItemId = matched.id;
    const index = filteredServiceItems.value.findIndex((item) => item.id === matched.id);
    if (index >= 0) {
      pager.pageNum = Math.floor(index / pager.pageSize) + 1;
    }
  }
}

watch(
  () => [form.serviceItemId, form.technicianId, form.appointmentDate],
  async ([serviceItemId, technicianId, appointmentDate], [prevServiceItemId, prevTechnicianId, prevAppointmentDate]) => {
    if (!serviceItemId || !appointmentDate) {
      slots.value = [];
      resetSlotSelection();
      return;
    }

    if (serviceItemId !== prevServiceItemId || technicianId !== prevTechnicianId || appointmentDate !== prevAppointmentDate) {
      resetSlotSelection();
      if (bookingDialogVisible.value) {
        await loadSlots();
      }
    }
  },
);

watch([serviceItems, keyword, selectedCategoryId], () => {
  pager.pageNum = 1;
});

watch(
  () => route.query.focusServiceId,
  () => {
    syncFocusedService();
  },
);

onMounted(async () => {
  await loadData();
});
</script>

<template>
  <div class="page-shell service-page">
    <section class="service-showcase panel" v-loading="loading">
      <div class="showcase-glow showcase-glow-left"></div>
      <div class="showcase-glow showcase-glow-right"></div>

      <div class="showcase-head">
        <div class="showcase-copy">
          <p class="eyebrow">Beauty service booking</p>
          <h2>选择一项喜欢的服务，把时间交给我们</h2>
          <p>
            浏览服务项目、风格图片与简介。点击服务图片即可打开预约窗口，系统会根据项目时长为你计算可预约时段。
          </p>
        </div>
        <div class="showcase-actions">
          <div class="mini-stat">
            <strong>{{ filteredServiceItems.length }}</strong>
            <span>可预约项目</span>
          </div>
          <el-button plain @click="router.push('/favorites')">我的收藏</el-button>
        </div>
      </div>

      <div class="filter-dock">
        <el-input v-model="keyword" placeholder="搜索服务项目 / 简介 / 分类" clearable />
        <el-select v-model="selectedCategoryId" placeholder="服务分类" clearable>
          <el-option v-for="item in categories" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
        <el-button text @click="resetServiceFilters">重置筛选</el-button>
      </div>

      <div class="service-card-grid">
        <article v-for="(item, index) in pagedServiceItems" :key="item.id" class="service-card" :class="{ featured: index === 0 }">
          <button class="service-image-button" type="button" @click="openBookingDialog(item)">
            <img v-if="hasServiceImage(item)" :src="item.imageUrl || ''" :alt="item.name" @error="markImageError(item.id)" />
            <div v-else class="service-image-fallback" :style="fallbackTone(item.id)">
              <span>{{ item.name.slice(0, 2) }}</span>
            </div>
            <span class="image-shade"></span>
            <span class="image-action">点击预约</span>
            <span class="category-chip">{{ getCategoryName(item.categoryId) }}</span>
          </button>

          <div class="service-card-body">
            <div class="service-card-title">
              <div>
                <p>{{ item.durationMin }} 分钟护理</p>
                <h3>{{ item.name }}</h3>
              </div>
              <button class="favorite-button" type="button" @click="toggleFavorite(item.id)">
                {{ isFavorited(item.id) ? '已收藏' : '收藏' }}
              </button>
            </div>
            <p class="service-intro">{{ item.intro || '暂无服务简介，可在后台基础资料中补充项目说明。' }}</p>
            <div class="service-meta">
              <span>到店专业护理</span>
              <strong>￥{{ Number(item.price).toFixed(2) }}</strong>
            </div>
          </div>
        </article>
      </div>

      <el-empty v-if="filteredServiceItems.length === 0" description="当前筛选条件下暂无服务项目" />

      <div v-if="filteredServiceItems.length > 0" class="pagination-wrap">
        <el-pagination
          background
          layout="total, prev, pager, next"
          :current-page="pager.pageNum"
          :page-size="pager.pageSize"
          :total="filteredServiceItems.length"
          @current-change="handleServicePageChange"
        />
      </div>
    </section>

    <el-dialog
      v-model="bookingDialogVisible"
      width="900px"
      class="booking-dialog"
      :title="selectedService ? `预约 ${selectedService.name}` : '服务预约'"
      @closed="closeBookingDialog"
    >
      <div v-if="selectedService" class="dialog-service-summary">
        <div class="dialog-service-image">
          <img
            v-if="hasServiceImage(selectedService)"
            :src="selectedService.imageUrl || ''"
            :alt="selectedService.name"
            @error="markImageError(selectedService.id)"
          />
          <div v-else class="service-image-fallback" :style="fallbackTone(selectedService.id)">
            <span>{{ selectedService.name.slice(0, 2) }}</span>
          </div>
        </div>
        <div class="dialog-service-copy">
          <span>{{ getCategoryName(selectedService.categoryId) }}</span>
          <h3>{{ selectedService.name }}</h3>
          <p>{{ selectedService.intro || '暂无服务简介。' }}</p>
        </div>
        <div class="dialog-price">
          <strong>￥{{ Number(selectedService.price).toFixed(2) }}</strong>
          <span>{{ selectedService.durationMin }} 分钟</span>
        </div>
      </div>

      <div class="booking-stack">
        <div class="form-block">
          <div class="block-title">1. 预约日期</div>
          <div class="date-grid">
            <button
              v-for="item in dateOptions"
              :key="item.value"
              type="button"
              class="date-card"
              :class="{ active: form.appointmentDate === item.value }"
              @click="form.appointmentDate = item.value"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.subtitle }}</span>
              <small>{{ item.weekday }}</small>
            </button>
          </div>
        </div>

        <div class="form-block">
          <div class="block-title">2. 技师选择</div>
          <el-select v-model="form.technicianId" clearable placeholder="不指定则由系统自动推荐最合适的技师">
            <el-option
              v-for="tech in activeTechnicians"
              :key="tech.id"
              :label="`${tech.name} · ${tech.levelName || '标准技师'} · ${tech.score || 5} 分`"
              :value="tech.id"
            />
          </el-select>
        </div>

        <div class="form-block">
          <div class="block-title">3. 选择时间段</div>
          <div class="slot-wrapper" v-loading="slotLoading">
            <template v-if="slots.length">
              <section v-for="group in slotGroups" v-show="group.items.length" :key="group.key" class="slot-group">
                <div class="slot-group-title">{{ group.label }}</div>
                <div class="slot-grid">
                  <button
                    v-for="slot in group.items"
                    :key="slot.startTime"
                    type="button"
                    class="slot-card"
                    :class="{ active: form.appointmentTime === slot.startTime }"
                    @click="selectSlot(slot)"
                  >
                    <strong>{{ slot.label }}</strong>
                    <span>可服务技师 {{ slot.availableTechnicianCount }} 位</span>
                    <small>{{ slot.technicianNames.join('、') }}</small>
                  </button>
                </div>
              </section>
            </template>
            <el-empty v-else description="当天暂无可预约时间段，请切换日期或更换技师" :image-size="72" />
          </div>
        </div>
      </div>

      <div v-if="selectedSlot" class="summary-box">
        <div class="summary-row"><strong>预约日期：</strong>{{ form.appointmentDate }}</div>
        <div class="summary-row"><strong>预约时段：</strong>{{ selectedSlot.label }}</div>
        <div class="summary-row"><strong>开始时间：</strong>{{ formatDateTime(selectedSlot.startTime) }}</div>
        <div class="summary-row"><strong>预计结束：</strong>{{ formatDateTime(selectedSlot.endTime) }}</div>
      </div>

      <div v-if="recommendation && !form.technicianId" class="recommend-box">
        <strong>系统推荐技师：{{ recommendation.technicianName }}</strong>
        <p>{{ recommendation.reason }}</p>
      </div>

      <template #footer>
        <div class="dialog-footer">
          <el-button @click="closeBookingDialog">取消</el-button>
          <el-button type="warning" :disabled="!selectedSlot" @click="submitAppointment">创建预约并去支付</el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.service-page {
  gap: 0;
}

.service-showcase {
  position: relative;
  overflow: hidden;
  padding: 34px;
  background:
    linear-gradient(135deg, rgba(255, 253, 247, 0.96), rgba(246, 234, 215, 0.86)),
    radial-gradient(circle at 12% 8%, rgba(225, 173, 103, 0.34), transparent 34%),
    radial-gradient(circle at 88% 0%, rgba(122, 86, 37, 0.18), transparent 30%);
}

.showcase-glow {
  position: absolute;
  border-radius: 999px;
  pointer-events: none;
  filter: blur(4px);
}

.showcase-glow-left {
  width: 260px;
  height: 260px;
  left: -90px;
  top: -110px;
  background: rgba(232, 189, 119, 0.28);
}

.showcase-glow-right {
  width: 360px;
  height: 360px;
  right: -150px;
  top: 90px;
  background: rgba(122, 86, 37, 0.1);
}

.showcase-head {
  position: relative;
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 24px;
  margin-bottom: 26px;
}

.showcase-copy {
  max-width: 760px;
}

.eyebrow {
  margin: 0 0 8px;
  color: var(--accent-deep);
  font-size: 13px;
  font-weight: 700;
  letter-spacing: 0.2em;
  text-transform: uppercase;
}

.showcase-copy h2 {
  margin: 0;
  font-size: clamp(34px, 5vw, 56px);
  line-height: 1.08;
  letter-spacing: -0.04em;
}

.showcase-copy p {
  max-width: 680px;
  margin: 16px 0 0;
  color: var(--muted);
  font-size: 19px;
}

.showcase-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.mini-stat {
  min-width: 132px;
  padding: 14px 18px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  border-radius: 22px;
  background: rgba(255, 252, 246, 0.72);
  box-shadow: 0 18px 40px rgba(68, 43, 13, 0.08);
}

.mini-stat strong,
.service-meta strong,
.dialog-price strong {
  display: block;
  color: var(--accent-deep);
  font-size: 26px;
  line-height: 1;
}

.mini-stat span {
  color: var(--muted);
  font-size: 14px;
}

.filter-dock {
  position: relative;
  z-index: 1;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  margin-bottom: 24px;
  padding: 14px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  border-radius: 24px;
  background: rgba(255, 250, 242, 0.78);
  box-shadow: inset 0 1px 0 rgba(255, 255, 255, 0.72);
}

.filter-dock .el-input,
.filter-dock .el-select {
  width: 280px;
}

.service-card-grid {
  position: relative;
  z-index: 1;
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(270px, 1fr));
  gap: 20px;
}

.service-card {
  overflow: hidden;
  border: 1px solid rgba(122, 86, 37, 0.14);
  border-radius: 30px;
  background: rgba(255, 250, 242, 0.9);
  box-shadow: 0 18px 42px rgba(68, 43, 13, 0.08);
  transition: transform 0.22s ease, box-shadow 0.22s ease, border-color 0.22s ease;
}

.service-card:hover {
  transform: translateY(-6px);
  border-color: rgba(186, 140, 66, 0.36);
  box-shadow: 0 28px 64px rgba(68, 43, 13, 0.16);
}

.service-card.featured {
  background: linear-gradient(180deg, rgba(255, 252, 246, 0.96), rgba(255, 244, 225, 0.92));
}

.service-image-button {
  position: relative;
  width: 100%;
  height: 210px;
  padding: 0;
  border: 0;
  background: transparent;
  cursor: pointer;
  overflow: hidden;
}

.service-card.featured .service-image-button {
  height: 250px;
}

.service-image-button img,
.service-image-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
  display: grid;
  place-items: center;
  transition: transform 0.45s ease;
}

.service-card:hover .service-image-button img,
.service-card:hover .service-image-fallback {
  transform: scale(1.045);
}

.service-image-fallback span {
  color: #fffaf0;
  font-size: 34px;
  font-weight: 700;
  letter-spacing: 0.18em;
}

.image-shade {
  position: absolute;
  inset: 0;
  background:
    linear-gradient(180deg, rgba(42, 33, 23, 0.05), rgba(42, 33, 23, 0.48)),
    radial-gradient(circle at 18% 18%, rgba(255, 255, 255, 0.24), transparent 24%);
}

.image-action,
.category-chip {
  position: absolute;
  z-index: 1;
  border-radius: 999px;
  backdrop-filter: blur(12px);
}

.image-action {
  right: 16px;
  bottom: 14px;
  padding: 9px 15px;
  color: #fff;
  background: rgba(42, 33, 23, 0.7);
}

.category-chip {
  left: 16px;
  top: 16px;
  padding: 8px 13px;
  color: #3f2c17;
  background: rgba(255, 248, 238, 0.82);
  font-weight: 700;
}

.service-card-body {
  display: grid;
  gap: 14px;
  padding: 20px;
}

.service-card-title {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: flex-start;
}

.service-card-title p {
  margin: 0 0 4px;
  color: var(--accent-deep);
  font-size: 14px;
  font-weight: 700;
}

.service-card-title h3 {
  margin: 0;
  font-size: 24px;
  line-height: 1.2;
}

.favorite-button {
  border: 1px solid rgba(186, 140, 66, 0.24);
  border-radius: 999px;
  padding: 8px 13px;
  color: var(--accent-deep);
  background: rgba(255, 248, 238, 0.86);
  cursor: pointer;
}

.service-intro {
  min-height: 62px;
  margin: 0;
  color: var(--muted);
}

.service-meta {
  display: flex;
  align-items: flex-end;
  justify-content: space-between;
  padding-top: 14px;
  border-top: 1px solid rgba(122, 86, 37, 0.1);
  color: var(--muted);
}

.pagination-wrap {
  position: relative;
  z-index: 1;
  margin-top: 24px;
  display: flex;
  justify-content: flex-end;
}

.dialog-service-summary {
  display: grid;
  grid-template-columns: 150px 1fr auto;
  gap: 18px;
  align-items: center;
  margin-bottom: 24px;
  padding: 16px;
  border: 1px solid rgba(186, 140, 66, 0.18);
  border-radius: 26px;
  background:
    linear-gradient(135deg, rgba(255, 249, 239, 0.96), rgba(246, 232, 207, 0.78)),
    radial-gradient(circle at top left, rgba(186, 140, 66, 0.16), transparent 42%);
}

.dialog-service-image {
  width: 150px;
  height: 116px;
  overflow: hidden;
  border-radius: 22px;
  background: rgba(186, 140, 66, 0.14);
}

.dialog-service-image img,
.dialog-service-image .service-image-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.dialog-service-copy span {
  color: var(--accent-deep);
  font-size: 14px;
  font-weight: 700;
}

.dialog-service-copy h3 {
  margin: 6px 0;
  font-size: 26px;
}

.dialog-service-copy p {
  margin: 0;
  color: var(--muted);
}

.dialog-price {
  min-width: 118px;
  display: grid;
  justify-items: end;
  gap: 5px;
}

.dialog-price span,
.recommend-box p {
  color: var(--muted);
}

.booking-stack {
  display: grid;
  gap: 22px;
}

.form-block {
  display: grid;
  gap: 14px;
}

.block-title,
.slot-group-title {
  font-weight: 800;
  color: var(--text);
}

.date-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.date-card,
.slot-card {
  border: 1px solid rgba(122, 86, 37, 0.14);
  background: rgba(255, 255, 255, 0.74);
  border-radius: 22px;
  padding: 16px;
  text-align: left;
  display: grid;
  gap: 6px;
  cursor: pointer;
  transition: transform 0.18s ease, border-color 0.18s ease, box-shadow 0.18s ease;
}

.date-card:hover,
.slot-card:hover,
.date-card.active,
.slot-card.active {
  transform: translateY(-2px);
  border-color: rgba(186, 140, 66, 0.44);
  box-shadow: 0 12px 24px rgba(186, 140, 66, 0.14);
  background: rgba(255, 246, 229, 0.92);
}

.date-card span,
.date-card small,
.slot-card span,
.slot-card small,
.summary-row {
  color: var(--muted);
}

.slot-wrapper {
  display: grid;
  gap: 18px;
}

.slot-group {
  display: grid;
  gap: 12px;
}

.slot-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 12px;
}

.summary-box,
.recommend-box {
  margin-top: 18px;
  padding: 18px;
  border-radius: 22px;
  background: rgba(255, 248, 238, 0.94);
  border: 1px solid rgba(186, 140, 66, 0.2);
}

.summary-box {
  display: grid;
  gap: 8px;
}

.dialog-footer {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

@media (max-width: 980px) {
  .showcase-head,
  .showcase-actions {
    flex-direction: column;
  }

  .filter-dock .el-input,
  .filter-dock .el-select {
    width: 100%;
  }

  .dialog-service-summary {
    grid-template-columns: 1fr;
  }

  .dialog-service-image {
    width: 100%;
    height: 180px;
  }

  .dialog-price {
    justify-items: start;
  }
}

@media (max-width: 760px) {
  .service-showcase {
    padding: 22px;
  }

  .date-grid,
  .slot-grid {
    grid-template-columns: 1fr;
  }

  .service-card-title {
    flex-direction: column;
  }
}
</style>
