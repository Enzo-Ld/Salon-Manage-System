import { createRouter, createWebHistory } from 'vue-router';
import { useAuthStore } from '../store/auth';

const routes = [
  { path: '/login', component: () => import('../views/LoginView.vue'), meta: { guestOnly: true } },
  { path: '/register', component: () => import('../views/RegisterView.vue'), meta: { guestOnly: true } },
  {
    path: '/',
    component: () => import('../views/layout/MainLayout.vue'),
    children: [
      { path: '', component: () => import('../views/HomeView.vue') },
      { path: 'profile', component: () => import('../views/ProfileView.vue') },
      { path: 'notifications', component: () => import('../views/NotificationView.vue') },
      { path: 'news', component: () => import('../views/NewsListView.vue') },
      { path: 'news/:id', component: () => import('../views/NewsDetailView.vue') },
      { path: 'wallet', component: () => import('../views/user/UserWalletView.vue'), meta: { roles: ['USER'] } },
      { path: 'payment/result', component: () => import('../views/user/PaymentResultView.vue'), meta: { roles: ['USER'] } },
      { path: 'ai-consult', component: () => import('../views/user/AiConsultView.vue'), meta: { roles: ['USER'] } },
      { path: 'services', component: () => import('../views/user/ServiceListView.vue'), meta: { roles: ['USER'] } },
      { path: 'favorites', component: () => import('../views/user/MyFavoritesView.vue'), meta: { roles: ['USER'] } },
      { path: 'appointments', component: () => import('../views/user/MyAppointmentView.vue'), meta: { roles: ['USER'] } },
      { path: 'shop', component: () => import('../views/user/ProductListView.vue'), meta: { roles: ['USER'] } },
      { path: 'shop/cart', component: () => import('../views/user/ShoppingCartView.vue'), meta: { roles: ['USER'] } },
      { path: 'shop/addresses', redirect: '/profile', meta: { roles: ['USER'] } },
      { path: 'shop/orders', component: () => import('../views/user/MyProductOrderView.vue'), meta: { roles: ['USER'] } },
      { path: 'shop/orders/:id', component: () => import('../views/user/ProductOrderDetailView.vue'), meta: { roles: ['USER'] } },
      { path: 'shop/:id', component: () => import('../views/user/ProductDetailView.vue'), meta: { roles: ['USER'] } },
      { path: 'chat', component: () => import('../views/chat/ChatView.vue'), meta: { roles: ['USER', 'TECH', 'ADMIN'] } },
      { path: 'tech/dashboard', component: () => import('../views/tech/TechDashboardView.vue'), meta: { roles: ['TECH'] } },
      { path: 'tech/appointments', component: () => import('../views/tech/TechAppointmentView.vue'), meta: { roles: ['TECH'] } },
      { path: 'tech/leaves', component: () => import('../views/tech/TechLeaveView.vue'), meta: { roles: ['TECH'] } },
      { path: 'admin/dashboard', component: () => import('../views/admin/AdminDashboardView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/catalog', component: () => import('../views/admin/AdminProductOperationsView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/content', component: () => import('../views/admin/AdminContentOperationsView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/service', component: () => import('../views/admin/AdminServiceOperationsView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/master', redirect: { path: '/admin/service' }, meta: { roles: ['ADMIN'] } },
      { path: 'admin/users', component: () => import('../views/admin/AdminUserManagementView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/comments', component: () => import('../views/admin/AdminCommentManagementView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/banners', redirect: { path: '/admin/content', query: { tab: 'banners' } }, meta: { roles: ['ADMIN'] } },
      { path: 'admin/news', redirect: { path: '/admin/content', query: { tab: 'news' } }, meta: { roles: ['ADMIN'] } },
      { path: 'admin/appointments', component: () => import('../views/admin/AdminAppointmentView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/schedules', component: () => import('../views/admin/AdminScheduleView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/leaves', component: () => import('../views/admin/AdminLeaveView.vue'), meta: { roles: ['ADMIN'] } },
      { path: 'admin/product-categories', redirect: { path: '/admin/catalog', query: { tab: 'categories' } }, meta: { roles: ['ADMIN'] } },
      { path: 'admin/products', redirect: { path: '/admin/catalog', query: { tab: 'products' } }, meta: { roles: ['ADMIN'] } },
      { path: 'admin/product-orders', redirect: { path: '/admin/catalog', query: { tab: 'orders' } }, meta: { roles: ['ADMIN'] } },
      { path: 'admin/recharge-activities', component: () => import('../views/admin/AdminRechargeActivityView.vue'), meta: { roles: ['ADMIN'] } },
    ],
  },
];

const router = createRouter({
  history: createWebHistory(),
  routes,
});

router.beforeEach(async (to) => {
  const auth = useAuthStore();

  if (auth.token && !auth.user) {
    try {
      await auth.loadMe();
    } catch {
      auth.logout();
    }
  }

  if (to.meta.guestOnly && auth.token) {
    return auth.defaultRoute;
  }

  if (to.path !== '/login' && to.path !== '/register' && !auth.token) {
    return '/login';
  }

  const roles = to.meta.roles as string[] | undefined;
  if (roles && auth.user && !roles.includes(auth.user.roleCode)) {
    return auth.defaultRoute;
  }

  return true;
});

export default router;
