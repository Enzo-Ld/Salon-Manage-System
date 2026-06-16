import type { PageResult } from '../api/types';

export function normalizePagePayload<T>(
  payload: PageResult<T> | T[] | null | undefined,
  pageNum = 1,
  pageSize = 10,
): PageResult<T> {
  if (payload && typeof payload === 'object' && Array.isArray((payload as PageResult<T>).records)) {
    const page = payload as PageResult<T>;
    return {
      records: Array.isArray(page.records) ? page.records : [],
      total: Number(page.total || 0),
      pageNum: Number(page.pageNum || pageNum),
      pageSize: Number(page.pageSize || pageSize),
      pages: Number(page.pages || 0),
    };
  }

  if (Array.isArray(payload)) {
    const safePageNum = Math.max(1, pageNum);
    const safePageSize = Math.max(1, pageSize);
    const total = payload.length;
    const pages = total === 0 ? 0 : Math.ceil(total / safePageSize);
    const currentPage = pages > 0 ? Math.min(safePageNum, pages) : safePageNum;
    const start = (currentPage - 1) * safePageSize;
    return {
      records: payload.slice(start, start + safePageSize),
      total,
      pageNum: currentPage,
      pageSize: safePageSize,
      pages,
    };
  }

  return {
    records: [],
    total: 0,
    pageNum,
    pageSize,
    pages: 0,
  };
}

export function isMissingPageEndpointError(error: unknown) {
  const message =
    (error as { response?: { data?: { message?: string } }; message?: string })?.response?.data?.message ||
    (error as { message?: string })?.message ||
    '';
  return typeof message === 'string' && message.includes('No static resource');
}
