<script setup lang="ts">
import * as echarts from 'echarts';
import { nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue';
import { ElMessage } from 'element-plus';
import { api } from '../../api/modules';
import type { AdminSalesOverview, CategorySalesStats, ProductSalesRank, SalesTrendPoint } from '../../api/types';

function currentMonth() {
  const now = new Date();
  return `${now.getFullYear()}-${`${now.getMonth() + 1}`.padStart(2, '0')}`;
}

const month = ref(currentMonth());
const loading = ref(false);
const overview = ref<AdminSalesOverview>({
  month: month.value,
  totalSales: 0,
  paidOrderCount: 0,
  soldItemCount: 0,
  productCount: 0,
});
const salesTrend = ref<SalesTrendPoint[]>([]);
const productRanking = ref<ProductSalesRank[]>([]);
const categoryStats = ref<CategorySalesStats[]>([]);

const salesTrendRef = ref<HTMLDivElement | null>(null);
const rankingRef = ref<HTMLDivElement | null>(null);
const categoryRef = ref<HTMLDivElement | null>(null);
let charts: echarts.ECharts[] = [];

function formatCurrency(value: number) {
  return `¥${Number(value || 0).toFixed(2)}`;
}

async function loadData() {
  loading.value = true;
  try {
    const [overviewRes, trendRes, rankingRes, categoryRes] = await Promise.all([
      api.adminSalesOverview(month.value),
      api.adminSalesTrend(month.value),
      api.adminProductRanking(month.value),
      api.adminCategoryStats(month.value),
    ]);
    overview.value = overviewRes.data.data;
    salesTrend.value = trendRes.data.data;
    productRanking.value = rankingRes.data.data;
    categoryStats.value = categoryRes.data.data;
    await nextTick();
    renderCharts();
  } catch {
    ElMessage.error('商城统计加载失败，请稍后重试');
  } finally {
    loading.value = false;
  }
}

function renderCharts() {
  charts.forEach((chart) => chart.dispose());
  charts = [];

  if (salesTrendRef.value) {
    const chart = echarts.init(salesTrendRef.value);
    chart.setOption({
      tooltip: { trigger: 'axis' },
      grid: { left: 36, right: 24, top: 28, bottom: 28 },
      xAxis: { type: 'category', data: salesTrend.value.map((item) => item.label) },
      yAxis: {
        type: 'value',
        axisLabel: {
          formatter: (value: number) => `¥${value}`,
        },
      },
      series: [
        {
          type: 'line',
          smooth: true,
          data: salesTrend.value.map((item) => item.amount),
          lineStyle: { color: '#ba8c42', width: 3 },
          itemStyle: { color: '#7a5625' },
          areaStyle: { color: 'rgba(186, 140, 66, 0.16)' },
        },
      ],
    });
    charts.push(chart);
  }

  if (rankingRef.value) {
    const chart = echarts.init(rankingRef.value);
    chart.setOption({
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        formatter: (params: Array<{ dataIndex?: number }>) => {
          const row = productRanking.value[params[0]?.dataIndex ?? 0];
          if (!row) return '';
          return `${row.productName}<br/>销量：${row.quantity}<br/>销售额：${formatCurrency(row.amount)}`;
        },
      },
      grid: { left: 100, right: 24, top: 28, bottom: 28 },
      xAxis: { type: 'value' },
      yAxis: {
        type: 'category',
        data: productRanking.value.map((item) => item.productName),
      },
      series: [
        {
          type: 'bar',
          data: productRanking.value.map((item) => item.quantity),
          itemStyle: { color: '#7a5625', borderRadius: [0, 12, 12, 0] },
        },
      ],
    });
    charts.push(chart);
  }

  if (categoryRef.value) {
    const chart = echarts.init(categoryRef.value);
    chart.setOption({
      tooltip: {
        trigger: 'item',
        formatter: (params: { name: string; value: number; percent: number }) =>
          `${params.name}<br/>销售额：${formatCurrency(params.value)}<br/>占比：${params.percent}%`,
      },
      series: [
        {
          type: 'pie',
          radius: ['42%', '72%'],
          data: categoryStats.value.map((item) => ({
            name: item.categoryName || '未分类',
            value: item.amount,
          })),
          label: { color: '#2a2117' },
        },
      ],
    });
    charts.push(chart);
  }
}

function handleResize() {
  charts.forEach((chart) => chart.resize());
}

watch(month, () => {
  void loadData();
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
  <div class="page-shell" v-loading="loading">
    <section class="panel dashboard-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">商城经营统计</h2>
          <p class="panel-subtitle">默认展示当前月份数据，可切换月份查看销售额趋势、商品销量排行和分类销售分布。</p>
        </div>
        <el-date-picker v-model="month" type="month" format="YYYY-MM" value-format="YYYY-MM" placeholder="选择月份" />
      </div>

      <div class="metric-grid">
        <div class="metric-tile">
          <div class="metric-label">统计月份</div>
          <div class="metric-value mono">{{ overview.month }}</div>
        </div>
        <div class="metric-tile">
          <div class="metric-label">当月销售额</div>
          <div class="metric-value">{{ formatCurrency(overview.totalSales) }}</div>
        </div>
        <div class="metric-tile">
          <div class="metric-label">已支付订单数</div>
          <div class="metric-value">{{ overview.paidOrderCount }}</div>
        </div>
        <div class="metric-tile">
          <div class="metric-label">售出件数</div>
          <div class="metric-value">{{ overview.soldItemCount }}</div>
        </div>
        <div class="metric-tile">
          <div class="metric-label">动销商品数</div>
          <div class="metric-value">{{ overview.productCount }}</div>
        </div>
      </div>
    </section>

    <section class="section-grid">
      <article class="panel chart-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">当月销售额趋势</h2>
            <p class="panel-subtitle">按日统计已支付订单销售额，便于展示商城经营走势。</p>
          </div>
        </div>
        <div ref="salesTrendRef" class="chart-box" />
      </article>

      <article class="panel chart-panel">
        <div class="panel-header">
          <div>
            <h2 class="panel-title">商品销量排行</h2>
            <p class="panel-subtitle">按所选月份的商品销量排序，便于观察热销商品。</p>
          </div>
        </div>
        <div ref="rankingRef" class="chart-box" />
      </article>
    </section>

    <section class="panel chart-panel">
      <div class="panel-header">
        <div>
          <h2 class="panel-title">商品分类统计</h2>
          <p class="panel-subtitle">按商品分类汇总销售额，和开题中的分类统计口径保持一致。</p>
        </div>
      </div>
      <div ref="categoryRef" class="chart-box" />
    </section>
  </div>
</template>

<style scoped>
.dashboard-panel,
.chart-panel {
  padding: 26px;
}
</style>
