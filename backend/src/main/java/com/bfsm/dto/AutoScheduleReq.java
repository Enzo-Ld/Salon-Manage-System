package com.bfsm.dto;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

@Data
public class AutoScheduleReq {
    private List<Long> technicianIds;
    private LocalDate startDate;
    private LocalDate endDate;
    private List<Integer> weekdays;
    private LocalTime startTime;
    private LocalTime endTime;
    private Integer dailyTechnicianCount;
    private Boolean overwriteExisting;
}
