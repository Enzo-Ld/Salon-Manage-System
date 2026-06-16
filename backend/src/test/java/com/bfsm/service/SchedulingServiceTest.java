package com.bfsm.service;

import com.bfsm.entity.Technician;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.ServiceItemMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.vo.TechnicianRecommendationVo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SchedulingServiceTest {
    @Mock
    private TechnicianMapper technicianMapper;
    @Mock
    private AppointmentMapper appointmentMapper;
    @Mock
    private ScheduleConflictChecker scheduleConflictChecker;
    @Mock
    private ServiceItemMapper serviceItemMapper;

    private SchedulingService schedulingService;

    @BeforeEach
    void setUp() {
        schedulingService = new SchedulingService(technicianMapper, appointmentMapper, scheduleConflictChecker, serviceItemMapper);
    }

    @Test
    void recommendTechnician_shouldPickBestAvailableCandidate() {
        LocalDateTime appointmentTime = LocalDateTime.of(2026, 4, 15, 10, 0);

        Technician amy = technician(1L, "Amy", 4.9);
        Technician bob = technician(2L, "Bob", 4.6);

        when(serviceItemMapper.selectById(3L)).thenReturn(serviceItem(3L, 60));
        when(technicianMapper.selectActiveByServiceItemId(3L)).thenReturn(List.of(amy, bob));
        when(scheduleConflictChecker.isWorkingDuring(1L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(true);
        when(scheduleConflictChecker.isWorkingDuring(2L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(true);
        when(scheduleConflictChecker.isOnLeaveDuring(1L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(false);
        when(scheduleConflictChecker.isOnLeaveDuring(2L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(false);
        when(scheduleConflictChecker.hasConflictInRange(1L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(false);
        when(scheduleConflictChecker.hasConflictInRange(2L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(false);
        when(appointmentMapper.countDayLoad(1L, appointmentTime.toLocalDate())).thenReturn(2L);
        when(appointmentMapper.countDayLoad(2L, appointmentTime.toLocalDate())).thenReturn(0L);

        TechnicianRecommendationVo recommendation = schedulingService.recommendTechnician(3L, appointmentTime);

        assertNotNull(recommendation);
        assertEquals(2L, recommendation.getTechnicianId());
        assertEquals("Bob", recommendation.getTechnicianName());
    }

    @Test
    void recommendTechnician_shouldSkipTechnicianOnLeave() {
        LocalDateTime appointmentTime = LocalDateTime.of(2026, 4, 15, 10, 0);

        Technician amy = technician(1L, "Amy", 5.0);
        Technician bob = technician(2L, "Bob", 4.5);

        when(serviceItemMapper.selectById(3L)).thenReturn(serviceItem(3L, 60));
        when(technicianMapper.selectActiveByServiceItemId(3L)).thenReturn(List.of(amy, bob));
        when(scheduleConflictChecker.isWorkingDuring(1L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(true);
        when(scheduleConflictChecker.isWorkingDuring(2L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(true);
        when(scheduleConflictChecker.isOnLeaveDuring(1L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(true);
        when(scheduleConflictChecker.isOnLeaveDuring(2L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(false);
        when(scheduleConflictChecker.hasConflictInRange(2L, appointmentTime, appointmentTime.plusMinutes(60))).thenReturn(false);
        when(appointmentMapper.countDayLoad(2L, appointmentTime.toLocalDate())).thenReturn(0L);

        TechnicianRecommendationVo recommendation = schedulingService.recommendTechnician(3L, appointmentTime);

        assertNotNull(recommendation);
        assertEquals(2L, recommendation.getTechnicianId());
    }

    private Technician technician(Long id, String name, double score) {
        Technician technician = new Technician();
        technician.setId(id);
        technician.setName(name);
        technician.setScore(BigDecimal.valueOf(score));
        technician.setStatus("ACTIVE");
        return technician;
    }

    private com.bfsm.entity.ServiceItem serviceItem(Long id, int durationMin) {
        com.bfsm.entity.ServiceItem item = new com.bfsm.entity.ServiceItem();
        item.setId(id);
        item.setDurationMin(durationMin);
        item.setStatus("ON");
        return item;
    }
}
