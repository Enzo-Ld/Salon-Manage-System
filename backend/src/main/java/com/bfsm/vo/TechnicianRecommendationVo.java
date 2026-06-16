package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class TechnicianRecommendationVo {
    private Long technicianId;
    private String technicianName;
    private double score;
    private String reason;
    private LocalDateTime appointmentTime;
    private LocalDateTime appointmentEndTime;
    private Integer durationMinSnapshot;
}
