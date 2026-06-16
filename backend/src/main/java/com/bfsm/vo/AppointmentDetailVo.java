package com.bfsm.vo;

import lombok.EqualsAndHashCode;
import lombok.Data;

import java.util.List;

@Data
@EqualsAndHashCode(callSuper = true)
public class AppointmentDetailVo extends AppointmentSummaryVo {
    private List<AppointmentLogVo> logs;
}
