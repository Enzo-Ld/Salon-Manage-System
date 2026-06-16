import request from '../utils/request';
import type { ApiResponse, CurrentUser } from './types';

export function login(username: string, password: string) {
  return request.post<ApiResponse<{ accessToken: string; expiresInSeconds: number }>>('/api/auth/login', {
    username,
    password,
  });
}

export function register(payload: {
  username: string;
  password: string;
  nickname?: string;
  phone?: string;
  roleCode: 'USER';
}) {
  return request.post<ApiResponse<null>>('/api/auth/register', payload);
}

export function currentUser() {
  return request.get<ApiResponse<CurrentUser>>('/api/auth/me');
}

export function updateProfile(payload: {
  nickname?: string;
  phone?: string;
  avatarUrl?: string | null;
  currentPassword?: string;
  newPassword?: string;
}) {
  return request.put<ApiResponse<CurrentUser>>('/api/auth/profile', payload);
}

export function uploadAvatar(file: File) {
  const formData = new FormData();
  formData.append('file', file);
  return request.post<ApiResponse<string>>('/api/auth/avatar', formData, {
    headers: {
      'Content-Type': 'multipart/form-data',
    },
  });
}

export function logout() {
  return request.post<ApiResponse<null>>('/api/auth/logout');
}
