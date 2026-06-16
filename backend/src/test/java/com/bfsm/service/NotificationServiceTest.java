package com.bfsm.service;

import com.bfsm.common.BizException;
import com.bfsm.entity.SystemNotification;
import com.bfsm.entity.SysUser;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.SystemNotificationMapper;
import com.bfsm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {
    @Mock
    private SystemNotificationMapper systemNotificationMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    private NotificationService notificationService;

    @BeforeEach
    void setUp() {
        notificationService = new NotificationService(systemNotificationMapper, sysUserMapper);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void notifyAdmins_shouldSendToAllEnabledAdmins() {
        when(sysUserMapper.selectIdsByRoleCode("ADMIN")).thenReturn(List.of(1L, 2L));
        when(sysUserMapper.selectById(anyLong())).thenAnswer(invocation -> enabledUser(invocation.getArgument(0)));

        notificationService.notifyAdmins("ORDER", "title", "content", "/admin/product-orders");

        verify(systemNotificationMapper, times(2)).insert(any(SystemNotification.class));
    }

    @Test
    void markRead_shouldRejectOtherUsersNotification() {
        setAuthentication(10L, "user", "USER");
        SystemNotification notification = new SystemNotification();
        notification.setId(1L);
        notification.setUserId(99L);
        notification.setReadStatus("UNREAD");
        when(systemNotificationMapper.selectById(1L)).thenReturn(notification);

        BizException exception = assertThrows(BizException.class, () -> notificationService.markRead(1L));

        assertEquals(403, exception.getCode());
    }

    private SysUser enabledUser(Long id) {
        SysUser user = new SysUser();
        user.setId(id);
        user.setUsername("user_" + id);
        user.setEnabled(1);
        return user;
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
