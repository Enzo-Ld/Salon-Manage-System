package com.bfsm.service;

import com.bfsm.common.BizException;
import com.bfsm.mapper.AppointmentMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.TechnicianMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ScheduleConflictChecker {
    private final TechnicianMapper technicianMapper;
    private final ScheduleMapper scheduleMapper;
    private final AppointmentMapper appointmentMapper;
    private final LeaveRecordService leaveRecordService;

    public ScheduleConflictChecker(TechnicianMapper technicianMapper, ScheduleMapper scheduleMapper,
                                   AppointmentMapper appointmentMapper, LeaveRecordService leaveRecordService) {
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.appointmentMapper = appointmentMapper;
        this.leaveRecordService = leaveRecordService;
    }

    public void validateTechnicianForAppointment(Long technicianId, Long serviceItemId,
                                                 LocalDateTime startTime, LocalDateTime endTime) {
        if (!supportsService(technicianId, serviceItemId)) {
            throw new BizException(4008, "Selected technician does not support this service item");
        }
        if (!isWorkingDuring(technicianId, startTime, endTime)) {
            throw new BizException(4009, "Selected technician is not working in this time range");
        }
        if (isOnLeaveDuring(technicianId, startTime, endTime)) {
            throw new BizException(4311, "Selected technician is on leave in this period");
        }
    }

    public boolean supportsService(Long technicianId, Long serviceItemId) {
        return technicianMapper.countServiceSupport(technicianId, serviceItemId) > 0;
    }

    public boolean isWorkingDuring(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return scheduleMapper.countWorkingRange(
                technicianId,
                startTime.toLocalDate(),
                startTime.toLocalTime(),
                endTime.toLocalTime()
        ) > 0;
    }

    public boolean isOnLeaveDuring(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return leaveRecordService.isOnLeaveDuring(technicianId, startTime, endTime);
    }

    public boolean hasConflictInRange(Long technicianId, LocalDateTime startTime, LocalDateTime endTime) {
        return appointmentMapper.countConflict(technicianId, startTime, endTime) > 0;
    }

    public boolean hasConflictInRangeExclude(Long technicianId, LocalDateTime startTime,
                                             LocalDateTime endTime, Long excludeId) {
        return appointmentMapper.countConflictExclude(technicianId, startTime, endTime, excludeId) > 0;
    }
}
