package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class AttendanceSummaryVo {
    private Long technicianId;
    private String technicianName;
    private LocalDate workDate;
    private String attendanceStatus;
    private String attendanceStatusLabel;
    private LocalDateTime checkInTime;
    private LocalDateTime checkOutTime;
    private boolean onLeaveNow;
    private boolean hasScheduleToday;
    private boolean canCheckIn;
    private boolean canCheckOut;
    private List<String> scheduleWindows;
}
