<script setup lang="ts">
import { computed, onBeforeUnmount, onMounted, watch } from 'vue';
import { RouterView, useRoute, useRouter } from 'vue-router';
import { useAuthStore } from '../../store/auth';
import { useNotificationStore } from '../../store/notifications';
import { resolveAvatar } from '../../utils/avatar';

const router = useRouter();
const route = useRoute();
const auth = useAuthStore();
const notifications = useNotificationStore();

type MenuItem = { label: string; path: string };

const commonItems: MenuItem[] = [
  { label: '首页', path: '/' },
  { label: '资讯中心', path: '/news' },
  { label: '个人资料', path: '/profile' },
  { label: '站内消息', path: '/notifications' },
];

const adminMenuGroups = computed(() => [
  {
    title: '经营总览',
    items: [
      { label: '经营总览', path: '/admin/dashboard' },
      { label: '商品运营', path: '/admin/catalog' },
    ],
  },
  {
    title: '内容运营',
    items: [
      { label: '内容运营', path: '/admin/content' },
    ],
  },
  {
    title: '服务运营',
    items: [
      { label: '服务资料', path: '/admin/service' },
      { label: '预约管理', path: '/admin/appointments' },
      { label: '排班管理', path: '/admin/schedules' },
      { label: '请假审批', path: '/admin/leaves' },
    ],
  },
  {
    title: '客户互动',
    items: [
      { label: '用户管理', path: '/admin/users' },
      { label: '评论管理', path: '/admin/comments' },
      { label: '在线沟通', path: '/chat' },
    ],
  },
  {
    title: '营销财务',
    items: [
      { label: '充值活动', path: '/admin/recharge-activities' },
    ],
  },
]);

const menuItems = computed(() => {
  if (auth.user?.roleCode === 'ADMIN') {
    return [
      ...commonItems,
      ...adminMenuGroups.value.flatMap((group) => group.items),
    ];
  }

  if (auth.user?.roleCode === 'TECH') {
    return [
      ...commonItems,
      { label: '技师工作台', path: '/tech/dashboard' },
      { label: '我的预约', path: '/tech/appointments' },
      { label: '请假记录', path: '/tech/leaves' },
      { label: '在线沟通', path: '/chat' },
    ];
  }

  return [
    ...commonItems,
    { label: '钱包', path: '/wallet' },
    { label: 'AI 顾问', path: '/ai-consult' },
    { label: '服务预约', path: '/services' },
    { label: '我的收藏', path: '/favorites' },
    { label: '我的预约', path: '/appointments' },
    { label: '商品商城', path: '/shop' },
    { label: '购物车', path: '/shop/cart' },
    { label: '我的订单', path: '/shop/orders' },
    { label: '在线沟通', path: '/chat' },
  ];
});

const pageTitle = computed(() => {
  const item = [...menuItems.value]
    .sort((a, b) => b.path.length - a.path.length)
    .find((entry) => route.path === entry.path || route.path.startsWith(`${entry.path}/`));
  return item?.label || 'BFSM 工作台';
});

function isActiveMenu(path: string) {
  if (path === '/') {
    return route.path === '/';
  }

  if (path === '/shop') {
    return route.path === '/shop' || /^\/shop\/\d+$/.test(route.path);
  }

  return route.path === path || route.path.startsWith(`${path}/`);
}

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

const avatarUrl = computed(() => resolveAvatar(auth.user?.avatarUrl, auth.user?.roleCode, auth.displayName));

async function handleLogout() {
  await auth.doLogout();
  notifications.clear();
  router.push('/login');
}

onMounted(() => {
  if (auth.token) {
    notifications.startPolling();
  }
});

onBeforeUnmount(() => {
  notifications.stopPolling();
});

watch(
  () => auth.token,
  (token) => {
    if (token) {
      notifications.startPolling();
      return;
    }
    notifications.clear();
  },
);
</script>

<template>
  <div class="workspace">
    <aside class="sidebar panel">
      <div class="brand-block">
        <p class="brand-kicker">美容服务管理平台</p>
        <h1>BFSM</h1>
        <p class="brand-copy">
          将预约、商城、用户资料、收藏、消息通知和后台运营整合到同一套工作台中，形成的完整业务闭环。
        </p>
      </div>

      <nav v-if="auth.user?.roleCode === 'ADMIN'" class="nav-list admin-nav">
        <div class="nav-group compact">
          <p class="nav-group-title">常用入口</p>
          <button
            v-for="item in commonItems"
            :key="item.path"
            class="nav-button"
            :class="{ active: isActiveMenu(item.path) }"
            @click="router.push(item.path)"
          >
            {{ item.label }}
          </button>
        </div>
        <div v-for="group in adminMenuGroups" :key="group.title" class="nav-group">
          <p class="nav-group-title">{{ group.title }}</p>
          <button
            v-for="item in group.items"
            :key="item.path"
            class="nav-button"
            :class="{ active: isActiveMenu(item.path) }"
            @click="router.push(item.path)"
          >
            {{ item.label }}
          </button>
        </div>
      </nav>

      <nav v-else class="nav-list">
        <button
          v-for="item in menuItems"
          :key="item.path"
          class="nav-button"
          :class="{ active: isActiveMenu(item.path) }"
          @click="router.push(item.path)"
        >
          {{ item.label }}
        </button>
      </nav>

      <div class="phase-note">
        <span>当前身份</span>
        <strong>{{ roleLabel }}</strong>
      </div>
    </aside>

    <div class="workspace-main">
      <header class="workspace-header panel">
        <div>
          <p class="workspace-kicker">统一工作台</p>
          <h2>{{ pageTitle }}</h2>
        </div>
        <div class="workspace-actions">
          <el-badge :value="notifications.unreadCount" :hidden="notifications.unreadCount === 0">
            <el-button plain @click="router.push('/notifications')">站内消息</el-button>
          </el-badge>
          <button class="user-chip" type="button" @click="router.push('/profile')">
            <img :src="avatarUrl" alt="avatar" class="user-avatar" />
            <span>
              <strong>{{ auth.displayName }}</strong>
              <small>{{ roleLabel }}</small>
            </span>
          </button>
          <el-button type="warning" plain @click="handleLogout">退出登录</el-button>
        </div>
      </header>

      <main class="workspace-content">
        <RouterView v-slot="{ Component }">
          <transition name="page-fade" mode="out-in">
            <component :is="Component" />
          </transition>
        </RouterView>
      </main>
    </div>
  </div>
</template>

<style scoped>
.workspace {
  min-height: 100vh;
  display: grid;
  grid-template-columns: 260px 1fr;
  align-items: start;
  gap: 16px;
  padding: 16px;
}

.sidebar {
  display: grid;
  position: sticky;
  top: 20px;
  max-height: calc(100vh - 40px);
  align-content: space-between;
  padding: 18px 14px;
  overflow: hidden;
  background: var(--surface-strong);
}

.brand-block h1 {
  margin: 6px 0;
  font-size: 30px;
  letter-spacing: 0;
}

.brand-kicker,
.brand-copy,
.workspace-kicker {
  margin: 0;
  color: var(--muted);
}

.brand-kicker,
.workspace-kicker {
  font-size: 12px;
  text-transform: uppercase;
  letter-spacing: 0.16em;
}

.brand-copy {
  line-height: 1.55;
  font-size: 13px;
}

.nav-list {
  display: grid;
  gap: 8px;
  margin: 20px 0;
  min-height: 0;
  overflow-y: auto;
  padding-right: 6px;
}

.admin-nav {
  gap: 14px;
}

.nav-group {
  display: grid;
  gap: 6px;
}

.nav-group-title {
  margin: 0 0 2px;
  padding: 0 10px;
  color: var(--muted);
  font-size: 12px;
}

.compact {
  padding-bottom: 8px;
  border-bottom: 1px solid var(--line);
}

.nav-button {
  border: 0;
  border-radius: 8px;
  padding: 10px 12px;
  text-align: left;
  font: inherit;
  color: var(--text);
  background: transparent;
  cursor: pointer;
  transition: transform 0.18s ease, background 0.18s ease, color 0.18s ease;
}

.nav-button:hover,
.nav-button.active {
  transform: translateX(2px);
  color: #fff;
  background: var(--accent);
}

.phase-note {
  padding: 12px;
  border-radius: 8px;
  border: 1px solid var(--line);
  background: var(--surface);
}

.phase-note span,
.user-chip small {
  display: block;
  color: var(--muted);
  font-size: 12px;
}

.workspace-main {
  display: grid;
  gap: 16px;
}

.workspace-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 18px;
}

.workspace-header h2 {
  margin: 4px 0 0;
  font-size: 22px;
}

.workspace-actions {
  display: flex;
  align-items: center;
  gap: 14px;
}

.user-chip {
  display: inline-flex;
  align-items: center;
  gap: 12px;
  padding: 8px 12px 8px 8px;
  border-radius: 999px;
  border: 1px solid var(--line);
  background: var(--surface-strong);
  cursor: pointer;
}

.user-chip span {
  display: grid;
  text-align: left;
}

.user-avatar {
  width: 42px;
  height: 42px;
  border-radius: 50%;
  object-fit: cover;
  box-shadow: 0 6px 16px rgba(15, 23, 42, 0.14);
}

.workspace-content {
  min-width: 0;
}

@media (max-width: 1024px) {
  .workspace {
    grid-template-columns: 1fr;
  }

  .sidebar {
    position: static;
    max-height: none;
    gap: 16px;
  }
}

@media (max-width: 720px) {
  .workspace {
    padding: 12px;
  }

  .workspace-header {
    padding: 16px;
    align-items: flex-start;
    flex-direction: column;
    gap: 16px;
  }

  .workspace-actions {
    width: 100%;
    flex-wrap: wrap;
  }
}
</style>
