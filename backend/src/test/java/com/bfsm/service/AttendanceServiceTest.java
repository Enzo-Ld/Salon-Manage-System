package com.bfsm.service;

import com.bfsm.common.BizException;
import com.bfsm.entity.AttendanceRecord;
import com.bfsm.entity.Schedule;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AttendanceRecordMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.AttendanceSummaryVo;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {
    @Mock
    private AttendanceRecordMapper attendanceRecordMapper;
    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private LeaveRecordService leaveRecordService;

    private AttendanceService attendanceService;

    @BeforeEach
    void setUp() {
        attendanceService = new AttendanceService(attendanceRecordMapper, technicianMapper, scheduleMapper, leaveRecordService);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void checkIn_shouldRejectWhenTechnicianIsOnLeave() {
        setAuthentication(2L, "tech_amy", "TECH");
        Technician technician = technician(1L, 2L);
        when(technicianMapper.selectOne(any())).thenReturn(technician);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(1L)));
        when(leaveRecordService.isOnLeaveAt(eq(technician.getId()), any())).thenReturn(true);

        BizException exception = assertThrows(BizException.class, () -> attendanceService.checkIn());
        assertEquals(4402, exception.getCode());
    }

    @Test
    void checkOut_shouldCompleteOpenAttendance() {
        setAuthentication(2L, "tech_amy", "TECH");
        Technician technician = technician(1L, 2L);
        AttendanceRecord record = new AttendanceRecord();
        record.setId(8L);
        record.setTechnicianId(1L);
        record.setWorkDate(LocalDate.now());
        record.setCheckInTime(java.time.LocalDateTime.now().minusHours(3));
        record.setStatus("ON_DUTY");

        when(technicianMapper.selectOne(any())).thenReturn(technician);
        when(attendanceRecordMapper.selectByTechnicianAndDate(1L, LocalDate.now())).thenReturn(record);
        when(scheduleMapper.selectList(any())).thenReturn(List.of(schedule(1L)));
        when(leaveRecordService.isOnLeaveAt(eq(technician.getId()), any())).thenReturn(false);

        AttendanceSummaryVo summary = attendanceService.checkOut();

        assertNotNull(summary.getCheckOutTime());
        assertEquals("OFF_DUTY", summary.getAttendanceStatus());
        verify(attendanceRecordMapper).updateById(record);
    }

    private Technician technician(Long id, Long userId) {
        Technician technician = new Technician();
        technician.setId(id);
        technician.setUserId(userId);
        technician.setName("Amy");
        technician.setStatus("ACTIVE");
        return technician;
    }

    private Schedule schedule(Long technicianId) {
        Schedule schedule = new Schedule();
        schedule.setTechnicianId(technicianId);
        schedule.setWorkDate(LocalDate.now());
        schedule.setStartTime(java.time.LocalTime.of(9, 0));
        schedule.setEndTime(java.time.LocalTime.of(18, 0));
        schedule.setStatus("WORKING");
        return schedule;
    }

    private void setAuthentication(Long userId, String username, String roleCode) {
        SecurityUser principal = new SecurityUser(userId, username, "", roleCode, true);
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
