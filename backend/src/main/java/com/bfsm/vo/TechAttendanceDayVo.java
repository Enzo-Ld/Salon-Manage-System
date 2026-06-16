package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechAttendanceDayVo {
    private String label;
    private String status;
    private String statusLabel;
    private String checkInTime;
    private String checkOutTime;
    private Integer checkInMinute;
    private Integer checkOutMinute;
}
