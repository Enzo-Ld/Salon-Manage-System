<script setup lang="ts">
import * as echarts from 'echarts';
import { Calendar, ChatDotRound, Clock, Finished, Money, Operation, Tickets, Timer, TrendCharts } from '@element-plus/icons-vue';
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { useRouter } from 'vue-router';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type {
  AppointmentSummary,
  AttendanceRecord,
  AttendanceSummary,
  Schedule,
  TechAttendanceChart,
  TechMonthStats,
  TechPerformancePoint,
} from '../../api/types';

const WEEKDAYS = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'];

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${`${now.getMonth() + 1}`.padStart(2, '0')}`;
}

function formatDate(date: Date) {
  const year = date.getFullYear();
  const monthText = `${date.getMonth() + 1}`.padStart(2, '0');
  const dayText = `${date.getDate()}`.padStart(2, '0');
  return `${year}-${monthText}-${dayText}`;
}

function addDays(date: Date, days: number) {
  const next = new Date(date);
  next.setDate(next.getDate() + days);
  return next;
}

function formatMonthDay(dateText: string) {
  const date = new Date(`${dateText}T00:00:00`);
  return `${date.getMonth() + 1}/${date.getDate()}`;
}

function formatWeekday(dateText: string) {
  return WEEKDAYS[new Date(`${dateText}T00:00:00`).getDay()];
}

function formatCurrency(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

function minuteToTimeLabel(value?: number | null) {
  if (value == null || Number.isNaN(value)) {
    return '--';
  }
  const hour = Math.floor(value / 60);
  const minute = value % 60;
  return `${`${hour}`.padStart(2, '0')}:${`${minute}`.padStart(2, '0')}`;
}

function scheduleStatusText(status: string) {
  if (status === 'WORKING') return '上班';
  if (status === 'OFF') return '停排/休息';
  return status || '--';
}

function scheduleTagType(status: string) {
  if (status === 'WORKING') return 'primary';
  if (status === 'OFF') return 'info';
  return 'info';
}

const router = useRouter();
const month = ref(currentMonth());
const loading = ref(false);
const attendanceLoading = ref(false);
const scheduleDialogVisible = ref(false);
const appointmentDialogVisible = ref(false);
const analyticsDialogVisible = ref(false);
const attendanceRecordDialogVisible = ref(false);

const stats = ref<TechMonthStats>({ month: month.value, serviceCount: 0, amount: 0 });
const performanceTrend = ref<TechPerformancePoint[]>([]);
const attendanceChart = ref<TechAttendanceChart>({
  month: month.value,
  onDutyCount: 0,
  offDutyCount: 0,
  leaveCount: 0,
  absentCount: 0,
  days: [],
});
const appointments = ref<AppointmentSummary[]>([]);
const attendance = ref<AttendanceSummary>({
  technicianId: 0,
  technicianName: '',
  workDate: '',
  attendanceStatus: 'NOT_CHECKED_IN',
  attendanceStatusLabel: '未打卡',
  onLeaveNow: false,
  hasScheduleToday: false,
  canCheckIn: false,
  canCheckOut: false,
  scheduleWindows: [],
});
const attendanceRecords = ref<AttendanceRecord[]>([]);
const schedules = ref<Schedule[]>([]);

const performanceRef = ref<HTMLDivElement | null>(null);
const attendanceSummaryRef = ref<HTMLDivElement | null>(null);
const attendanceTimeRef = ref<HTMLDivElement | null>(null);
let charts: echarts.ECharts[] = [];

const todayText = computed(() => formatDate(new Date()));
const scheduleRange = computed(() => {
  const start = new Date();
  const end = addDays(start, 29);
  return {
    startDate: formatDate(start),
    endDate: formatDate(end),
  };
});

const pendingCount = computed(() => appointments.value.filter((item) => item.status === 'PENDING').length);
const recentAppointments = computed(() => appointments.value.slice(0, 5));
const workingSchedules = computed(() => schedules.value.filter((item) => item.status === 'WORKING'));
const workingDayCount = computed(() => new Set(workingSchedules.value.map((item) => item.workDate)).size);
const nextSchedule = computed(() => workingSchedules.value.find((item) => item.workDate >= todayText.value));
const nextScheduleText = computed(() => {
  const item = nextSchedule.value;
  if (!item) return '暂无后续班次';
  return `${item.workDate} ${item.startTime} - ${item.endTime}`;
});
const scheduleDays = computed(() =>
  Array.from({ length: 30 }, (_, index) => {
    const dateText = formatDate(addDays(new Date(), index));
    return {
      date: dateText,
      monthDay: formatMonthDay(dateText),
      weekday: formatWeekday(dateText),
      isToday: dateText === todayText.value,
      items: schedules.value
        .filter((item) => item.workDate === dateText)
        .sort((a, b) => a.startTime.localeCompare(b.startTime)),
    };
  }),
);
const metricCards = computed(() => [
  { label: '本月完成服务', value: stats.value.serviceCount, helper: stats.value.month || month.value, icon: Finished },
  { label: '本月服务金额', value: formatCurrency(stats.value.amount), helper: '已完成预约收入', icon: Money },
  { label: '待确认预约', value: pendingCount.value, helper: '需要尽快处理', icon: Tickets },
  { label: '未来 30 天班次', value: workingSchedules.value.length, helper: `${workingDayCount.value} 个工作日`, icon: Calendar },
]);

async function loadData() {
  loading.value = true;
  try {
    const [statsRes, performanceRes, attendanceChartRes, appointmentRes, attendanceRes, attendanceRecordsRes, scheduleRes] =
      await Promise.all([
        api.techMonthStats(month.value),
        api.techPerformanceTrend(month.value),
        api.techAttendanceChart(month.value),
        api.appointments(),
        api.attendanceToday(),
        api.attendanceRecords(),
        api.mySchedules(scheduleRange.value),
      ]);
    stats.value = statsRes.data.data;
    performanceTrend.value = performanceRes.data.data;
    attendanceChart.value = attendanceChartRes.data.data;
    appointments.value = appointmentRes.data.data;
    attendance.value = attendanceRes.data.data;
    attendanceRecords.value = attendanceRecordsRes.data.data;
    schedules.value = scheduleRes.data.data;
    await nextTick();
    renderCharts();
  } catch {
    ElMessage.error('技师工作台数据加载失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

function renderCharts() {
  charts.forEach((chart) => chart.dispose());
  charts = [];

  if (performanceRef.value) {
    const chart = echarts.init(performanceRef.value);
    chart.setOption({
      color: ['#2563eb', '#0f766e'],
      tooltip: { trigger: 'axis' },
      legend: { data: ['完成单量', '服务金额'], top: 6 },
      grid: { left: 40, right: 40, top: 54, bottom: 28 },
      xAxis: { type: 'category', data: performanceTrend.value.map((item) => item.label) },
      yAxis: [
        { type: 'value', name: '单量' },
        {
          type: 'value',
          name: '金额',
          axisLabel: { formatter: (value: number) => `¥${value}` },
        },
      ],
      series: [
        {
          name: '完成单量',
          type: 'bar',
          data: performanceTrend.value.map((item) => item.serviceCount),
          itemStyle: { borderRadius: [6, 6, 0, 0] },
        },
        {
          name: '服务金额',
          type: 'line',
          smooth: true,
          yAxisIndex: 1,
          data: performanceTrend.value.map((item) => item.amount),
          lineStyle: { width: 3 },
        },
      ],
    });
    charts.push(chart);
  }

  if (attendanceSummaryRef.value) {
    const chart = echarts.init(attendanceSummaryRef.value);
    chart.setOption({
      color: ['#2563eb', '#38bdf8', '#f59e0b', '#ef4444'],
      tooltip: { trigger: 'item' },
      series: [
        {
          type: 'pie',
          radius: ['42%', '68%'],
          data: [
            { name: '出勤', value: attendanceChart.value.onDutyCount },
            { name: '下班', value: attendanceChart.value.offDutyCount },
            { name: '请假', value: attendanceChart.value.leaveCount },
            { name: '缺勤', value: attendanceChart.value.absentCount },
          ],
          label: { color: '#1f2937' },
        },
      ],
    });
    charts.push(chart);
  }

  if (attendanceTimeRef.value) {
    const chart = echarts.init(attendanceTimeRef.value);
    chart.setOption({
      color: ['#2563eb', '#0f766e'],
      tooltip: {
        trigger: 'axis',
        valueFormatter: (value: number) => minuteToTimeLabel(value),
      },
      legend: { data: ['上班打卡', '下班打卡'], top: 6 },
      grid: { left: 48, right: 28, top: 54, bottom: 28 },
      xAxis: { type: 'category', data: attendanceChart.value.days.map((item) => item.label) },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value: number) => minuteToTimeLabel(value),
        },
      },
      series: [
        {
          name: '上班打卡',
          type: 'line',
          smooth: true,
          data: attendanceChart.value.days.map((item) => item.checkInMinute ?? null),
          lineStyle: { width: 3 },
        },
        {
          name: '下班打卡',
          type: 'line',
          smooth: true,
          data: attendanceChart.value.days.map((item) => item.checkOutMinute ?? null),
          lineStyle: { width: 3 },
        },
      ],
    });
    charts.push(chart);
  }
}

function handleResize() {
  charts.forEach((chart) => chart.resize());
}

async function refreshAttendanceRecords() {
  const { data: recordsData } = await api.attendanceRecords();
  attendanceRecords.value = recordsData.data;
}

async function checkIn() {
  attendanceLoading.value = true;
  try {
    const { data } = await api.checkInAttendance();
    attendance.value = data.data;
    ElMessage.success('上班打卡成功');
    await refreshAttendanceRecords();
  } finally {
    attendanceLoading.value = false;
  }
}

async function checkOut() {
  attendanceLoading.value = true;
  try {
    const { data } = await api.checkOutAttendance();
    attendance.value = data.data;
    ElMessage.success('下班打卡成功');
    await refreshAttendanceRecords();
  } finally {
    attendanceLoading.value = false;
  }
}

function statusTagType(status: string) {
  if (status === 'ON_DUTY') return 'success';
  if (status === 'OFF_DUTY') return 'info';
  if (status === 'ON_LEAVE') return 'warning';
  return 'info';
}

function openChat(item: AppointmentSummary) {
  router.push({
    path: '/chat',
    query: {
      receiverId: item.userId,
      appointmentId: item.id,
      serviceName: item.serviceItemName,
      appointmentTime: `${item.appointmentTime}${item.appointmentEndTime ? ` - ${item.appointmentEndTime}` : ''}`,
      draft: `您好，您的“${item.serviceItemName}”预约已由我接单，如需确认到店时间或服务准备事项，可以直接在这里沟通。`,
    },
  });
}

watch(month, () => {
  void loadData();
});

watch(analyticsDialogVisible, async (visible) => {
  if (!visible) return;
  await nextTick();
  renderCharts();
});

onMounted(async () => {
  await loadData();
  window.addEventListener('resize', handleResize);
});

onBeforeUnmount(() => {
  window.removeEventListener('resize', handleResize);
  charts.forEach((chart) => chart.dispose());
});
</script>

<template>
  <div class="page-shell tech-workbench" v-loading="loading">
    <section class="panel dashboard-panel dashboard-hero">
      <div class="panel-header hero-content">
        <div>
          <h2 class="panel-title">技师工作台</h2>
          <p class="panel-subtitle">首页只保留今日最重要的工作状态，其余信息通过弹窗查看。</p>
        </div>
        <div class="hero-actions">
          <el-date-picker v-model="month" type="month" format="YYYY-MM" value-format="YYYY-MM" placeholder="选择月份" />
          <el-button type="primary" :icon="Operation" @click="router.push('/tech/appointments')">处理预约</el-button>
        </div>
      </div>

      <div class="metric-grid hero-metrics">
        <article v-for="item in metricCards" :key="item.label" class="metric-tile hero-metric">
          <div class="metric-icon">
            <el-icon><component :is="item.icon" /></el-icon>
          </div>
          <div>
            <div class="metric-label">{{ item.label }}</div>
            <div class="metric-value">{{ item.value }}</div>
            <p>{{ item.helper }}</p>
          </div>
        </article>
      </div>
    </section>

    <section class="panel attendance-panel">
      <div class="panel-header compact-header">
        <div>
          <h2 class="panel-title">今日考勤</h2>
          <p class="panel-subtitle">打卡会自动校验今日排班和请假记录。</p>
        </div>
        <el-tag :type="statusTagType(attendance.attendanceStatus)" effect="dark">{{ attendance.attendanceStatusLabel }}</el-tag>
      </div>

      <div class="attendance-card compact-attendance">
        <div class="attendance-main">
          <div class="attendance-row">
            <span>工作日期</span>
            <strong>{{ attendance.workDate || '--' }}</strong>
          </div>
          <div class="attendance-row">
            <span>今日排班</span>
            <strong>{{ attendance.scheduleWindows.join(' / ') || '今日暂无排班' }}</strong>
          </div>
          <div class="attendance-row">
            <span>上班打卡</span>
            <strong>{{ attendance.checkInTime || '--' }}</strong>
          </div>
          <div class="attendance-row">
            <span>下班打卡</span>
            <strong>{{ attendance.checkOutTime || '--' }}</strong>
          </div>
          <p v-if="attendance.onLeaveNow" class="attendance-warning">当前处于已批准请假时段，暂不可打卡。</p>
        </div>
        <div class="attendance-actions">
          <el-button type="primary" :icon="Clock" :loading="attendanceLoading" :disabled="!attendance.canCheckIn" @click="checkIn">
            上班打卡
          </el-button>
          <el-button plain :icon="Timer" :loading="attendanceLoading" :disabled="!attendance.canCheckOut" @click="checkOut">
            下班打卡
          </el-button>
        </div>
      </div>
    </section>

    <section class="panel chart-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">个人业绩图</h2>
          <p class="panel-subtitle">按日展示完成单量和服务金额趋势。</p>
        </div>
        <el-icon class="panel-mark"><TrendCharts /></el-icon>
      </div>
      <div ref="performanceRef" class="chart-box" />
    </section>

    <section class="panel quick-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">更多工作信息</h2>
          <p class="panel-subtitle">排班、预约、考勤图表和考勤记录通过弹窗查看。</p>
        </div>
      </div>
      <div class="quick-action-grid">
        <button type="button" class="quick-action" @click="scheduleDialogVisible = true">
          <el-icon><Calendar /></el-icon>
          <span>排班日历</span>
          <strong>{{ nextScheduleText }}</strong>
        </button>
        <button type="button" class="quick-action" @click="appointmentDialogVisible = true">
          <el-icon><Tickets /></el-icon>
          <span>最近预约</span>
          <strong>待确认 {{ pendingCount }} 条</strong>
        </button>
        <button type="button" class="quick-action" @click="analyticsDialogVisible = true">
          <el-icon><TrendCharts /></el-icon>
          <span>考勤图表</span>
          <strong>{{ stats.month || month }}</strong>
        </button>
        <button type="button" class="quick-action" @click="attendanceRecordDialogVisible = true">
          <el-icon><Finished /></el-icon>
          <span>考勤记录</span>
          <strong>最近 {{ attendanceRecords.length }} 条</strong>
        </button>
        <button type="button" class="quick-action" @click="router.push('/tech/leaves')">
          <el-icon><Calendar /></el-icon>
          <span>请假记录</span>
          <strong>申请与查看</strong>
        </button>
        <button type="button" class="quick-action" @click="router.push('/chat')">
          <el-icon><ChatDotRound /></el-icon>
          <span>在线沟通</span>
          <strong>联系用户</strong>
        </button>
      </div>
    </section>

    <el-dialog
      v-model="scheduleDialogVisible"
      title="未来 30 天排班日历"
      width="92vw"
      class="schedule-dialog"
      append-to-body
    >
      <div class="dialog-heading">
        <div>
          <strong>{{ scheduleRange.startDate }} 至 {{ scheduleRange.endDate }}</strong>
          <p>只读展示当前登录技师自己的排班。</p>
        </div>
        <el-tag type="primary" effect="plain">我的排班</el-tag>
      </div>

      <div class="schedule-board dialog-board">
        <article v-for="day in scheduleDays" :key="day.date" class="schedule-day" :class="{ today: day.isToday }">
          <div class="day-head">
            <div>
              <strong>{{ day.monthDay }}</strong>
              <span>{{ day.weekday }}</span>
            </div>
            <el-tag v-if="day.isToday" size="small" type="primary" effect="dark">今天</el-tag>
          </div>

          <div v-if="day.items.length" class="shift-list">
            <div v-for="item in day.items" :key="item.id || `${item.workDate}-${item.startTime}`" class="shift-card">
              <el-tag size="small" :type="scheduleTagType(item.status)" effect="plain">{{ scheduleStatusText(item.status) }}</el-tag>
              <span>{{ item.startTime }} - {{ item.endTime }}</span>
            </div>
          </div>
          <p v-else class="empty-day">暂无排班</p>
        </article>
      </div>
    </el-dialog>

    <el-dialog v-model="appointmentDialogVisible" title="最近预约" width="78vw" class="workbench-dialog" append-to-body>
      <div class="dialog-heading">
        <div>
          <strong>最近 5 条预约</strong>
          <p>可在这里快速联系用户，完整处理请进入预约页面。</p>
        </div>
        <el-button type="primary" plain :icon="Tickets" @click="router.push('/tech/appointments')">全部预约</el-button>
      </div>
      <div class="soft-list appointment-list">
        <div v-for="item in recentAppointments" :key="item.id" class="soft-list-item appointment-item">
          <div>
            <strong>{{ item.serviceItemName }}</strong>
            <p>{{ item.appointmentTime }}<span v-if="item.appointmentEndTime"> - {{ item.appointmentEndTime }}</span></p>
            <p>预约用户：{{ item.username }}</p>
          </div>
          <div class="appointment-actions">
            <el-tag effect="plain">{{ item.statusLabel }}</el-tag>
            <el-button size="small" plain @click="router.push('/tech/appointments')">去处理</el-button>
            <el-button size="small" type="primary" plain :icon="ChatDotRound" @click="openChat(item)">联系用户</el-button>
          </div>
        </div>
        <el-empty v-if="recentAppointments.length === 0" description="当前暂无预约数据" />
      </div>
    </el-dialog>

    <el-dialog v-model="analyticsDialogVisible" title="考勤图表" width="88vw" class="workbench-dialog" append-to-body>
      <div class="dialog-heading">
        <div>
          <strong>{{ stats.month || month }} 考勤数据</strong>
          <p>按所选月份展示考勤状态分布和打卡时间趋势。</p>
        </div>
      </div>
      <div class="dialog-chart-grid">
        <article class="dialog-chart-card">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">考勤状态分布</h2>
              <p class="panel-subtitle">汇总出勤、下班、请假和缺勤情况。</p>
            </div>
          </div>
          <div ref="attendanceSummaryRef" class="chart-box" />
        </article>
        <article class="dialog-chart-card">
          <div class="panel-header">
            <div>
              <h2 class="panel-title">打卡时间趋势</h2>
              <p class="panel-subtitle">按日展示上下班打卡时间。</p>
            </div>
          </div>
          <div ref="attendanceTimeRef" class="chart-box trend-chart" />
        </article>
      </div>
    </el-dialog>

    <el-dialog v-model="attendanceRecordDialogVisible" title="考勤记录" width="76vw" class="workbench-dialog" append-to-body>
      <el-table :data="attendanceRecords" stripe>
        <el-table-column prop="workDate" label="日期" min-width="120" />
        <el-table-column prop="checkInTime" label="上班打卡" min-width="160" />
        <el-table-column prop="checkOutTime" label="下班打卡" min-width="160" />
        <el-table-column prop="status" label="状态" min-width="120" />
      </el-table>
    </el-dialog>
  </div>
</template>

<style scoped>
.tech-workbench {
  gap: 22px;
}

.dashboard-panel.dashboard-hero {
  padding: 24px;
}

.hero-content {
  margin-bottom: 24px;
}

.hero-metric p,
.attendance-warning,
.empty-day,
.soft-list-item p,
.schedule-note,
.dialog-heading p {
  margin: 0;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 12px;
  min-width: 280px;
}

.hero-metrics {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.hero-metric {
  display: flex;
  gap: 14px;
  align-items: flex-start;
  background: rgba(255, 255, 255, 0.96);
}

.metric-icon {
  display: grid;
  place-items: center;
  width: 42px;
  height: 42px;
  flex: 0 0 auto;
  border-radius: 8px;
  color: #2563eb;
  background: #eff6ff;
}

.hero-metric .metric-value {
  font-size: 28px;
}

.hero-metric p {
  margin-top: 4px;
  color: var(--muted);
  font-size: 14px;
}

.ops-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) minmax(300px, 0.6fr);
  gap: 22px;
}

.dashboard-panel,
.chart-panel,
.attendance-panel,
.list-panel,
.task-panel,
.quick-panel,
.schedule-summary-panel,
.schedule-board-panel {
  padding: 24px;
}

.compact-header {
  align-items: flex-start;
}

.attendance-card {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 20px;
  margin-bottom: 18px;
  padding: 18px;
  border-radius: 8px;
  background: linear-gradient(180deg, #f8fbff, #fff);
  border: 1px solid #dbeafe;
}

.attendance-main {
  display: grid;
  gap: 10px;
}

.attendance-row {
  display: flex;
  justify-content: space-between;
  gap: 18px;
  color: var(--muted);
}

.attendance-row strong {
  color: var(--text);
  text-align: right;
}

.attendance-actions {
  display: grid;
  align-content: start;
  gap: 10px;
  min-width: 138px;
}

.attendance-warning {
  color: #c76a1b;
}

.quick-action-grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 12px;
}

.quick-action {
  min-height: 112px;
  display: grid;
  align-content: start;
  gap: 8px;
  padding: 16px;
  text-align: left;
  color: var(--text);
  cursor: pointer;
  border: 1px solid var(--line);
  border-radius: 8px;
  background: var(--surface-strong);
  font: inherit;
  transition: border-color 0.18s ease, transform 0.18s ease, box-shadow 0.18s ease;
}

.quick-action:hover {
  border-color: #93c5fd;
  transform: translateY(-1px);
  box-shadow: 0 8px 22px rgba(37, 99, 235, 0.1);
}

.quick-action .el-icon {
  width: 34px;
  height: 34px;
  border-radius: 8px;
  color: var(--accent);
  background: #eff6ff;
}

.quick-action span {
  font-weight: 700;
}

.quick-action strong {
  color: var(--muted);
  font-size: 15px;
  font-weight: 500;
}

.schedule-next {
  display: flex;
  gap: 14px;
  align-items: center;
  padding: 18px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.schedule-next .el-icon {
  width: 46px;
  height: 46px;
  border-radius: 8px;
  color: #fff;
  background: var(--accent);
}

.schedule-next span,
.schedule-stat span,
.day-head span,
.empty-day,
.soft-list-item p {
  color: var(--muted);
}

.schedule-next strong {
  display: block;
  margin-top: 4px;
}

.schedule-stat-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 10px;
  margin-top: 14px;
}

.schedule-stat {
  padding: 14px;
  border-radius: 8px;
  background: var(--surface-strong);
  border: 1px solid var(--line);
}

.schedule-stat strong {
  display: block;
  margin-top: 8px;
  font-size: 26px;
}

.schedule-stat small {
  margin-left: 2px;
  font-size: 14px;
  color: var(--muted);
}

.schedule-note {
  margin-top: 12px;
  color: var(--muted);
  font-size: 15px;
}

.schedule-dialog :deep(.el-dialog),
.workbench-dialog :deep(.el-dialog) {
  max-width: 1180px;
}

.dialog-heading {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
  margin-bottom: 16px;
  padding: 14px 16px;
  border-radius: 8px;
  background: #eff6ff;
  border: 1px solid #bfdbfe;
}

.dialog-heading p {
  margin-top: 4px;
  color: var(--muted);
}

.dialog-chart-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
}

.dialog-chart-card {
  padding: 18px;
  border-radius: 8px;
  background: var(--surface-strong);
  border: 1px solid var(--line);
}

.dialog-chart-wide {
  grid-column: 1 / -1;
}

.schedule-board {
  display: grid;
  grid-template-columns: repeat(7, minmax(0, 1fr));
  gap: 12px;
}

.schedule-day {
  min-height: 148px;
  display: grid;
  align-content: start;
  gap: 12px;
  padding: 14px;
  border-radius: 8px;
  background: #f8fafc;
  border: 1px solid var(--line);
}

.schedule-day.today {
  background: #eff6ff;
  border-color: #93c5fd;
  box-shadow: inset 0 0 0 1px rgba(37, 99, 235, 0.12);
}

.day-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 10px;
}

.day-head strong,
.day-head span {
  display: block;
}

.shift-list {
  display: grid;
  gap: 8px;
}

.shift-card {
  display: grid;
  gap: 6px;
  padding: 10px;
  border-radius: 8px;
  background: #fff;
  border: 1px solid #dbeafe;
}

.shift-card span {
  color: var(--text);
  font-size: 15px;
}

.appointment-list {
  gap: 10px;
}

.appointment-item {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 16px;
}

.appointment-actions {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

.panel-mark {
  color: var(--accent);
  font-size: 28px;
}

.trend-chart {
  min-height: 280px;
}

@media (max-width: 1280px) {
  .hero-metrics,
  .quick-action-grid,
  .schedule-board {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 1080px) {
  .ops-grid,
  .section-grid {
    grid-template-columns: 1fr;
  }

  .appointment-item {
    flex-direction: column;
  }

  .hero-actions,
  .appointment-actions {
    justify-content: flex-start;
  }
}

@media (max-width: 720px) {
  .dashboard-panel.dashboard-hero,
  .chart-panel,
  .attendance-panel,
  .list-panel,
  .task-panel,
  .quick-panel,
  .schedule-summary-panel,
  .schedule-board-panel {
    padding: 18px;
  }

  .hero-metrics,
  .quick-action-grid,
  .dialog-chart-grid,
  .schedule-stat-grid,
  .schedule-board {
    grid-template-columns: 1fr;
  }

  .attendance-card {
    grid-template-columns: 1fr;
  }

  .attendance-actions {
    display: flex;
    flex-wrap: wrap;
  }

  .attendance-row {
    flex-direction: column;
    gap: 2px;
  }

  .attendance-row strong {
    text-align: left;
  }
}
</style>
