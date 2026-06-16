package com.bfsm.service.gateway;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class WechatRefundNotifyResult {
    private String refundNo;
    private String refundId;
    private String refundStatus;
    private LocalDateTime successTime;
    private String rawPlaintext;
}
