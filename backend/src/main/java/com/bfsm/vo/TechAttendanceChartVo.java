package com.bfsm.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TechAttendanceChartVo {
    private String month;
    private Long onDutyCount;
    private Long offDutyCount;
    private Long leaveCount;
    private Long absentCount;
    private List<TechAttendanceDayVo> days;
}
