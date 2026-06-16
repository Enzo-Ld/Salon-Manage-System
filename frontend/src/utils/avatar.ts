import { API_BASE_URL } from './request';

type RoleCode = 'USER' | 'TECH' | 'ADMIN' | string | undefined | null;

function normalizePath(path?: string | null) {
  if (!path) {
    return '';
  }
  const normalized = path.trim();
  if (!normalized) {
    return '';
  }
  if (normalized.startsWith('/avatars/')) {
    return `${API_BASE_URL}/uploads${normalized}`;
  }
  if (normalized.startsWith('avatars/')) {
    return `${API_BASE_URL}/uploads/${normalized}`;
  }
  if (!normalized.includes('/')) {
    return `${API_BASE_URL}/uploads/avatars/${normalized}`;
  }
  if (normalized.startsWith('http://') || normalized.startsWith('https://') || normalized.startsWith('data:')) {
    return normalized;
  }
  return `${API_BASE_URL}${normalized.startsWith('/') ? normalized : `/${normalized}`}`;
}

function pickInitial(name?: string | null) {
  const value = (name || '').trim();
  if (!value) {
    return 'B';
  }
  return value[0]!.toUpperCase();
}

function palette(roleCode: RoleCode) {
  switch (roleCode) {
    case 'ADMIN':
      return { start: '#8e3b2f', end: '#d68b68', label: '管理员' };
    case 'TECH':
      return { start: '#18635b', end: '#74b59f', label: '技师' };
    default:
      return { start: '#7a5625', end: '#d7b16b', label: '用户' };
  }
}

function defaultAvatar(roleCode?: RoleCode, name?: string | null) {
  const { start, end, label } = palette(roleCode);
  const initial = pickInitial(name);
  const svg = `
    <svg xmlns="http://www.w3.org/2000/svg" width="160" height="160" viewBox="0 0 160 160">
      <defs>
        <linearGradient id="g" x1="0%" y1="0%" x2="100%" y2="100%">
          <stop offset="0%" stop-color="${start}" />
          <stop offset="100%" stop-color="${end}" />
        </linearGradient>
      </defs>
      <rect width="160" height="160" rx="40" fill="url(#g)" />
      <circle cx="80" cy="62" r="28" fill="rgba(255,255,255,0.92)" />
      <path d="M38 132c7-23 24-36 42-36s35 13 42 36" fill="rgba(255,255,255,0.92)" />
      <text x="80" y="74" text-anchor="middle" font-size="22" font-family="Arial, sans-serif" fill="${start}">${initial}</text>
      <text x="80" y="148" text-anchor="middle" font-size="13" font-family="Arial, sans-serif" fill="rgba(255,255,255,0.92)">${label}</text>
    </svg>
  `;
  return `data:image/svg+xml;charset=UTF-8,${encodeURIComponent(svg)}`;
}

export function resolveAvatar(avatarUrl?: string | null, roleCode?: RoleCode, name?: string | null) {
  return normalizePath(avatarUrl) || defaultAvatar(roleCode, name);
}

export function resolveAssetUrl(path?: string | null) {
  return normalizePath(path);
}
