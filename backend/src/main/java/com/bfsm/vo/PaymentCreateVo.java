package com.bfsm.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class PaymentCreateVo {
    private String paymentNo;
    private String businessType;
    private Long businessId;
    private String payChannel;
    private String payMode;
    private BigDecimal amount;
    private String status;
    private String statusLabel;
    private String subject;
    private String checkoutType;
    private String payUrl;
    private String qrCodeText;
    private String formHtml;
    private LocalDateTime expireTime;
}
