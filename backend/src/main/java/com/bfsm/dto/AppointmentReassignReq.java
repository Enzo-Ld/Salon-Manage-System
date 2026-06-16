package com.bfsm.dto;

import lombok.Data;

@Data
public class AppointmentReassignReq {
    private Long technicianId;
    private String remark;
}
