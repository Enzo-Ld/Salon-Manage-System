package com.bfsm.service.gateway;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentGatewayStatus {
    private String status;
    private String platformTradeNo;
    private LocalDateTime paidTime;
    private String notifyContent;
}
