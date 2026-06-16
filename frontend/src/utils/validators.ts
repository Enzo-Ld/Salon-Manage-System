export const MAINLAND_PHONE_REGEX = /^1\d{10}$/;

export function normalizePhone(phone?: string | null) {
  const normalized = phone?.trim() ?? '';
  return normalized || '';
}

export function isValidMainlandPhone(phone?: string | null) {
  const normalized = normalizePhone(phone);
  return !normalized || MAINLAND_PHONE_REGEX.test(normalized);
}
