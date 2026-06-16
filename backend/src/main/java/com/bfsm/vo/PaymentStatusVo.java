package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentStatusVo {
    private String paymentNo;
    private String businessType;
    private Long businessId;
    private String payChannel;
    private String payMode;
    private BigDecimal amount;
    private String status;
    private String statusLabel;
    private String subject;
    private String nextAction;
    private String notifyContent;
    private String closeReason;
    private String failureReason;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private boolean canComplete;
    private boolean canCancel;
}
