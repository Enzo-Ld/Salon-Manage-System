package com.bfsm.service.gateway;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WechatPaymentNotifyResult {
    private String outTradeNo;
    private String transactionId;
    private String tradeState;
    private LocalDateTime successTime;
    private String rawPlaintext;
}
