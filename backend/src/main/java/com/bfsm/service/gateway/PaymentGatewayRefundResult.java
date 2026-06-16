package com.bfsm.service.gateway;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class PaymentGatewayRefundResult {
    private String status;
    private String platformRefundNo;
    private String notifyContent;
    private LocalDateTime refundedTime;

    public static PaymentGatewayRefundResult success(String platformRefundNo, String notifyContent) {
        PaymentGatewayRefundResult result = new PaymentGatewayRefundResult();
        result.setStatus("SUCCESS");
        result.setPlatformRefundNo(platformRefundNo);
        result.setNotifyContent(notifyContent);
        result.setRefundedTime(LocalDateTime.now());
        return result;
    }

    public static PaymentGatewayRefundResult manualRequired(String notifyContent) {
        PaymentGatewayRefundResult result = new PaymentGatewayRefundResult();
        result.setStatus("MANUAL_REQUIRED");
        result.setNotifyContent(notifyContent);
        return result;
    }
}
