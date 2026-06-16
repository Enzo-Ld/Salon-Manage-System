package com.bfsm.common;

import com.bfsm.vo.PageResultVo;

import java.util.List;

public final class PageUtils {
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 100L;

    private PageUtils() {
    }

    public static long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    public static long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public static <T> PageResultVo<T> paginate(List<T> source, Long pageNum, Long pageSize) {
        long current = normalizePageNum(pageNum);
        long size = normalizePageSize(pageSize);
        int total = source == null ? 0 : source.size();
        if (total == 0) {
            return new PageResultVo<>(List.of(), 0, current, size, 0);
        }

        int fromIndex = (int) ((current - 1) * size);
        if (fromIndex >= total) {
            long pages = (long) Math.ceil((double) total / size);
            return new PageResultVo<>(List.of(), total, current, size, pages);
        }

        int toIndex = Math.min(total, fromIndex + (int) size);
        long pages = (long) Math.ceil((double) total / size);
        return new PageResultVo<>(source.subList(fromIndex, toIndex), total, current, size, pages);
    }
}
