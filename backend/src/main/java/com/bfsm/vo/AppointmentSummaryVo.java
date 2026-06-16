package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class AppointmentSummaryVo {
    private Long id;
    private Long userId;
    private String username;
    private Long technicianId;
    private Long technicianUserId;
    private String technicianName;
    private Long serviceItemId;
    private String serviceItemName;
    private LocalDateTime appointmentTime;
    private LocalDateTime appointmentEndTime;
    private Integer durationMinSnapshot;
    private String status;
    private String statusLabel;
    private String payStatus;
    private String payStatusLabel;
    private String payMethod;
    private String payMethodLabel;
    private LocalDateTime payTime;
    private String payNo;
    private BigDecimal amount;
    private String assignMode;
    private String assignModeLabel;
    private String latestPaymentNo;
    private String latestPaymentChannel;
    private String latestPaymentChannelLabel;
    private String latestPaymentStatus;
    private String latestPaymentStatusLabel;
    private LocalDateTime latestPaymentTime;
    private String nextActionHint;
    private String refundNo;
    private BigDecimal refundAmount;
    private String refundStatus;
    private String refundStatusLabel;
    private LocalDateTime refundTime;
    private boolean canPay;
    private String latestRemark;
    private boolean canCancel;
    private boolean canComment;
}
