package com.bfsm.service;

import com.bfsm.dto.AutoScheduleReq;
import com.bfsm.entity.Schedule;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.LeaveRecordMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.vo.AutoScheduleDayVo;
import com.bfsm.vo.AutoSchedulePreviewVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AutoScheduleServiceTest {
    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private ScheduleMapper scheduleMapper;
    @Mock
    private LeaveRecordMapper leaveRecordMapper;
    @Mock
    private AppointmentMapper appointmentMapper;

    private AutoScheduleService autoScheduleService;

    @BeforeEach
    void setUp() {
        autoScheduleService = new AutoScheduleService(technicianMapper, scheduleMapper, leaveRecordMapper, appointmentMapper);
    }

    @Test
    void preview_shouldRotateStableOrderAcrossAdjacentWeeks() {
        when(technicianMapper.selectList(any())).thenReturn(technicians(5));
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        AutoSchedulePreviewVo firstWeek = autoScheduleService.preview(req(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 22), 2));
        AutoSchedulePreviewVo secondWeek = autoScheduleService.preview(req(LocalDate.of(2026, 5, 25), LocalDate.of(2026, 5, 29), 2));

        List<Long> firstMonday = technicianIds(firstWeek.getDays().get(0));
        List<Long> secondMonday = technicianIds(secondWeek.getDays().get(0));

        assertFalse(firstMonday.equals(secondMonday));
        assertIterableEquals(firstMonday, technicianIds(autoScheduleService.preview(req(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 22), 2)).getDays().get(0)));
    }

    @Test
    void preview_shouldBalanceGeneratedLoadWithinRange() {
        when(technicianMapper.selectList(any())).thenReturn(technicians(5));
        when(scheduleMapper.selectList(any())).thenReturn(List.of());

        AutoSchedulePreviewVo preview = autoScheduleService.preview(req(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 22), 2));

        Map<Long, Long> counts = preview.getDays().stream()
                .flatMap(day -> day.getItems().stream())
                .collect(Collectors.groupingBy(item -> item.getTechnicianId(), Collectors.counting()));

        assertEquals(5, counts.size());
        assertTrue(counts.values().stream().allMatch(count -> count == 2L));
    }

    @Test
    void preview_shouldPreferTechniciansWithLowerRecentHistoryLoad() {
        when(technicianMapper.selectList(any())).thenReturn(technicians(3));
        when(scheduleMapper.selectList(any())).thenReturn(List.of(
                schedule(1L, LocalDate.of(2026, 5, 1)),
                schedule(1L, LocalDate.of(2026, 5, 2)),
                schedule(1L, LocalDate.of(2026, 5, 3)),
                schedule(1L, LocalDate.of(2026, 5, 4))
        ));

        AutoSchedulePreviewVo preview = autoScheduleService.preview(req(LocalDate.of(2026, 5, 18), LocalDate.of(2026, 5, 18), 2));

        List<Long> selected = technicianIds(preview.getDays().get(0));
        assertFalse(selected.contains(1L));
        assertTrue(selected.containsAll(List.of(2L, 3L)));
    }

    @Test
    void preview_shouldSkipLeaveScheduleAndAppointmentConflicts() {
        LocalDate date = LocalDate.of(2026, 5, 18);
        when(technicianMapper.selectList(any())).thenReturn(technicians(4));
        when(scheduleMapper.selectList(any())).thenReturn(List.of());
        when(leaveRecordMapper.countApprovedOverlap(eq(1L), any(), any(), isNull())).thenReturn(1L);
        when(scheduleMapper.countOverlap(eq(2L), eq(date), eq(LocalTime.of(9, 0)), eq(LocalTime.of(18, 0)), isNull())).thenReturn(1L);
        when(appointmentMapper.countConflictInRange(eq(3L), any(), any())).thenReturn(1L);

        AutoSchedulePreviewVo preview = autoScheduleService.preview(req(date, date, 3));

        AutoScheduleDayVo day = preview.getDays().get(0);
        assertEquals(List.of(4L), technicianIds(day));
        assertEquals(1, day.getGeneratedCount());
        assertEquals(2, day.getShortageCount());
    }

    private AutoScheduleReq req(LocalDate startDate, LocalDate endDate, int dailyCount) {
        AutoScheduleReq req = new AutoScheduleReq();
        req.setStartDate(startDate);
        req.setEndDate(endDate);
        req.setWeekdays(List.of(1, 2, 3, 4, 5));
        req.setStartTime(LocalTime.of(9, 0));
        req.setEndTime(LocalTime.of(18, 0));
        req.setDailyTechnicianCount(dailyCount);
        req.setOverwriteExisting(false);
        return req;
    }

    private List<Technician> technicians(int count) {
        return java.util.stream.LongStream.rangeClosed(1, count)
                .mapToObj(id -> technician(id, "Tech " + id))
                .toList();
    }

    private Technician technician(Long id, String name) {
        Technician technician = new Technician();
        technician.setId(id);
        technician.setName(name);
        technician.setScore(BigDecimal.valueOf(5.0));
        technician.setStatus("ACTIVE");
        return technician;
    }

    private Schedule schedule(Long technicianId, LocalDate workDate) {
        Schedule schedule = new Schedule();
        schedule.setTechnicianId(technicianId);
        schedule.setWorkDate(workDate);
        schedule.setStartTime(LocalTime.of(9, 0));
        schedule.setEndTime(LocalTime.of(18, 0));
        schedule.setStatus("WORKING");
        return schedule;
    }

    private List<Long> technicianIds(AutoScheduleDayVo day) {
        return day.getItems().stream().map(item -> item.getTechnicianId()).toList();
    }
}
