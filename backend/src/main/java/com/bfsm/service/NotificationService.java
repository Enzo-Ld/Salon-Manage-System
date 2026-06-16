package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.SysUser;
import com.bfsm.entity.SystemNotification;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.SystemNotificationMapper;
import com.bfsm.vo.PageResultVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Service
public class NotificationService {
    private static final String UNREAD = "UNREAD";
    private static final String READ = "READ";
    private static final long DEFAULT_PAGE_NUM = 1L;
    private static final long DEFAULT_PAGE_SIZE = 10L;
    private static final long MAX_PAGE_SIZE = 50L;

    private final SystemNotificationMapper systemNotificationMapper;
    private final SysUserMapper sysUserMapper;

    public NotificationService(SystemNotificationMapper systemNotificationMapper, SysUserMapper sysUserMapper) {
        this.systemNotificationMapper = systemNotificationMapper;
        this.sysUserMapper = sysUserMapper;
    }

    public PageResultVo<SystemNotification> listCurrentUser(Long pageNum, Long pageSize, String readStatus, String keyword) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        Page<SystemNotification> page = new Page<>(
                normalizePageNum(pageNum),
                normalizePageSize(pageSize)
        );

        LambdaQueryWrapper<SystemNotification> query = new LambdaQueryWrapper<SystemNotification>()
                .eq(SystemNotification::getUserId, userId);
        String normalizedReadStatus = normalizeReadStatus(readStatus);
        if (normalizedReadStatus != null) {
            query.eq(SystemNotification::getReadStatus, normalizedReadStatus);
        }
        if (keyword != null && !keyword.isBlank()) {
            String normalizedKeyword = keyword.trim();
            query.and(wrapper -> wrapper.like(SystemNotification::getTitle, normalizedKeyword)
                    .or()
                    .like(SystemNotification::getContent, normalizedKeyword)
                    .or()
                    .like(SystemNotification::getCategory, normalizedKeyword));
        }
        query.orderByDesc(SystemNotification::getReadStatus)
                .orderByDesc(SystemNotification::getCreatedAt)
                .orderByDesc(SystemNotification::getId);

        return PageResultVo.from(systemNotificationMapper.selectPage(page, query));
    }

    public long unreadCountCurrentUser() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        return systemNotificationMapper.selectCount(new LambdaQueryWrapper<SystemNotification>()
                .eq(SystemNotification::getUserId, userId)
                .eq(SystemNotification::getReadStatus, UNREAD));
    }

    @Transactional
    public void markRead(Long id) {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        SystemNotification notification = systemNotificationMapper.selectById(id);
        if (notification == null) {
            throw new BizException(4601, "Notification not found");
        }
        if (!userId.equals(notification.getUserId())) {
            throw new BizException(403, "No permission to read this notification");
        }
        if (READ.equals(notification.getReadStatus())) {
            return;
        }
        notification.setReadStatus(READ);
        notification.setReadAt(LocalDateTime.now());
        systemNotificationMapper.updateById(notification);
    }

    @Transactional
    public void markAllRead() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        List<SystemNotification> notifications = systemNotificationMapper.selectList(new LambdaQueryWrapper<SystemNotification>()
                .eq(SystemNotification::getUserId, userId)
                .eq(SystemNotification::getReadStatus, UNREAD));
        LocalDateTime now = LocalDateTime.now();
        for (SystemNotification notification : notifications) {
            notification.setReadStatus(READ);
            notification.setReadAt(now);
            systemNotificationMapper.updateById(notification);
        }
    }

    @Transactional
    public void clearRead() {
        Long userId = SecurityUtils.currentUserOrThrow().getUserId();
        systemNotificationMapper.delete(new LambdaQueryWrapper<SystemNotification>()
                .eq(SystemNotification::getUserId, userId)
                .eq(SystemNotification::getReadStatus, READ));
    }

    @Transactional
    public void notifyUser(Long userId, String category, String title, String content, String targetPath) {
        if (userId == null) {
            return;
        }
        SysUser user = sysUserMapper.selectById(userId);
        if (user == null || user.getEnabled() == null || user.getEnabled() == 0) {
            return;
        }

        SystemNotification notification = new SystemNotification();
        notification.setUserId(userId);
        notification.setCategory(category);
        notification.setTitle(title);
        notification.setContent(content);
        notification.setTargetPath(targetPath);
        notification.setReadStatus(UNREAD);
        systemNotificationMapper.insert(notification);
    }

    @Transactional
    public void notifyUsers(Collection<Long> userIds, String category, String title, String content, String targetPath) {
        if (userIds == null || userIds.isEmpty()) {
            return;
        }
        Set<Long> distinctIds = new LinkedHashSet<>(userIds);
        for (Long userId : distinctIds) {
            notifyUser(userId, category, title, content, targetPath);
        }
    }

    @Transactional
    public void notifyAdmins(String category, String title, String content, String targetPath) {
        notifyUsers(sysUserMapper.selectIdsByRoleCode("ADMIN"), category, title, content, targetPath);
    }

    private long normalizePageNum(Long pageNum) {
        return pageNum == null || pageNum < 1 ? DEFAULT_PAGE_NUM : pageNum;
    }

    private long normalizePageSize(Long pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private String normalizeReadStatus(String readStatus) {
        if (readStatus == null || readStatus.isBlank()) {
            return null;
        }
        String normalized = readStatus.trim().toUpperCase();
        if (UNREAD.equals(normalized) || READ.equals(normalized)) {
            return normalized;
        }
        throw new BizException(4602, "Unsupported readStatus");
    }
}
