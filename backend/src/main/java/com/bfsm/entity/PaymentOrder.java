package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_order")
public class PaymentOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String businessType;
    private Long businessId;
    private String paymentNo;
    private String outTradeNo;
    private String payChannel;
    private String payMode;
    private BigDecimal amount;
    private String status;
    private String subject;
    private String bodyText;
    private String extraInfo;
    private String platformTradeNo;
    private String notifyContent;
    private String closeReason;
    private String failureReason;
    private LocalDateTime expireTime;
    private LocalDateTime paidTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
