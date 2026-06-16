<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue';
import { ElMessage, ElMessageBox } from 'element-plus';
import TechnicianSelect from '../../components/TechnicianSelect.vue';
import { api } from '../../api/modules';
import type { AdminUser, AutoSchedulePreview, LeaveRecord, Schedule, Technician } from '../../api/types';
import { buildTechnicianOptions, selectableTechnicianOptions } from '../../composables/useTechnicianOptions';

type ScheduleMode = 'MANUAL' | 'AUTO';

type ManualScheduleForm = {
  selectedTechnicianIds: number[];
  dateRange: string[];
  weekdays: number[];
  startTime: string;
  endTime: string;
  status: string;
};

type AutoScheduleForm = {
  candidateTechnicianIds: number[];
  dateRange: string[];
  weekdays: number[];
  startTime: string;
  endTime: string;
  dailyTechnicianCount: number;
  overwriteExisting: boolean;
};

type EditScheduleForm = {
  id?: number;
  technicianId: number | null;
  workDate: string;
  startTime: string;
  endTime: string;
  status: string;
};

type BatchPreviewItem = {
  technicianId: number;
  technicianName: string;
  workDate: string;
  startTime: string;
  endTime: string;
  status: string;
  existingId?: number;
  hasLeaveConflict: boolean;
};

type WeeklyBoardItem = {
  key: string;
  id?: number;
  technicianId: number;
  technicianName: string;
  workDate: string;
  startTime: string;
  endTime: string;
  status: string;
  source: 'saved' | 'draft';
  willOverwrite: boolean;
  hasLeaveConflict: boolean;
  canCancel: boolean;
};

const loading = ref(false);
const submitting = ref(false);
const previewing = ref(false);
const weekOffset = ref(0);
const overviewDialogVisible = ref(false);
const scheduleDialogVisible = ref(false);

const schedules = ref<Schedule[]>([]);
const technicians = ref<Technician[]>([]);
const users = ref<AdminUser[]>([]);
const leaveRecords = ref<LeaveRecord[]>([]);
const autoPreviewResult = ref<AutoSchedulePreview | null>(null);
const scheduleMode = ref<ScheduleMode>('MANUAL');

const filters = reactive({
  technicianId: 0,
  keyword: '',
  dateRange: [] as string[],
});
const listPager = reactive({
  pageNum: 1,
  pageSize: 10,
});

const manualForm = reactive<ManualScheduleForm>({
  selectedTechnicianIds: [],
  dateRange: [] as string[],
  weekdays: [1, 2, 3, 4, 5] as number[],
  startTime: '09:00',
  endTime: '18:00',
  status: 'WORKING',
});

const autoForm = reactive<AutoScheduleForm>({
  candidateTechnicianIds: [],
  dateRange: [] as string[],
  weekdays: [1, 2, 3, 4, 5] as number[],
  startTime: '09:00',
  endTime: '18:00',
  dailyTechnicianCount: 2,
  overwriteExisting: false,
});

const editForm = reactive<EditScheduleForm>({
  id: undefined,
  technicianId: null,
  workDate: '',
  startTime: '09:00',
  endTime: '18:00',
  status: 'WORKING',
});

const weekdayOptions = [
  { label: '周一', value: 1 },
  { label: '周二', value: 2 },
  { label: '周三', value: 3 },
  { label: '周四', value: 4 },
  { label: '周五', value: 5 },
  { label: '周六', value: 6 },
  { label: '周日', value: 0 },
];

const technicianMap = computed(() =>
  technicians.value.reduce<Record<number, Technician>>((acc, item) => {
    acc[item.id] = item;
    return acc;
  }, {}),
);

const technicianOptions = computed(() => buildTechnicianOptions(technicians.value, users.value));
const availableTechnicianOptions = computed(() => selectableTechnicianOptions(technicianOptions.value));
const approvedLeaves = computed(() => leaveRecords.value.filter((item) => item.status === 'APPROVED'));
const isEditMode = computed(() => Boolean(editForm.id));
const todayText = computed(() => formatDate(new Date()));
const scheduleDialogTitle = computed(() => {
  if (isEditMode.value) return '编辑排班';
  return scheduleMode.value === 'MANUAL' ? '手动排班' : '自动排班';
});
const formSingleDate = computed({
  get: () => editForm.workDate,
  set: (value: string) => {
    editForm.workDate = value || '';
  },
});
const activeDateRange = computed({
  get: () => {
    if (isEditMode.value) {
      return editForm.workDate ? [editForm.workDate, editForm.workDate] : [];
    }
    return scheduleMode.value === 'MANUAL' ? manualForm.dateRange : autoForm.dateRange;
  },
  set: (value: string[]) => {
    if (isEditMode.value) {
      editForm.workDate = value[0] || '';
      return;
    }
    if (scheduleMode.value === 'MANUAL') {
      manualForm.dateRange = value;
    } else {
      autoForm.dateRange = value;
    }
  },
});
const activeWeekdays = computed({
  get: () => (scheduleMode.value === 'MANUAL' ? manualForm.weekdays : autoForm.weekdays),
  set: (value: number[]) => {
    if (scheduleMode.value === 'MANUAL') {
      manualForm.weekdays = value;
    } else {
      autoForm.weekdays = value;
    }
  },
});
const activeStartTime = computed({
  get: () => (isEditMode.value ? editForm.startTime : scheduleMode.value === 'MANUAL' ? manualForm.startTime : autoForm.startTime),
  set: (value: string) => {
    if (isEditMode.value) {
      editForm.startTime = value;
    } else if (scheduleMode.value === 'MANUAL') {
      manualForm.startTime = value;
    } else {
      autoForm.startTime = value;
    }
  },
});
const activeEndTime = computed({
  get: () => (isEditMode.value ? editForm.endTime : scheduleMode.value === 'MANUAL' ? manualForm.endTime : autoForm.endTime),
  set: (value: string) => {
    if (isEditMode.value) {
      editForm.endTime = value;
    } else if (scheduleMode.value === 'MANUAL') {
      manualForm.endTime = value;
    } else {
      autoForm.endTime = value;
    }
  },
});
const activeStatus = computed({
  get: () => (isEditMode.value ? editForm.status : manualForm.status),
  set: (value: string) => {
    if (isEditMode.value) {
      editForm.status = value;
    } else {
      manualForm.status = value;
    }
  },
});
const autoDailyTechnicianMax = computed(() => {
  const candidateCount = autoForm.candidateTechnicianIds.length || availableTechnicianOptions.value.length;
  return Math.max(1, candidateCount);
});
const formDateSummary = computed(() => {
  if (activeDateRange.value.length !== 2) {
    return isEditMode.value ? '请选择需要调整的排班日期' : '先选择日期范围，再按工作日筛选要生成的排班';
  }
  if (isEditMode.value) {
    return `正在编辑 ${editForm.workDate} 的单条排班`;
  }
  const totalDays = buildDateRange(activeDateRange.value[0], activeDateRange.value[1]).length;
  return `已选择 ${activeDateRange.value[0]} 至 ${activeDateRange.value[1]}，共 ${totalDays} 天`;
});
const formQuickRanges = computed(() => {
  const thisWeekStart = startOfWeek(new Date());
  const nextWeekStart = addDays(thisWeekStart, 7);
  return [
    { label: '今天', range: [todayText.value, todayText.value] as [string, string] },
    { label: '明天', range: createOffsetRange(1, 1) },
    { label: '本周', range: formatRange(thisWeekStart, addDays(thisWeekStart, 6)) },
    { label: '下周', range: formatRange(nextWeekStart, addDays(nextWeekStart, 6)) },
    { label: '未来 7 天', range: createOffsetRange(0, 6) },
  ];
});
const filterQuickRanges = computed(() => {
  const thisWeekStart = startOfWeek(new Date());
  return [
    { label: '今天', range: [todayText.value, todayText.value] as [string, string] },
    { label: '本周', range: formatRange(thisWeekStart, addDays(thisWeekStart, 6)) },
    { label: '未来 30 天', range: createOffsetRange(0, 29) },
  ];
});

const weeklyDates = computed(() => {
  const start = startOfWeek(addDays(new Date(), weekOffset.value * 7));
  return Array.from({ length: 7 }, (_, index) => {
    const date = addDays(start, index);
    const dateText = formatDate(date);
    return {
      date: dateText,
      label: formatWeekday(date),
      dayNumber: `${date.getMonth() + 1}/${date.getDate()}`,
      isToday: dateText === todayText.value,
    };
  });
});

const weekLabel = computed(() => {
  const first = weeklyDates.value[0];
  const last = weeklyDates.value[6];
  return first && last ? `${first.date} 至 ${last.date}` : '';
});

const scheduleStats = computed(() => {
  const weekStart = weeklyDates.value[0]?.date ?? todayText.value;
  const weekEnd = weeklyDates.value[6]?.date ?? todayText.value;
  return {
    technicianCount: technicians.value.length,
    todayCount: schedules.value.filter((item) => item.workDate === todayText.value).length,
    weeklyCount: schedules.value.filter((item) => item.workDate >= weekStart && item.workDate <= weekEnd).length,
    approvedLeaves: approvedLeaves.value.length,
  };
});

const batchPreview = computed<BatchPreviewItem[]>(() => {
  if (isEditMode.value) {
    return [];
  }
  if (scheduleMode.value === 'AUTO') {
    if (!autoPreviewResult.value) return [];
    return autoPreviewResult.value.days.flatMap((day) =>
      day.items.map((item) => ({
        technicianId: item.technicianId,
        technicianName: item.technicianName,
        workDate: item.workDate,
        startTime: item.startTime,
        endTime: item.endTime,
        status: item.status,
        existingId: item.existingId || undefined,
        hasLeaveConflict: item.hasLeaveConflict || item.hasExistingScheduleConflict,
      })),
    );
  }
  if (!manualForm.selectedTechnicianIds.length || manualForm.dateRange.length !== 2) {
    return [];
  }

  const [startDate, endDate] = manualForm.dateRange;
  return buildDateRange(startDate, endDate)
    .filter((date) => manualForm.weekdays.includes(weekdayValue(date)))
    .flatMap((date) =>
      manualForm.selectedTechnicianIds.map((technicianId) => {
        const sameDaySchedules = schedules.value.filter(
          (item) =>
            item.technicianId === technicianId &&
            item.workDate === date &&
            rangesOverlap(item.startTime, item.endTime, manualForm.startTime, manualForm.endTime),
        );
        const targetSchedule = sameDaySchedules[0];
        return {
          technicianId,
          technicianName: technicianMap.value[technicianId]?.name || `#${technicianId}`,
          workDate: date,
          startTime: manualForm.startTime,
          endTime: manualForm.endTime,
          status: manualForm.status,
          existingId: targetSchedule?.id,
          hasLeaveConflict: hasApprovedLeaveConflict(technicianId, date),
        };
      }),
    );
});

const filteredSchedules = computed(() =>
  schedules.value.filter((item) => {
    const matchTechnician = !filters.technicianId || item.technicianId === filters.technicianId;
    const matchKeyword = !filters.keyword.trim() || technicianName(item.technicianId).includes(filters.keyword.trim());
    const matchDate =
      filters.dateRange.length !== 2 ||
      (item.workDate >= filters.dateRange[0] && item.workDate <= filters.dateRange[1]);
    return matchTechnician && matchKeyword && matchDate;
  }),
);
const pagedFilteredSchedules = computed(() => {
  const start = (listPager.pageNum - 1) * listPager.pageSize;
  return filteredSchedules.value.slice(start, start + listPager.pageSize);
});

const weeklyBoard = computed(() =>
  weeklyDates.value.map((day) => {
    const savedItems: WeeklyBoardItem[] = schedules.value
      .filter((item) => item.workDate === day.date)
      .map((item) => ({
        key: `saved-${item.id}`,
        id: item.id,
        technicianId: item.technicianId,
        technicianName: technicianName(item.technicianId),
        workDate: item.workDate,
        startTime: item.startTime,
        endTime: item.endTime,
        status: item.status,
        source: 'saved',
        willOverwrite: false,
        hasLeaveConflict: hasApprovedLeaveConflict(item.technicianId, item.workDate),
        canCancel: !isPastDate(item.workDate),
      }));

    const draftItems: WeeklyBoardItem[] = batchPreview.value
      .filter((item) => item.workDate === day.date)
      .map((item, index) => ({
        key: `draft-${day.date}-${item.technicianId}-${index}`,
        technicianId: item.technicianId,
        technicianName: item.technicianName,
        workDate: item.workDate,
        startTime: item.startTime,
        endTime: item.endTime,
        status: item.status,
        source: 'draft',
        willOverwrite: Boolean(item.existingId),
        hasLeaveConflict: item.hasLeaveConflict,
        canCancel: false,
      }));

    return {
      ...day,
      leaveCount: approvedLeaves.value.filter(
        (leave) => leave.startTime.slice(0, 10) <= day.date && leave.endTime.slice(0, 10) >= day.date,
      ).length,
      items: [...savedItems, ...draftItems].sort((a, b) => {
        if (a.technicianName !== b.technicianName) {
          return a.technicianName.localeCompare(b.technicianName, 'zh-CN');
        }
        return a.startTime.localeCompare(b.startTime);
      }),
    };
  }),
);

const currentWeekDraftSummary = computed(() => {
  const currentDates = new Set(weeklyDates.value.map((item) => item.date));
  const items = batchPreview.value.filter((item) => currentDates.has(item.workDate));
  return {
    total: items.length,
    conflicts: items.filter((item) => item.hasLeaveConflict).length,
    overwrite: items.filter((item) => item.existingId).length,
    shortage:
      autoPreviewResult.value?.days
        .filter((day) => currentDates.has(day.workDate))
        .reduce((sum, day) => sum + day.shortageCount, 0) ?? 0,
  };
});

const autoSchedulePayload = computed(() => {
  if (autoForm.dateRange.length !== 2) return null;
  return {
    technicianIds: autoForm.candidateTechnicianIds,
    startDate: autoForm.dateRange[0],
    endDate: autoForm.dateRange[1],
    weekdays: autoForm.weekdays,
    startTime: normalizeTimeForApi(autoForm.startTime),
    endTime: normalizeTimeForApi(autoForm.endTime),
    dailyTechnicianCount: autoForm.dailyTechnicianCount,
    overwriteExisting: autoForm.overwriteExisting,
  };
});

const scheduleSubmitSummary = computed(() => {
  if (isEditMode.value) {
    const technician = editForm.technicianId ? technicianName(editForm.technicianId) : '未选择';
    return `编辑单条：${technician}，${editForm.workDate || '未选日期'}，${editForm.startTime} - ${editForm.endTime}`;
  }
  if (scheduleMode.value === 'MANUAL') {
    const names = manualForm.selectedTechnicianIds.map(technicianName).join('、') || '未选择';
    const range = manualForm.dateRange.length === 2 ? `${manualForm.dateRange[0]} 至 ${manualForm.dateRange[1]}` : '未选日期';
    return `手动排班：${names}，${range}，${manualForm.startTime} - ${manualForm.endTime}`;
  }
  const candidateNames = autoForm.candidateTechnicianIds.map(technicianName).join('、') || '全部可用在职技师';
  const range = autoForm.dateRange.length === 2 ? `${autoForm.dateRange[0]} 至 ${autoForm.dateRange[1]}` : '未选日期';
  const overwriteText = autoForm.overwriteExisting ? '允许覆盖无预约排班' : '跳过已有排班';
  return `自动排班：候选池 ${candidateNames}，${range}，每天 ${autoForm.dailyTechnicianCount} 人，${overwriteText}`;
});

async function loadData() {
  loading.value = true;
  try {
    const [scheduleRes, technicianRes, leaveRes, userRes] = await Promise.all([
      api.schedules(),
      api.technicians(),
      api.leaveRecords(),
      api.users(),
    ]);
    schedules.value = scheduleRes.data.data;
    technicians.value = technicianRes.data.data;
    leaveRecords.value = leaveRes.data.data;
    users.value = userRes.data.data;
  } finally {
    loading.value = false;
  }
}

function technicianName(technicianId: number) {
  return technicianMap.value[technicianId]?.name || `#${technicianId}`;
}

function hasApprovedLeaveConflict(technicianId: number, date: string) {
  return approvedLeaves.value.some(
    (leave) =>
      leave.technicianId === technicianId &&
      leave.startTime.slice(0, 10) <= date &&
      leave.endTime.slice(0, 10) >= date,
  );
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const month = `${date.getMonth() + 1}`.padStart(2, '0');
  const day = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${month}-${day}`;
}

function formatWeekday(date: Date) {
  return ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()];
}

function isPastDate(dateText: string) {
  return dateText < todayText.value;
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function startOfWeek(date: Date) {
  const current = new Date(date);
  const weekday = current.getDay();
  const diff = weekday === 0 ? -6 : 1 - weekday;
  current.setDate(current.getDate() + diff);
  current.setHours(0, 0, 0, 0);
  return current;
}

function buildDateRange(start: string, end: string) {
  const result: string[] = [];
  const current = new Date(`${start}T00:00:00`);
  const deadline = new Date(`${end}T00:00:00`);
  while (current <= deadline) {
    result.push(formatDate(current));
    current.setDate(current.getDate() + 1);
  }
  return result;
}

function weekdayValue(dateText: string) {
  return new Date(`${dateText}T00:00:00`).getDay();
}

function chooseWeekPreset(type: 'workday' | 'weekend' | 'full') {
  if (type === 'workday') {
    activeWeekdays.value = [1, 2, 3, 4, 5];
    return;
  }
  if (type === 'weekend') {
    activeWeekdays.value = [6, 0];
    return;
  }
  activeWeekdays.value = [1, 2, 3, 4, 5, 6, 0];
}

function normalizeTimeForPicker(value: string) {
  return value?.slice(0, 5) || '';
}

function normalizeTimeForApi(value: string) {
  if (!value) {
    return '';
  }
  return value.length === 5 ? `${value}:00` : value;
}

function comparableTime(value: string) {
  return value.slice(0, 5);
}

function rangesOverlap(startA: string, endA: string, startB: string, endB: string) {
  return comparableTime(startA) < comparableTime(endB) && comparableTime(endA) > comparableTime(startB);
}

function formatRange(start: Date, end: Date) {
  return [formatDate(start), formatDate(end)] as [string, string];
}

function createOffsetRange(startOffset: number, endOffset: number) {
  return formatRange(addDays(new Date(), startOffset), addDays(new Date(), endOffset));
}

function applyFormQuickRange(range: [string, string]) {
  activeDateRange.value = [...range];
  syncWeekToDate(range[0]);
}

function applyFilterQuickRange(range: [string, string]) {
  filters.dateRange = [...range];
}

function clearFilterDateRange() {
  filters.dateRange = [];
}

function disableScheduleDate(date: Date) {
  return !isEditMode.value && formatDate(date) < todayText.value;
}

function syncWeekToDate(dateText?: string) {
  if (!dateText) {
    return;
  }

  const targetStart = startOfWeek(new Date(`${dateText}T00:00:00`));
  const currentStart = startOfWeek(new Date());
  const diffDays = Math.round((targetStart.getTime() - currentStart.getTime()) / (1000 * 60 * 60 * 24));
  weekOffset.value = Math.round(diffDays / 7);
}

function focusPreviewRange() {
  if (!activeDateRange.value.length) {
    ElMessage.warning('请先选择日期');
    return;
  }
  syncWeekToDate(activeDateRange.value[0]);
}

function resetEditForm() {
  Object.assign(editForm, {
    id: undefined,
    technicianId: null,
    workDate: '',
    startTime: '09:00',
    endTime: '18:00',
    status: 'WORKING',
  });
}

function resetForm() {
  resetEditForm();
  Object.assign(manualForm, {
    selectedTechnicianIds: [],
    dateRange: [],
    weekdays: [1, 2, 3, 4, 5],
    startTime: '09:00',
    endTime: '18:00',
    status: 'WORKING',
  });
  Object.assign(autoForm, {
    candidateTechnicianIds: [],
    dateRange: [],
    weekdays: [1, 2, 3, 4, 5],
    startTime: '09:00',
    endTime: '18:00',
    dailyTechnicianCount: Math.min(2, autoDailyTechnicianMax.value),
    overwriteExisting: false,
  });
  scheduleMode.value = 'MANUAL';
  autoPreviewResult.value = null;
}

function openOverviewDialog() {
  overviewDialogVisible.value = true;
}

function openManualScheduleDialog() {
  resetEditForm();
  scheduleMode.value = 'MANUAL';
  autoPreviewResult.value = null;
  scheduleDialogVisible.value = true;
}

function openAutoScheduleDialog() {
  resetEditForm();
  scheduleMode.value = 'AUTO';
  scheduleDialogVisible.value = true;
}

function editSchedule(row: Schedule) {
  Object.assign(editForm, {
    id: row.id,
    technicianId: row.technicianId,
    workDate: row.workDate,
    startTime: normalizeTimeForPicker(row.startTime),
    endTime: normalizeTimeForPicker(row.endTime),
    status: row.status,
  });
  scheduleMode.value = 'MANUAL';
  autoPreviewResult.value = null;
  overviewDialogVisible.value = false;
  scheduleDialogVisible.value = true;
  syncWeekToDate(row.workDate);
}

async function refreshAutoPreview() {
  if (isEditMode.value) {
    ElMessage.warning('编辑单条排班时不需要自动预览');
    return;
  }
  if (scheduleMode.value !== 'AUTO') {
    ElMessage.warning('手动排班会按已选技师直接生成草稿，不需要自动预览');
    return;
  }
  if (!autoSchedulePayload.value) {
    ElMessage.warning('请先选择完整的日期范围');
    return;
  }
  previewing.value = true;
  try {
    const { data } = await api.autoSchedulePreview(autoSchedulePayload.value);
    autoPreviewResult.value = data.data;
    ElMessage.success(`自动排班预览完成：计划 ${data.data.totalPlanned} 人次，实际可排 ${data.data.totalGenerated} 人次`);
  } finally {
    previewing.value = false;
  }
}

function switchWeek(step: number) {
  weekOffset.value += step;
}

function goToCurrentWeek() {
  weekOffset.value = 0;
}

async function cancelSchedule(id?: number) {
  if (!id) {
    return;
  }

  try {
    await ElMessageBox.confirm(
      '确认取消这条排班吗？取消后该技师当天的这一班次会被移除。',
      '取消排班',
      {
        type: 'warning',
        confirmButtonText: '确认取消',
        cancelButtonText: '先保留',
      },
    );
    await api.deleteSchedule(id);
    ElMessage.success('排班已取消');
    if (editForm.id === id) {
      resetEditForm();
    }
    await loadData();
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      throw error;
    }
  }
}

async function submitSchedules() {
  if (!activeStartTime.value || !activeEndTime.value) {
    ElMessage.warning('请填写完整的上下班时间');
    return;
  }
  if (activeStartTime.value >= activeEndTime.value) {
    ElMessage.warning('下班时间必须晚于上班时间');
    return;
  }

  submitting.value = true;
  try {
    if (isEditMode.value) {
      if (!editForm.technicianId) {
        ElMessage.warning('请先选择技师');
        return;
      }
      if (!editForm.workDate) {
        ElMessage.warning('请先选择排班日期');
        return;
      }
      await api.saveSchedule({
        id: editForm.id,
        technicianId: editForm.technicianId,
        workDate: editForm.workDate,
        startTime: normalizeTimeForApi(editForm.startTime),
        endTime: normalizeTimeForApi(editForm.endTime),
        status: editForm.status,
      });
      ElMessage.success('排班已更新');
      scheduleDialogVisible.value = false;
      resetForm();
      await loadData();
      return;
    }

    if (scheduleMode.value === 'MANUAL') {
      if (!manualForm.selectedTechnicianIds.length) {
        ElMessage.warning('请先选择技师');
        return;
      }
      if (manualForm.dateRange.length !== 2) {
        ElMessage.warning('请先选择排班日期范围');
        return;
      }
      if (!batchPreview.value.length) {
        ElMessage.warning('当前条件下没有可生成的排班计划');
        return;
      }

      for (const item of batchPreview.value) {
        await api.saveSchedule({
          id: item.existingId,
          technicianId: item.technicianId,
          workDate: item.workDate,
          startTime: normalizeTimeForApi(item.startTime),
          endTime: normalizeTimeForApi(item.endTime),
          status: item.status,
        });
      }
      ElMessage.success(`手动排班完成：写入 ${batchPreview.value.length} 人次`);
      scheduleDialogVisible.value = false;
      resetForm();
      await loadData();
      return;
    }

    if (!autoSchedulePayload.value) {
      ElMessage.warning('请先选择完整的日期范围');
      return;
    }
    if (!autoPreviewResult.value) {
      await refreshAutoPreview();
    }
    const { data } = await api.autoScheduleApply(autoSchedulePayload.value);
    ElMessage.success(`自动排班完成：写入 ${data.data.totalGenerated} 人次，缺口 ${data.data.totalShortage} 人次`);

    scheduleDialogVisible.value = false;
    resetForm();
    await loadData();
  } finally {
    submitting.value = false;
  }
}

function itemTagType(item: WeeklyBoardItem) {
  if (item.source === 'saved' && !item.canCancel) return 'info';
  if (item.hasLeaveConflict) return 'danger';
  if (item.source === 'draft') return 'warning';
  return item.status === 'WORKING' ? 'success' : 'info';
}

function itemStatusText(item: WeeklyBoardItem) {
  if (item.source === 'saved' && !item.canCancel) return '日期已过';
  if (item.hasLeaveConflict) return '请假冲突';
  if (item.source === 'draft') return item.willOverwrite ? '待提交（将覆盖）' : '待提交';
  return item.status === 'WORKING' ? '已排班' : item.status;
}

function tableStatusType(row: Schedule) {
  if (isPastDate(row.workDate)) {
    return 'info';
  }
  return row.status === 'WORKING' ? 'success' : 'info';
}

function tableStatusText(row: Schedule) {
  if (isPastDate(row.workDate)) {
    return '日期已过';
  }
  return row.status === 'WORKING' ? '正常上班' : row.status;
}

function handleListPageChange(pageNum: number) {
  listPager.pageNum = pageNum;
}

function handleListSizeChange(pageSize: number) {
  listPager.pageSize = pageSize;
  listPager.pageNum = 1;
}

watch(
  () => [filteredSchedules.value.length, filters.technicianId, filters.keyword, filters.dateRange.join('|')],
  () => {
    const maxPage = Math.max(1, Math.ceil(filteredSchedules.value.length / listPager.pageSize));
    if (listPager.pageNum > maxPage) {
      listPager.pageNum = maxPage;
    }
  },
);

watch(
  () => [
    scheduleMode.value,
    autoForm.candidateTechnicianIds.join(','),
    autoForm.dateRange.join('|'),
    autoForm.weekdays.join(','),
    autoForm.startTime,
    autoForm.endTime,
    autoForm.dailyTechnicianCount,
    autoForm.overwriteExisting,
  ],
  () => {
    autoPreviewResult.value = null;
  },
);

watch(
  () => activeDateRange.value[0],
  (value) => {
    if (value) {
      syncWeekToDate(value);
    }
  },
);

watch(autoDailyTechnicianMax, (max) => {
  if (autoForm.dailyTechnicianCount > max) {
    autoForm.dailyTechnicianCount = max;
  }
});

onMounted(loadData);
</script>

<template>
  <div class="page-shell" v-loading="loading">
    <section class="metric-grid">
      <article class="metric-tile">
        <div class="metric-label">技师总数</div>
        <div class="metric-value">{{ scheduleStats.technicianCount }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">今日排班</div>
        <div class="metric-value">{{ scheduleStats.todayCount }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">本周排班</div>
        <div class="metric-value">{{ scheduleStats.weeklyCount }}</div>
      </article>
      <article class="metric-tile">
        <div class="metric-label">已批准请假</div>
        <div class="metric-value">{{ scheduleStats.approvedLeaves }}</div>
      </article>
    </section>

    <section class="panel schedule-panel">
      <div class="panel-header schedule-home-header">
        <div>
          <h2 class="panel-title">周排班预览</h2>
          <p class="panel-subtitle">以周视图查看门店排班。排班总览、手动排班和自动排班通过弹窗处理，首页保持聚焦。</p>
        </div>
        <div class="toolbar">
          <el-button plain @click="openOverviewDialog">排班总览</el-button>
          <el-button type="primary" plain @click="openManualScheduleDialog">手动排班</el-button>
          <el-button type="warning" @click="openAutoScheduleDialog">自动排班</el-button>
        </div>
      </div>

      <article class="preview-board">
        <div class="board-header">
          <div class="section-heading">
            <h3>{{ weekLabel }}</h3>
            <p>黄色为待提交草稿，灰色表示日期已过；可切换周次查看排班覆盖情况。</p>
          </div>
          <div class="toolbar">
            <el-button plain @click="switchWeek(-1)">上一周</el-button>
            <el-button plain @click="goToCurrentWeek">本周</el-button>
            <el-button type="warning" plain @click="switchWeek(1)">下一周</el-button>
          </div>
        </div>

        <div class="draft-summary" v-if="currentWeekDraftSummary.total || currentWeekDraftSummary.shortage">
          <div class="draft-chip">
            <span>本周待提交</span>
            <strong>{{ currentWeekDraftSummary.total }}</strong>
          </div>
          <div class="draft-chip danger">
            <span>请假冲突</span>
            <strong>{{ currentWeekDraftSummary.conflicts }}</strong>
          </div>
          <div class="draft-chip">
            <span>将覆盖旧排班</span>
            <strong>{{ currentWeekDraftSummary.overwrite }}</strong>
          </div>
          <div class="draft-chip danger">
            <span>人数缺口</span>
            <strong>{{ currentWeekDraftSummary.shortage }}</strong>
          </div>
        </div>

        <div class="week-grid">
          <article
            v-for="day in weeklyBoard"
            :key="day.date"
            class="day-card"
            :class="{ today: day.isToday }"
          >
            <div class="day-header">
              <div>
                <strong>{{ day.label }}</strong>
                <p>{{ day.dayNumber }}</p>
              </div>
              <el-tag v-if="day.leaveCount" type="danger" effect="plain">请假 {{ day.leaveCount }}</el-tag>
            </div>

            <el-empty v-if="day.items.length === 0" description="暂无排班" :image-size="48" />

            <div v-else class="day-items">
              <div
                v-for="item in day.items"
                :key="item.key"
                class="shift-item"
                :class="{ draft: item.source === 'draft', conflict: item.hasLeaveConflict, expired: item.source === 'saved' && !item.canCancel }"
              >
                <div class="shift-top">
                  <strong>{{ item.technicianName }}</strong>
                  <el-tag size="small" :type="itemTagType(item)" effect="plain">{{ itemStatusText(item) }}</el-tag>
                </div>
                <p>{{ item.startTime }} - {{ item.endTime }}</p>
                <div v-if="item.source === 'saved'" class="shift-actions">
                  <el-button
                    v-if="item.canCancel"
                    size="small"
                    type="danger"
                    plain
                    @click="cancelSchedule(item.id)"
                  >
                    取消
                  </el-button>
                  <el-tag v-else size="small" type="info" effect="plain">已过期</el-tag>
                </div>
              </div>
            </div>
          </article>
        </div>
      </article>
    </section>

    <el-dialog v-model="overviewDialogVisible" title="排班总览" width="92vw" class="schedule-dialog" append-to-body>
      <div class="overview-dialog-body">
        <div class="filter-bar">
          <el-select v-model="filters.technicianId" clearable placeholder="筛选技师">
            <el-option v-for="item in technicians" :key="item.id" :label="item.name" :value="item.id" />
          </el-select>
          <div class="filter-date-box">
            <div class="toolbar quick-actions">
              <el-button
                v-for="item in filterQuickRanges"
                :key="item.label"
                size="small"
                plain
                @click="applyFilterQuickRange(item.range)"
              >
                {{ item.label }}
              </el-button>
              <el-button size="small" plain @click="clearFilterDateRange">清空日期</el-button>
            </div>
            <el-date-picker
              v-model="filters.dateRange"
              class="full-width"
              type="daterange"
              start-placeholder="开始日期"
              end-placeholder="结束日期"
              value-format="YYYY-MM-DD"
              unlink-panels
            />
          </div>
          <el-input v-model="filters.keyword" placeholder="按技师姓名搜索" clearable />
        </div>

        <el-table :data="pagedFilteredSchedules" stripe>
          <el-table-column label="技师" min-width="120">
            <template #default="{ row }">{{ technicianName(row.technicianId) }}</template>
          </el-table-column>
          <el-table-column prop="workDate" label="工作日期" width="130" />
          <el-table-column label="班次" min-width="180">
            <template #default="{ row }">{{ row.startTime }} - {{ row.endTime }}</template>
          </el-table-column>
          <el-table-column label="状态" width="130">
            <template #default="{ row }">
              <el-tag :type="tableStatusType(row)">
                {{ tableStatusText(row) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="240">
            <template #default="{ row }">
              <div class="toolbar">
                <el-button size="small" plain @click="editSchedule(row)">编辑</el-button>
                <el-button
                  v-if="!isPastDate(row.workDate)"
                  size="small"
                  type="danger"
                  plain
                  @click="cancelSchedule(row.id)"
                >
                  取消排班
                </el-button>
                <el-tag v-else size="small" type="info" effect="plain">日期已过</el-tag>
              </div>
            </template>
          </el-table-column>
        </el-table>

        <div class="pagination-wrap">
          <el-pagination
            background
            layout="total, sizes, prev, pager, next"
            :current-page="listPager.pageNum"
            :page-size="listPager.pageSize"
            :page-sizes="[10, 20, 50]"
            :total="filteredSchedules.length"
            @current-change="handleListPageChange"
            @size-change="handleListSizeChange"
          />
        </div>
      </div>
    </el-dialog>

    <el-dialog v-model="scheduleDialogVisible" :title="scheduleDialogTitle" width="92vw" class="schedule-dialog" append-to-body>
      <div class="schedule-dialog-body">
        <div class="dialog-mode-bar" v-if="!isEditMode">
          <el-radio-group v-model="scheduleMode" class="mode-switch">
            <el-radio-button label="MANUAL">手动排班</el-radio-button>
            <el-radio-button label="AUTO">自动排班</el-radio-button>
          </el-radio-group>
          <el-button plain @click="resetForm">重置表单</el-button>
        </div>

        <el-form label-position="top" class="form-grid">
          <el-form-item :label="isEditMode ? '排班技师' : scheduleMode === 'MANUAL' ? '手动选择技师' : '自动候选池'">
            <TechnicianSelect
              v-if="isEditMode"
              v-model="editForm.technicianId"
              class="full-width"
              :technicians="technicians"
              :users="users"
              placeholder="请选择排班技师"
            />
            <TechnicianSelect
              v-else-if="scheduleMode === 'MANUAL'"
              v-model="manualForm.selectedTechnicianIds"
              class="full-width"
              multiple
              :technicians="technicians"
              :users="users"
              placeholder="请选择要排班的技师"
            />
            <TechnicianSelect
              v-else
              v-model="autoForm.candidateTechnicianIds"
              class="full-width"
              multiple
              :technicians="technicians"
              :users="users"
              placeholder="候选池为空时使用全部可用在职技师"
            />
          </el-form-item>

          <el-form-item :label="isEditMode ? '排班日期' : '日期范围'" class="form-span-2">
            <div class="date-field">
              <div class="toolbar quick-actions">
                <el-button
                  v-for="item in formQuickRanges"
                  :key="item.label"
                  size="small"
                  plain
                  @click="applyFormQuickRange(item.range)"
                >
                  {{ item.label }}
                </el-button>
                <el-button size="small" plain @click="focusPreviewRange">定位到预览周</el-button>
              </div>

              <el-date-picker
                v-if="isEditMode"
                v-model="formSingleDate"
                class="full-width"
                type="date"
                placeholder="选择排班日期"
                value-format="YYYY-MM-DD"
              />
              <el-date-picker
                v-else
                v-model="activeDateRange"
                class="full-width"
                type="daterange"
                start-placeholder="开始日期"
                end-placeholder="结束日期"
                value-format="YYYY-MM-DD"
                unlink-panels
                :disabled-date="disableScheduleDate"
              />

              <div class="date-hint">
                <strong>{{ formDateSummary }}</strong>
                <span v-if="!isEditMode">批量排班不会允许选择今天之前的日期，减少误操作。</span>
              </div>
            </div>
          </el-form-item>

          <el-form-item v-if="!isEditMode" label="工作日筛选">
            <div class="weekday-box">
              <div class="toolbar">
                <el-button size="small" plain @click="chooseWeekPreset('workday')">工作日</el-button>
                <el-button size="small" plain @click="chooseWeekPreset('weekend')">周末</el-button>
                <el-button size="small" plain @click="chooseWeekPreset('full')">全周</el-button>
              </div>
              <el-checkbox-group v-model="activeWeekdays">
                <el-checkbox v-for="item in weekdayOptions" :key="item.value" :value="item.value">
                  {{ item.label }}
                </el-checkbox>
              </el-checkbox-group>
            </div>
          </el-form-item>

          <el-form-item label="上班时间">
            <el-time-select
              v-model="activeStartTime"
              class="full-width"
              start="06:00"
              step="00:30"
              end="23:00"
              placeholder="选择上班时间"
            />
          </el-form-item>

          <el-form-item label="下班时间">
            <el-time-select
              v-model="activeEndTime"
              class="full-width"
              start="06:30"
              step="00:30"
              end="23:30"
              :min-time="activeStartTime"
              placeholder="选择下班时间"
            />
          </el-form-item>

          <el-form-item v-if="!isEditMode && scheduleMode === 'AUTO'" label="每天排班人数">
            <el-input-number v-model="autoForm.dailyTechnicianCount" :min="1" :max="autoDailyTechnicianMax" />
          </el-form-item>

          <el-form-item v-if="!isEditMode && scheduleMode === 'AUTO'" label="已有排班处理">
            <el-switch
              v-model="autoForm.overwriteExisting"
              active-text="允许覆盖无预约排班"
              inactive-text="跳过已有排班"
            />
          </el-form-item>

          <el-form-item v-if="isEditMode || scheduleMode === 'MANUAL'" label="排班状态">
            <el-select v-model="activeStatus" class="full-width">
              <el-option label="正常上班" value="WORKING" />
              <el-option label="停排" value="OFF" />
            </el-select>
          </el-form-item>
        </el-form>

        <div class="draft-summary">
          <div class="draft-chip">
            <span>本周待提交</span>
            <strong>{{ currentWeekDraftSummary.total }}</strong>
          </div>
          <div class="draft-chip danger">
            <span>请假冲突</span>
            <strong>{{ currentWeekDraftSummary.conflicts }}</strong>
          </div>
          <div class="draft-chip">
            <span>将覆盖旧排班</span>
            <strong>{{ currentWeekDraftSummary.overwrite }}</strong>
          </div>
          <div class="draft-chip danger">
            <span>人数缺口</span>
            <strong>{{ currentWeekDraftSummary.shortage }}</strong>
          </div>
        </div>

        <div class="tips-box">
          <p><strong>提交摘要：</strong>{{ scheduleSubmitSummary }}</p>
          <p v-if="scheduleMode === 'AUTO' && !isEditMode">自动排班预览会同步显示到周排班预览中，确认应用后才会写入排班记录。</p>
          <p v-else>手动排班会按照当前选择生成草稿，确认应用后写入排班记录。</p>
        </div>
      </div>

      <template #footer>
        <div class="toolbar dialog-footer">
          <el-button @click="scheduleDialogVisible = false">取消</el-button>
          <el-button v-if="!isEditMode && scheduleMode === 'AUTO'" plain :loading="previewing" @click="refreshAutoPreview">
            生成自动排班预览
          </el-button>
          <el-button type="warning" :loading="submitting" @click="submitSchedules">
            {{ isEditMode ? '保存排班修改' : scheduleMode === 'MANUAL' ? '确认应用手动排班' : '确认应用自动排班' }}
          </el-button>
        </div>
      </template>
    </el-dialog>
  </div>
</template>

<style scoped>
.schedule-panel {
  padding: 26px;
}

.schedule-home-header {
  align-items: flex-start;
}

.preview-board {
  display: grid;
  gap: 18px;
}

.section-heading h3,
.section-heading p,
.day-header p,
.shift-item p,
.tips-box p {
  margin: 0;
}

.section-heading p,
.day-header p,
.shift-item p,
.tips-box p {
  color: var(--muted);
  line-height: 1.7;
}

.board-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.week-grid {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 12px;
}

.day-card {
  min-height: 250px;
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: var(--surface-strong);
  border: 1px solid var(--line);
}

.day-card.today {
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.32);
  background: #f8fbff;
}

.day-header {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.day-items {
  display: grid;
  gap: 10px;
}

.shift-item {
  display: grid;
  gap: 8px;
  padding: 12px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid var(--line);
}

.shift-item.draft {
  background: #fff7ed;
  border-color: #fed7aa;
}

.shift-item.conflict {
  background: #fef2f2;
  border-color: #fecaca;
}

.shift-item.expired {
  background: #f3f4f6;
  border-color: #e5e7eb;
}

.shift-top {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 8px;
}

.shift-actions {
  display: flex;
  justify-content: flex-end;
}

.schedule-dialog :deep(.el-dialog) {
  max-width: 1040px;
}

.overview-dialog-body,
.schedule-dialog-body {
  display: grid;
  gap: 18px;
}

.dialog-mode-bar {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}

.form-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px 16px;
}

.mode-switch {
  width: fit-content;
}

.form-span-2 {
  grid-column: 1 / -1;
}

.date-field,
.filter-date-box {
  display: grid;
  gap: 12px;
}

.full-width {
  width: 100%;
}

.quick-actions {
  gap: 8px;
}

.date-hint {
  display: grid;
  gap: 6px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #eff6ff;
}

.date-hint strong {
  color: var(--text);
}

.date-hint span {
  color: var(--muted);
}

.weekday-box {
  display: grid;
  gap: 12px;
}

.draft-summary {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 12px;
}

.draft-chip {
  padding: 14px 16px;
  border-radius: 8px;
  background: var(--surface-strong);
  border: 1px solid var(--line);
}

.draft-chip span {
  display: block;
  color: var(--muted);
  font-size: 14px;
  margin-bottom: 8px;
}

.draft-chip strong {
  font-size: 24px;
}

.draft-chip.danger strong {
  color: var(--danger);
}

.tips-box {
  display: grid;
  gap: 8px;
  padding: 16px 18px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px dashed var(--line);
}

.filter-bar {
  display: grid;
  grid-template-columns: 220px minmax(320px, 420px) 1fr;
  gap: 14px;
}

.pagination-wrap {
  display: flex;
  justify-content: flex-end;
}

.dialog-footer {
  justify-content: flex-end;
}

@media (max-width: 1280px) {
  .week-grid {
    grid-template-columns: repeat(4, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .filter-bar,
  .form-grid,
  .draft-summary,
  .week-grid {
    grid-template-columns: 1fr;
  }

  .form-span-2 {
    grid-column: auto;
  }

  .board-header,
  .schedule-home-header,
  .dialog-mode-bar {
    flex-direction: column;
  }

  .pagination-wrap,
  .dialog-footer {
    justify-content: flex-start;
  }
}
</style>
