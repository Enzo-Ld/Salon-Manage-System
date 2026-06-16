package com.bfsm.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AppointmentLogVo {
    private Long id;
    private String fromStatus;
    private String fromStatusLabel;
    private String toStatus;
    private String toStatusLabel;
    private Long operatorUserId;
    private String operatorName;
    private String remark;
    private LocalDateTime createdAt;
}
