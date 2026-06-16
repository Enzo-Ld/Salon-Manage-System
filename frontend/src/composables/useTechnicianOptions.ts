import type { AdminUser, Technician, TechnicianOption } from '../api/types';

export function buildTechnicianOptions(technicians: Technician[], users: AdminUser[] = []): TechnicianOption[] {
  const userMap = users.reduce<Record<number, AdminUser>>((acc, user) => {
    acc[user.id] = user;
    return acc;
  }, {});

  return technicians.map((technician) => {
    const user = userMap[technician.userId];
    return {
      technicianId: technician.id,
      technicianName: technician.name,
      userId: technician.userId,
      username: user?.username,
      nickname: user?.nickname,
      phone: user?.phone,
      levelName: technician.levelName,
      score: technician.score,
      status: technician.status,
      enabled: user?.enabled,
    };
  });
}

export function selectableTechnicianOptions(options: TechnicianOption[]) {
  return options.filter((option) => option.status === 'ACTIVE' && option.enabled !== 0);
}

export function technicianOptionLabel(option: TechnicianOption) {
  const accountName = option.nickname || option.username || `账号 #${option.userId}`;
  const phone = option.phone || '未填手机号';
  const level = option.levelName || '未设置等级';
  const status = option.status === 'ACTIVE' ? '在职' : '停用';
  return `${option.technicianName}（${accountName} / ${phone} / ${level} / ${status}）`;
}
