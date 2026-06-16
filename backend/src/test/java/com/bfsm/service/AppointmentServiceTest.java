package com.bfsm.service;

import com.bfsm.cache.RedisLockService;
import com.bfsm.common.BizException;
import com.bfsm.dto.CommentReq;
import com.bfsm.entity.Appointment;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentLogMapper;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.CommentMapper;
import com.bfsm.mapper.PaymentOrderMapper;
import com.bfsm.mapper.PaymentRefundMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AppointmentServiceTest {
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private AppointmentLogMapper appointmentLogMapper;
    @Mock
    private ServiceItemMapper serviceItemMapper;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private SchedulingService schedulingService;
    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private RedisLockService redisLockService;
    @Mock
    private ScheduleConflictChecker scheduleConflictChecker;
    @Mock
    private CommentMapper commentMapper;
    @Mock
    private SysUserMapper sysUserMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ChatService chatService;
    @Mock
    private WalletService walletService;
    @Mock
    private PaymentService paymentService;
    @Mock
    private PaymentOrderMapper paymentOrderMapper;
    @Mock
    private PaymentRefundMapper paymentRefundMapper;
    @Mock
    private TechnicianRatingService technicianRatingService;

    private AppointmentService appointmentService;

    @BeforeEach
    void setUp() {
        appointmentService = new AppointmentService(
                appointmentMapper,
                appointmentLogMapper,
                serviceItemMapper,
                scheduleMapper,
                schedulingService,
                technicianMapper,
                redisLockService,
                scheduleConflictChecker,
                commentMapper,
                sysUserMapper,
                notificationService,
                chatService,
                walletService,
                paymentService,
                paymentOrderMapper,
                paymentRefundMapper,
                technicianRatingService
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void moveStatus_shouldRejectIllegalTransition() {
        setAuthentication(1L, "admin", "ADMIN");

        Appointment appointment = new Appointment();
        appointment.setId(9L);
        appointment.setTechnicianId(2L);
        appointment.setServiceItemId(3L);
        appointment.setStatus("PENDING");
        appointment.setPayStatus("PAID");
        appointment.setAppointmentTime(LocalDateTime.of(2026, 4, 15, 10, 0));

        when(appointmentMapper.selectById(9L)).thenReturn(appointment);

        BizException exception = assertThrows(BizException.class, () -> appointmentService.moveStatus(9L, "COMPLETED", "非法流转"));
        assertEquals(4019, exception.getCode());
    }

    @Test
    void cancel_shouldRejectNonOwnerUser() {
        setAuthentication(10L, "user_x", "USER");

        Appointment appointment = new Appointment();
        appointment.setId(7L);
        appointment.setUserId(11L);
        appointment.setStatus("PENDING");

        when(appointmentMapper.selectById(7L)).thenReturn(appointment);

        BizException exception = assertThrows(BizException.class, () -> appointmentService.cancel(7L));
        assertEquals(403, exception.getCode());
    }

    @Test
    void submitComment_shouldRejectWhenAppointmentNotCompleted() {
        setAuthentication(4L, "user_tom", "USER");

        Appointment appointment = new Appointment();
        appointment.setId(5L);
        appointment.setUserId(4L);
        appointment.setStatus("IN_SERVICE");

        when(appointmentMapper.selectById(5L)).thenReturn(appointment);

        CommentReq req = new CommentReq();
        req.setRating(5);
        req.setContent("服务很好");

        BizException exception = assertThrows(BizException.class, () -> appointmentService.submitComment(5L, req));
        assertEquals(4027, exception.getCode());
    }

    @Test
    void moveStatus_shouldSendChatReminderWhenTechnicianApprovesAppointment() {
        setAuthentication(20L, "tech_amy", "TECH");

        Appointment appointment = new Appointment();
        appointment.setId(12L);
        appointment.setUserId(8L);
        appointment.setTechnicianId(6L);
        appointment.setServiceItemId(3L);
        appointment.setStatus("PENDING");
        appointment.setPayStatus("PAID");
        appointment.setAppointmentTime(LocalDateTime.of(2026, 4, 16, 10, 0));
        appointment.setAppointmentEndTime(LocalDateTime.of(2026, 4, 16, 11, 30));

        Technician technician = new Technician();
        technician.setId(6L);
        technician.setUserId(20L);

        when(appointmentMapper.selectById(12L)).thenReturn(appointment);
        when(technicianMapper.selectOne(any())).thenReturn(technician);
        when(technicianMapper.selectById(6L)).thenReturn(technician);
        when(redisLockService.tryLock(eq("lock:appointment:6:20260416"), any()))
                .thenReturn("token");

        appointmentService.moveStatus(12L, "APPROVED", "技师确认接单");

        verify(chatService).sendSystemMessage(eq(20L), eq(8L), contains("2026-04-16 10:00 - 2026-04-16 11:30"));
    }

    @Test
    void moveStatus_shouldNotSendChatReminderWhenAdminApprovesAppointment() {
        setAuthentication(1L, "admin", "ADMIN");

        Appointment appointment = new Appointment();
        appointment.setId(13L);
        appointment.setUserId(8L);
        appointment.setTechnicianId(6L);
        appointment.setServiceItemId(3L);
        appointment.setStatus("PENDING");
        appointment.setPayStatus("PAID");
        appointment.setAppointmentTime(LocalDateTime.of(2026, 4, 16, 14, 0));
        appointment.setAppointmentEndTime(LocalDateTime.of(2026, 4, 16, 15, 0));

        when(appointmentMapper.selectById(13L)).thenReturn(appointment);
        when(redisLockService.tryLock(eq("lock:appointment:6:20260416"), any()))
                .thenReturn("token");

        appointmentService.moveStatus(13L, "APPROVED", "管理员代为确认");

        verify(chatService, never()).sendSystemMessage(any(), any(), anyString());
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
