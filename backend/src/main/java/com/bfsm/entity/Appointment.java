package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("appointment")
public class Appointment {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private Long technicianId;
    private Long serviceItemId;
    private LocalDateTime appointmentTime;
    private LocalDateTime appointmentEndTime;
    private Integer durationMinSnapshot;
    private String status;
    private String payStatus;
    private String payMethod;
    private LocalDateTime payTime;
    private String payNo;
    private BigDecimal amount;
    private String assignMode;
    private String commentText;
    private LocalDateTime cancelTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

