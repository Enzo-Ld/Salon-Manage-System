package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AppointmentAvailableSlotVo {
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private String label;
    private int availableTechnicianCount;
    private List<String> technicianNames;
    private Long recommendedTechnicianId;
    private String recommendedTechnicianName;
}
