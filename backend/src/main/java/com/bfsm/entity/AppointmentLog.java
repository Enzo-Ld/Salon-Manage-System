package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("appointment_log")
public class AppointmentLog {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long appointmentId;
    private String fromStatus;
    private String toStatus;
    private Long operatorUserId;
    private String remark;
    private LocalDateTime createdAt;
}

