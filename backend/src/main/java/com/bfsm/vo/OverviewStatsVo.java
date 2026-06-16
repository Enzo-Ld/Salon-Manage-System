package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OverviewStatsVo {
    private long totalAppointments;
    private long completedAppointments;
    private long cancelledAppointments;
    private long pendingAppointments;
    private double completionRate;
    private double cancelRate;
}
