package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class LeaveAppointmentConflictVo {
    private Long appointmentId;
    private Long userId;
    private String username;
    private Long technicianId;
    private String technicianName;
    private Long serviceItemId;
    private String serviceItemName;
    private LocalDateTime appointmentTime;
    private LocalDateTime appointmentEndTime;
    private String status;
    private String payStatus;
    private List<TechnicianCandidateVo> candidates = new ArrayList<>();
}
