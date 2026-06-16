import axios from 'axios';
import { ElMessage } from 'element-plus';
import { useAuthStore } from '../store/auth';

export const API_BASE_URL = 'http://localhost:8080';

const request = axios.create({
  baseURL: API_BASE_URL,
  timeout: 15000,
});

request.interceptors.request.use((config) => {
  const store = useAuthStore();
  if (store.token) {
    config.headers.Authorization = `Bearer ${store.token}`;
  }
  return config;
});

request.interceptors.response.use(
  (response) => {
    const store = useAuthStore();
    const payload = response.data;
    if (payload && typeof payload.code === 'number' && payload.code !== 0) {
      if (payload.code === 401) {
        store.logout();
      }
      ElMessage.error(payload.message || '请求失败');
      return Promise.reject(new Error(payload.message || '请求失败'));
    }
    return response;
  },
  (error) => {
    const store = useAuthStore();
    const message = error?.response?.data?.message || error?.message || '网络异常';
    if (error?.response?.status === 401) {
      store.logout();
    }
    ElMessage.error(message);
    return Promise.reject(error);
  },
);

export default request;
