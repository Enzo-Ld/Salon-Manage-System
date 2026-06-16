package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;

@Data
public class AutoScheduleItemVo {
    private Long technicianId;
    private String technicianName;
    private LocalDate workDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private String status;
    private Long existingId;
    private Boolean hasLeaveConflict;
    private Boolean hasExistingScheduleConflict;
    private String reason;
}
