import { defineStore } from 'pinia';
import { currentUser, login, logout as logoutApi } from '../api/auth';
import type { CurrentUser } from '../api/types';

export const useAuthStore = defineStore('auth', {
  state: () => ({
    token: localStorage.getItem('token') || '',
    user: null as CurrentUser | null,
  }),
  getters: {
    isAuthenticated: (state) => Boolean(state.token),
    defaultRoute: (state) => {
      switch (state.user?.roleCode) {
        case 'ADMIN':
          return '/admin/dashboard';
        case 'TECH':
          return '/tech/dashboard';
        default:
          return '/';
      }
    },
    displayName: (state) => state.user?.nickname || state.user?.username || '未登录用户',
  },
  actions: {
    async doLogin(username: string, password: string) {
      const { data } = await login(username, password);
      this.token = data.data.accessToken;
      localStorage.setItem('token', this.token);
      await this.loadMe();
    },
    async loadMe() {
      if (!this.token) return;
      const { data } = await currentUser();
      this.user = data.data;
    },
    setUser(user: CurrentUser | null) {
      this.user = user;
    },
    async doLogout() {
      if (this.token) {
        try {
          await logoutApi();
        } catch {
          // noop
        }
      }
      this.logout();
    },
    logout() {
      this.token = '';
      this.user = null;
      localStorage.removeItem('token');
    },
  },
});
