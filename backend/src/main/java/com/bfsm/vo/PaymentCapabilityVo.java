package com.bfsm.vo;

import lombok.Data;

import java.util.List;

@Data
public class PaymentCapabilityVo {
    private String mode;
    private String gatewayLabel;
    private List<String> supportedChannels;
    private List<PaymentChannelCapabilityVo> channels;
    private boolean externalCashier;
    private boolean manualCompletionEnabled;
}
