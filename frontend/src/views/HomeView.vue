<script setup lang="ts">
import { computed, onMounted, ref } from 'vue';
import { useRouter } from 'vue-router';
import { api } from '../api/modules';
import type { Announcement, Banner } from '../api/types';

const router = useRouter();
const banners = ref<Banner[]>([]);
const announcements = ref<Announcement[]>([]);

const fallbackBanners: Banner[] = [
  {
    id: -1,
    title: 'BFSM 美容美发服务平台',
    subtitle: '预约服务、商品购买、在线沟通，一站式完成门店服务体验。',
    imageUrl: 'https://images.unsplash.com/photo-1560066984-138dadb4c035?auto=format&fit=crop&w=1600&q=85',
    linkUrl: '/services',
    sortNo: 1,
    status: 'ON',
  },
  {
    id: -2,
    title: '从咨询到到店服务',
    subtitle: '用户选择服务项目，系统结合排班和技师能力完成预约演示。',
    imageUrl: 'https://images.unsplash.com/photo-1522337360788-8b13dee7a37e?auto=format&fit=crop&w=1600&q=85',
    linkUrl: '/chat',
    sortNo: 2,
    status: 'ON',
  },
];

const heroBanners = computed(() => (banners.value.length > 0 ? banners.value : fallbackBanners));
const latestNews = computed(() => announcements.value.slice(0, 4));

const portalEntries = [
  {
    title: '服务预约',
    desc: '浏览服务项目，选择预约时间，技师推荐、支付和服务流转。',
    path: '/services',
    action: '立即预约',
    index: '01',
  },
  {
    title: '商品购买',
    desc: '进入门店商城，商品详情、购物车、地址、下单和支付流程。',
    path: '/shop',
    action: '选购商品',
    index: '02',
  },
  {
    title: '在线沟通',
    desc: '与技师沟通或平台管理员进行线上咨询，实时沟通能力。',
    path: '/chat',
    action: '开始沟通',
    index: '03',
  },
];

function go(path: string) {
  void router.push(path);
}

function openBanner(item: Banner) {
  if (!item.linkUrl) return;
  if (item.linkUrl.startsWith('http')) {
    window.open(item.linkUrl, '_blank', 'noopener');
    return;
  }
  go(item.linkUrl);
}

function formatDate(value?: string | null) {
  if (!value) return '最新发布';
  return value.slice(0, 10);
}

onMounted(async () => {
  const [bannerRes, announcementRes] = await Promise.all([api.banners(), api.announcements()]);
  banners.value = bannerRes.data.data || [];
  announcements.value = announcementRes.data.data || [];
});
</script>

<template>
  <div class="home-page">
    <section class="home-hero">
      <el-carousel height="520px" indicator-position="outside" arrow="always">
        <el-carousel-item v-for="item in heroBanners" :key="item.id || item.title">
          <article
            class="hero-slide"
            :style="{ backgroundImage: `linear-gradient(90deg, rgba(43, 29, 16, 0.78), rgba(43, 29, 16, 0.34), rgba(43, 29, 16, 0.12)), url(${item.imageUrl})` }"
          >
            <div class="hero-content">
              <p class="section-kicker">BFSM BEAUTY SERVICE</p>
              <h1>{{ item.title || '美容美发预约、商品购买、在线咨询一体化平台' }}</h1>
              <p class="hero-desc">
                {{ item.subtitle || '围绕门店真实经营流程，完成服务预约、商品购买、支付结算、在线沟通和运营管理演示。' }}
              </p>
              <div class="hero-actions">
                <el-button type="warning" size="large" @click="go('/services')">立即预约</el-button>
                <el-button size="large" plain @click="go('/shop')">选购商品</el-button>
                <el-button v-if="item.linkUrl" size="large" text class="hero-link" @click="openBanner(item)">查看详情</el-button>
              </div>
            </div>
          </article>
        </el-carousel-item>
      </el-carousel>
    </section>

    <section class="portal-strip">
      <button
        v-for="entry in portalEntries"
        :key="entry.path"
        class="portal-card"
        type="button"
        @click="go(entry.path)"
      >
        <span class="portal-index">{{ entry.index }}</span>
        <strong>{{ entry.title }}</strong>
        <p>{{ entry.desc }}</p>
        <em>{{ entry.action }}</em>
      </button>
    </section>

    <section class="panel news-panel">
      <div class="panel-header">
        <div>
          <p class="section-kicker">NEWS CENTER</p>
          <h2 class="panel-title">最新资讯</h2>
          <p class="panel-subtitle">展示后台发布的公告与资讯内容，体现首页内容运营能力。</p>
        </div>
        <el-button plain @click="go('/news')">查看全部</el-button>
      </div>
      <div class="news-grid" v-if="latestNews.length > 0">
        <button
          v-for="item in latestNews"
          :key="item.id"
          class="news-card"
          type="button"
          @click="go(`/news/${item.id}`)"
        >
          <span>{{ formatDate(item.publishTime || item.createdAt) }}</span>
          <strong>{{ item.title }}</strong>
          <p>{{ item.summary || '点击查看资讯详情，了解门店活动、平台公告和运营内容。' }}</p>
        </button>
      </div>
      <el-empty v-else description="暂无已发布资讯" />
    </section>
  </div>
</template>

<style scoped>
.home-page {
  display: grid;
  gap: 24px;
}

.home-hero {
  overflow: hidden;
  border-radius: 28px;
  box-shadow: var(--shadow);
}

.hero-slide {
  min-height: 520px;
  display: flex;
  align-items: center;
  padding: 56px;
  color: #fff;
  background-size: cover;
  background-position: center;
}

.hero-content {
  width: min(680px, 100%);
}

.section-kicker {
  margin: 0 0 12px;
  color: var(--accent);
  font-size: 12px;
  font-weight: 700;
  letter-spacing: 0.16em;
  text-transform: uppercase;
}

.hero-content .section-kicker {
  color: #f1d59b;
}

.hero-content h1 {
  margin: 0;
  max-width: 15ch;
  font-size: 54px;
  line-height: 1.08;
  letter-spacing: 0;
}

.hero-desc {
  max-width: 44ch;
  margin: 22px 0 30px;
  color: rgba(255, 255, 255, 0.9);
  font-size: 20px;
  line-height: 1.8;
}

.hero-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}

.hero-link {
  color: #fff;
}

.portal-strip {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.portal-card {
  min-height: 220px;
  display: flex;
  flex-direction: column;
  align-items: flex-start;
  border: 1px solid rgba(122, 86, 37, 0.14);
  border-radius: 24px;
  padding: 24px;
  color: var(--text);
  text-align: left;
  font: inherit;
  cursor: pointer;
  background:
    linear-gradient(135deg, rgba(255, 253, 248, 0.96), rgba(247, 238, 222, 0.88)),
    radial-gradient(circle at top right, rgba(186, 140, 66, 0.18), transparent 48%);
  box-shadow: 0 18px 50px rgba(68, 43, 13, 0.1);
  transition: transform 0.2s ease, box-shadow 0.2s ease, border-color 0.2s ease;
}

.portal-card:hover {
  transform: translateY(-4px);
  border-color: rgba(186, 140, 66, 0.38);
  box-shadow: 0 24px 70px rgba(68, 43, 13, 0.16);
}

.portal-index {
  color: var(--accent-deep);
  font-family: 'Cascadia Code', 'Consolas', monospace;
  font-size: 14px;
  font-weight: 700;
}

.portal-card strong {
  margin-top: 18px;
  font-size: 28px;
  line-height: 1.2;
}

.portal-card p {
  margin: 14px 0 22px;
  color: var(--muted);
  line-height: 1.75;
}

.portal-card em {
  margin-top: auto;
  color: var(--accent-deep);
  font-style: normal;
  font-weight: 700;
}

.home-sections {
  align-items: stretch;
}

.capability-panel,
.consult-panel,
.news-panel {
  padding: 26px;
}

.capability-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 14px;
}

.capability-item {
  min-height: 118px;
  padding: 18px;
  border-radius: 20px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  background: rgba(255, 250, 242, 0.84);
}

.capability-item strong,
.capability-item span {
  display: block;
}

.capability-item strong {
  font-size: 22px;
}

.capability-item span {
  margin-top: 10px;
  color: var(--muted);
  line-height: 1.7;
}

.consult-panel {
  display: flex;
  flex-direction: column;
  justify-content: center;
  background:
    linear-gradient(145deg, rgba(60, 43, 25, 0.92), rgba(122, 86, 37, 0.86)),
    url('https://images.unsplash.com/photo-1605497788044-5a32c7078486?auto=format&fit=crop&w=900&q=80') center/cover;
  color: #fff;
}

.consult-panel .section-kicker,
.consult-panel p {
  color: rgba(255, 238, 204, 0.92);
}

.consult-panel h2 {
  margin: 0;
  font-size: 36px;
  line-height: 1.18;
}

.consult-panel p {
  margin: 18px 0 24px;
  line-height: 1.8;
}

.journey-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
}

.news-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.news-card {
  min-height: 190px;
  border: 1px solid rgba(122, 86, 37, 0.12);
  border-radius: 20px;
  padding: 18px;
  color: var(--text);
  text-align: left;
  font: inherit;
  cursor: pointer;
  background: rgba(255, 250, 242, 0.82);
  transition: transform 0.2s ease, border-color 0.2s ease;
}

.news-card:hover {
  transform: translateY(-3px);
  border-color: rgba(186, 140, 66, 0.34);
}

.news-card span {
  color: var(--accent-deep);
  font-size: 14px;
  font-weight: 700;
}

.news-card strong {
  display: block;
  margin-top: 12px;
  font-size: 21px;
  line-height: 1.35;
}

.news-card p {
  margin: 12px 0 0;
  color: var(--muted);
  line-height: 1.7;
}

@media (max-width: 1180px) {
  .portal-strip,
  .news-grid {
    grid-template-columns: 1fr;
  }

  .hero-content h1 {
    font-size: 44px;
  }
}

@media (max-width: 720px) {
  .home-hero {
    border-radius: 22px;
  }

  .hero-slide {
    min-height: 500px;
    padding: 32px 22px;
  }

  .hero-content h1,
  .consult-panel h2 {
    font-size: 32px;
  }

  .hero-desc {
    font-size: 18px;
  }

  .portal-card,
  .capability-panel,
  .consult-panel,
  .news-panel {
    padding: 20px;
  }

  .capability-grid {
    grid-template-columns: 1fr;
  }

  .panel-header {
    align-items: flex-start;
    flex-direction: column;
  }
}
</style>
