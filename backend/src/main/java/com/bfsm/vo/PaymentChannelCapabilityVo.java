package com.bfsm.vo;

import lombok.Data;

@Data
public class PaymentChannelCapabilityVo {
    private String channel;
    private String label;
    private String payMode;
    private boolean enabled;
    private boolean externalCashier;
    private boolean qrCode;
    private boolean manualCompletion;
}
