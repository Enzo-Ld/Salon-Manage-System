package com.bfsm.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("payment_refund")
public class PaymentRefund {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long paymentOrderId;
    private String refundNo;
    private BigDecimal amount;
    private String status;
    private String reason;
    private String refundChannel;
    private String refundMode;
    private String platformRefundNo;
    private String notifyContent;
    private LocalDateTime refundedTime;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
