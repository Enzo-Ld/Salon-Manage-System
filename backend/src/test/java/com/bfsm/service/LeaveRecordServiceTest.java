package com.bfsm.service;

import com.bfsm.common.BizException;
import com.bfsm.entity.LeaveRecord;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.LeaveRecordMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.SysUserMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class LeaveRecordServiceTest {
    @Mock
    private LeaveRecordMapper leaveRecordMapper;
    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private NotificationService notificationService;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private ServiceItemMapper serviceItemMapper;
    @Mock
    private SysUserMapper sysUserMapper;

    private LeaveRecordService leaveRecordService;

    @BeforeEach
    void setUp() {
        leaveRecordService = new LeaveRecordService(
                leaveRecordMapper,
                technicianMapper,
                appointmentMapper,
                notificationService,
                scheduleMapper,
                serviceItemMapper,
                sysUserMapper
        );
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void save_shouldCreatePendingLeaveRequestForTechnician() {
        setAuthentication(2L, "tech_amy", "TECH");
        when(technicianMapper.selectOne(any())).thenReturn(technician(5L, 2L));
        when(leaveRecordMapper.selectById(88L)).thenReturn(storedLeave(88L, "PENDING"));

        LeaveRecord request = draftLeave();
        when(leaveRecordMapper.insert(org.mockito.ArgumentMatchers.<LeaveRecord>any())).thenAnswer(invocation -> {
            LeaveRecord record = invocation.getArgument(0);
            record.setId(88L);
            return 1;
        });

        LeaveRecord saved = leaveRecordService.save(request);

        ArgumentCaptor<LeaveRecord> captor = ArgumentCaptor.forClass(LeaveRecord.class);
        verify(leaveRecordMapper).insert(captor.capture());
        LeaveRecord inserted = captor.getValue();
        assertEquals("PENDING", inserted.getStatus());
        assertEquals(5L, inserted.getTechnicianId());
        assertEquals(2L, inserted.getCreatedByUserId());
        assertEquals("PENDING", saved.getStatus());
    }

    @Test
    void approve_shouldMarkPendingLeaveAsApproved() {
        setAuthentication(1L, "admin", "ADMIN");
        LeaveRecord record = storedLeave(12L, "PENDING");
        when(leaveRecordMapper.selectById(12L)).thenReturn(record, approvedLeave(12L));
        when(leaveRecordMapper.countApprovedOverlap(anyLong(), any(), any(), any())).thenReturn(0L);
        when(appointmentMapper.countConflictInRange(anyLong(), any(), any())).thenReturn(0L);

        LeaveRecord approved = leaveRecordService.approve(12L, "Approved by admin");

        assertEquals("APPROVED", record.getStatus());
        assertEquals(1L, record.getReviewedByUserId());
        assertNotNull(record.getReviewedAt());
        assertEquals("APPROVED", approved.getStatus());
        verify(leaveRecordMapper).updateById(record);
    }

    @Test
    void reject_shouldRequireRemark() {
        setAuthentication(1L, "admin", "ADMIN");
        when(leaveRecordMapper.selectById(12L)).thenReturn(storedLeave(12L, "PENDING"));

        BizException exception = assertThrows(BizException.class, () -> leaveRecordService.reject(12L, " "));

        assertEquals(4320, exception.getCode());
    }

    private LeaveRecord draftLeave() {
        LeaveRecord record = new LeaveRecord();
        record.setStartTime(LocalDateTime.of(2026, 4, 16, 9, 0));
        record.setEndTime(LocalDateTime.of(2026, 4, 16, 18, 0));
        record.setReason("Personal leave");
        return record;
    }

    private LeaveRecord storedLeave(Long id, String status) {
        LeaveRecord record = draftLeave();
        record.setId(id);
        record.setTechnicianId(5L);
        record.setCreatedByUserId(2L);
        record.setStatus(status);
        return record;
    }

    private LeaveRecord approvedLeave(Long id) {
        LeaveRecord record = storedLeave(id, "APPROVED");
        record.setReviewedByUserId(1L);
        record.setReviewedAt(LocalDateTime.of(2026, 4, 14, 18, 0));
        record.setReviewRemark("Approved by admin");
        return record;
    }

    private Technician technician(Long id, Long userId) {
        Technician technician = new Technician();
        technician.setId(id);
        technician.setUserId(userId);
        technician.setName("Amy");
        technician.setStatus("ACTIVE");
        return technician;
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
