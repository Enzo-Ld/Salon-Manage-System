import { defineStore } from 'pinia';
import { api } from '../api/modules';

export const useNotificationStore = defineStore('notifications', {
  state: () => ({
    unreadCount: 0,
    pollingTimer: null as number | null,
  }),
  actions: {
    async refreshUnreadCount() {
      try {
        const { data } = await api.notificationUnreadCount();
        this.unreadCount = data.data || 0;
      } catch {
        this.unreadCount = 0;
      }
    },
    startPolling() {
      this.stopPolling();
      void this.refreshUnreadCount();
      this.pollingTimer = window.setInterval(() => {
        void this.refreshUnreadCount();
      }, 30000);
    },
    stopPolling() {
      if (this.pollingTimer) {
        window.clearInterval(this.pollingTimer);
        this.pollingTimer = null;
      }
    },
    clear() {
      this.stopPolling();
      this.unreadCount = 0;
    },
  },
});
