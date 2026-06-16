package com.bfsm.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Data
@Component
@ConfigurationProperties(prefix = "bfsm.payment")
public class PaymentProperties {
    private String mode = "MANUAL_CASHIER";
    private boolean manualCompletionEnabled = true;
    private Alipay alipay = new Alipay();
    private Wechat wechat = new Wechat();

    public String resolvedMode() {
        return mode == null || mode.isBlank() ? "MANUAL_CASHIER" : mode.trim().toUpperCase();
    }

    public List<String> supportedChannels() {
        List<String> channels = new ArrayList<>();
        if (resolveModeForChannel("WECHAT") != null) {
            channels.add("WECHAT");
        }
        if (resolveModeForChannel("ALIPAY") != null) {
            channels.add("ALIPAY");
        }
        return channels;
    }

    public boolean supportsChannel(String channel) {
        return resolveModeForChannel(channel) != null;
    }

    public String resolveModeForChannel(String channel) {
        if (channel == null || channel.isBlank()) {
            return null;
        }
        String normalizedChannel = channel.trim().toUpperCase();
        String configuredMode = resolvedMode();

        if ("MANUAL_CASHIER".equals(configuredMode)) {
            return ("WECHAT".equals(normalizedChannel) || "ALIPAY".equals(normalizedChannel))
                    ? "MANUAL_CASHIER" : null;
        }
        if ("ALIPAY_PAGE".equals(configuredMode)) {
            return "ALIPAY".equals(normalizedChannel) ? "ALIPAY_PAGE" : null;
        }
        if ("WECHAT_NATIVE".equals(configuredMode)) {
            return "WECHAT".equals(normalizedChannel) ? "WECHAT_NATIVE" : null;
        }

        if ("CHANNEL_ROUTER".equals(configuredMode)) {
            if ("WECHAT".equals(normalizedChannel) && wechat.isEnabled()) {
                return "WECHAT_NATIVE";
            }
            if ("ALIPAY".equals(normalizedChannel) && alipay.isEnabled()) {
                return "ALIPAY_PAGE";
            }
        }
        return null;
    }

    public boolean usesExternalCashier() {
        return supportedChannels().stream()
                .map(this::resolveModeForChannel)
                .anyMatch(mode -> "ALIPAY_PAGE".equals(mode));
    }

    public boolean usesExternalCashier(String mode) {
        return "ALIPAY_PAGE".equalsIgnoreCase(mode);
    }

    public boolean usesQrCode(String mode) {
        return "WECHAT_NATIVE".equalsIgnoreCase(mode) || "MANUAL_CASHIER".equalsIgnoreCase(mode);
    }

    public boolean allowsManualCompletion(String mode) {
        return manualCompletionEnabled && "MANUAL_CASHIER".equalsIgnoreCase(mode);
    }

    public String gatewayLabel() {
        if ("CHANNEL_ROUTER".equals(resolvedMode())) {
            return "按渠道路由支付";
        }
        if ("ALIPAY_PAGE".equalsIgnoreCase(resolvedMode())) {
            return "支付宝页面支付";
        }
        if ("WECHAT_NATIVE".equalsIgnoreCase(resolvedMode())) {
            return "微信支付 Native";
        }
        return "统一支付收银台";
    }

    @Data
    public static class Alipay {
        private boolean enabled = true;
        private String gatewayUrl = "https://openapi-sandbox.dl.alipaydev.com/gateway.do";
        private String appId;
        private String merchantPrivateKey;
        private String alipayPublicKey;
        private String notifyUrl;
        private String returnUrl;
        private String signType = "RSA2";
        private String charset = "UTF-8";
    }

    @Data
    public static class Wechat {
        private boolean enabled = true;
        private String gatewayUrl = "https://api.mch.weixin.qq.com";
        private String appId;
        private String mchId;
        private String merchantSerialNo;
        private String merchantPrivateKey;
        private String apiV3Key;
        private String platformPublicKey;
        private String notifyUrl;
        private String refundNotifyUrl;
    }
}
