package com.bfsm.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.bfsm.common.BizException;
import com.bfsm.common.SecurityUtils;
import com.bfsm.entity.AttendanceRecord;
import com.bfsm.entity.Schedule;
import com.bfsm.entity.Technician;
import com.bfsm.mapper.AttendanceRecordMapper;
import com.bfsm.mapper.ScheduleMapper;
import com.bfsm.mapper.TechnicianMapper;
import com.bfsm.security.SecurityUser;
import com.bfsm.vo.AttendanceSummaryVo;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
public class AttendanceService {
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm");

    private final AttendanceRecordMapper attendanceRecordMapper;
    private final TechnicianMapper technicianMapper;
    private final ScheduleMapper scheduleMapper;
    private final LeaveRecordService leaveRecordService;

    public AttendanceService(AttendanceRecordMapper attendanceRecordMapper, TechnicianMapper technicianMapper,
                             ScheduleMapper scheduleMapper, LeaveRecordService leaveRecordService) {
        this.attendanceRecordMapper = attendanceRecordMapper;
        this.technicianMapper = technicianMapper;
        this.scheduleMapper = scheduleMapper;
        this.leaveRecordService = leaveRecordService;
    }

    public AttendanceSummaryVo todaySummary() {
        Technician technician = currentTechnicianOrThrow();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Schedule> schedules = loadTodaySchedules(technician.getId(), today);
        AttendanceRecord record = attendanceRecordMapper.selectByTechnicianAndDate(technician.getId(), today);
        boolean onLeaveNow = leaveRecordService.isOnLeaveAt(technician.getId(), now);
        return toSummary(technician, today, schedules, record, onLeaveNow);
    }

    public List<AttendanceRecord> recentRecords() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if ("ADMIN".equals(currentUser.getRoleCode())) {
            return attendanceRecordMapper.selectRecentAll(50);
        }
        Technician technician = currentTechnicianOrThrow();
        return attendanceRecordMapper.selectRecentByTechnician(technician.getId(), 14);
    }

    @Transactional
    public AttendanceSummaryVo checkIn() {
        Technician technician = currentTechnicianOrThrow();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        List<Schedule> schedules = loadTodaySchedules(technician.getId(), today);
        if (schedules.isEmpty()) {
            throw new BizException(4401, "No working schedule found for today");
        }
        if (leaveRecordService.isOnLeaveAt(technician.getId(), now)) {
            throw new BizException(4402, "Cannot check in while on approved leave");
        }

        AttendanceRecord record = attendanceRecordMapper.selectByTechnicianAndDate(technician.getId(), today);
        if (record != null) {
            if (record.getCheckOutTime() != null) {
                throw new BizException(4403, "You have already checked out today");
            }
            if (record.getCheckInTime() != null) {
                throw new BizException(4404, "You are already checked in");
            }
        } else {
            record = new AttendanceRecord();
            record.setTechnicianId(technician.getId());
            record.setWorkDate(today);
        }

        record.setCheckInTime(now);
        record.setStatus("ON_DUTY");
        record.setRemark("Checked in by technician");
        if (record.getId() == null) {
            attendanceRecordMapper.insert(record);
        } else {
            attendanceRecordMapper.updateById(record);
        }
        return toSummary(technician, today, schedules, record, false);
    }

    @Transactional
    public AttendanceSummaryVo checkOut() {
        Technician technician = currentTechnicianOrThrow();
        LocalDate today = LocalDate.now();
        LocalDateTime now = LocalDateTime.now();
        AttendanceRecord record = attendanceRecordMapper.selectByTechnicianAndDate(technician.getId(), today);
        if (record == null || record.getCheckInTime() == null) {
            throw new BizException(4405, "Please check in before checking out");
        }
        if (record.getCheckOutTime() != null) {
            throw new BizException(4406, "You have already checked out today");
        }

        record.setCheckOutTime(now);
        record.setStatus("OFF_DUTY");
        record.setRemark("Checked out by technician");
        attendanceRecordMapper.updateById(record);

        List<Schedule> schedules = loadTodaySchedules(technician.getId(), today);
        boolean onLeaveNow = leaveRecordService.isOnLeaveAt(technician.getId(), now);
        return toSummary(technician, today, schedules, record, onLeaveNow);
    }

    private AttendanceSummaryVo toSummary(Technician technician, LocalDate workDate, List<Schedule> schedules,
                                          AttendanceRecord record, boolean onLeaveNow) {
        AttendanceSummaryVo summary = new AttendanceSummaryVo();
        summary.setTechnicianId(technician.getId());
        summary.setTechnicianName(technician.getName());
        summary.setWorkDate(workDate);
        summary.setOnLeaveNow(onLeaveNow);
        summary.setHasScheduleToday(!schedules.isEmpty());
        summary.setScheduleWindows(schedules.stream()
                .map(item -> item.getStartTime().format(TIME_FORMATTER) + " - " + item.getEndTime().format(TIME_FORMATTER))
                .toList());

        if (record == null || record.getCheckInTime() == null) {
            summary.setAttendanceStatus(onLeaveNow ? "ON_LEAVE" : "NOT_CHECKED_IN");
            summary.setAttendanceStatusLabel(onLeaveNow ? "On Leave" : "Not Checked In");
            summary.setCanCheckIn(!onLeaveNow && !schedules.isEmpty());
            summary.setCanCheckOut(false);
            return summary;
        }

        summary.setCheckInTime(record.getCheckInTime());
        summary.setCheckOutTime(record.getCheckOutTime());
        if (record.getCheckOutTime() == null) {
            summary.setAttendanceStatus("ON_DUTY");
            summary.setAttendanceStatusLabel("On Duty");
            summary.setCanCheckIn(false);
            summary.setCanCheckOut(true);
            return summary;
        }

        summary.setAttendanceStatus("OFF_DUTY");
        summary.setAttendanceStatusLabel("Checked Out");
        summary.setCanCheckIn(false);
        summary.setCanCheckOut(false);
        return summary;
    }

    private Technician currentTechnicianOrThrow() {
        SecurityUser currentUser = SecurityUtils.currentUserOrThrow();
        if (!"TECH".equals(currentUser.getRoleCode())) {
            throw new BizException(403, "Only technicians can use attendance actions");
        }
        Technician technician = technicianMapper.selectOne(
                new LambdaQueryWrapper<Technician>().eq(Technician::getUserId, currentUser.getUserId())
        );
        if (technician == null) {
            throw new BizException(4410, "Technician profile not found");
        }
        return technician;
    }

    private List<Schedule> loadTodaySchedules(Long technicianId, LocalDate today) {
        return scheduleMapper.selectList(new LambdaQueryWrapper<Schedule>()
                .eq(Schedule::getTechnicianId, technicianId)
                .eq(Schedule::getWorkDate, today)
                .eq(Schedule::getStatus, "WORKING")
                .orderByAsc(Schedule::getStartTime));
    }
}
