package com.bfsm.service.gateway;

import com.bfsm.common.BizException;
import com.bfsm.config.PaymentProperties;
import com.bfsm.entity.PaymentOrder;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.vo.PaymentCreateVo;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class WechatNativePaymentGateway implements PaymentGateway {
    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    public WechatNativePaymentGateway(PaymentProperties paymentProperties,
                                      ObjectMapper objectMapper,
                                      RestClient.Builder restClientBuilder) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
        this.restClientBuilder = restClientBuilder;
    }

    @Override
    public boolean supports(String mode) {
        return "WECHAT_NATIVE".equalsIgnoreCase(mode);
    }

    @Override
    public PaymentCreateVo create(PaymentOrder order) {
        ensureConfigured();
        try {
            String path = "/v3/pay/transactions/native";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("appid", wechat().getAppId());
            body.put("mchid", wechat().getMchId());
            body.put("description", truncate(order.getSubject(), 127));
            body.put("out_trade_no", order.getOutTradeNo());
            body.put("notify_url", wechat().getNotifyUrl());
            if (order.getExpireTime() != null) {
                body.put("time_expire", order.getExpireTime()
                        .atZone(ZoneId.systemDefault())
                        .toOffsetDateTime()
                        .toString());
            }
            body.put("amount", Map.of(
                    "total", toCents(order.getAmount()),
                    "currency", "CNY"
            ));
            String bodyText = objectMapper.writeValueAsString(body);
            String response = restClient()
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization("POST", path, bodyText))
                    .body(bodyText)
                    .retrieve()
                    .body(String.class);

            String codeUrl = objectMapper.readTree(response).path("code_url").asText(null);
            if (!hasText(codeUrl)) {
                throw new BizException(4720, "Wechat Native payment did not return code_url");
            }
            PaymentCreateVo vo = baseCreateVo(order);
            vo.setCheckoutType("QR_CODE");
            vo.setQrCodeText(codeUrl);
            return vo;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(4720, "Failed to create Wechat payment: " + ex.getMessage());
        }
    }

    @Override
    public PaymentGatewayStatus query(PaymentOrder order) {
        ensureConfigured();
        try {
            String path = "/v3/pay/transactions/out-trade-no/"
                    + urlEncode(order.getOutTradeNo()) + "?mchid=" + urlEncode(wechat().getMchId());
            String response = restClient()
                    .get()
                    .uri(path)
                    .header("Authorization", authorization("GET", path, ""))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            PaymentGatewayStatus status = new PaymentGatewayStatus();
            status.setPlatformTradeNo(node.path("transaction_id").asText(null));
            status.setNotifyContent(node.path("trade_state_desc").asText(null));
            String tradeState = node.path("trade_state").asText("");
            if ("SUCCESS".equals(tradeState)) {
                status.setStatus("SUCCESS");
                status.setPaidTime(parseWechatTime(node.path("success_time").asText(null)));
            } else if ("CLOSED".equals(tradeState) || "REVOKED".equals(tradeState)) {
                status.setStatus("CLOSED");
            } else if ("PAYERROR".equals(tradeState)) {
                status.setStatus("FAILED");
            } else {
                status.setStatus(order.getStatus());
            }
            return status;
        } catch (Exception ex) {
            return null;
        }
    }

    @Override
    public PaymentGatewayStatus close(PaymentOrder order) {
        ensureConfigured();
        try {
            String path = "/v3/pay/transactions/out-trade-no/"
                    + urlEncode(order.getOutTradeNo()) + "/close";
            String bodyText = objectMapper.writeValueAsString(Map.of("mchid", wechat().getMchId()));
            restClient()
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization("POST", path, bodyText))
                    .body(bodyText)
                    .retrieve()
                    .toBodilessEntity();
        } catch (Exception ignored) {
            // BFSM still closes the local payment and lets reconciliation repair late provider state.
        }
        PaymentGatewayStatus status = new PaymentGatewayStatus();
        status.setStatus("CLOSED");
        status.setNotifyContent("Wechat payment closed");
        return status;
    }

    @Override
    public PaymentGatewayRefundResult refund(PaymentOrder order, PaymentRefund refund) {
        ensureRefundConfigured();
        try {
            String path = "/v3/refund/domestic/refunds";
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("out_trade_no", order.getOutTradeNo());
            body.put("out_refund_no", refund.getRefundNo());
            body.put("reason", truncate(refund.getReason(), 80));
            if (hasText(wechat().getRefundNotifyUrl())) {
                body.put("notify_url", wechat().getRefundNotifyUrl());
            }
            body.put("amount", Map.of(
                    "refund", toCents(refund.getAmount()),
                    "total", toCents(order.getAmount()),
                    "currency", "CNY"
            ));
            String bodyText = objectMapper.writeValueAsString(body);
            String response = restClient()
                    .post()
                    .uri(path)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", authorization("POST", path, bodyText))
                    .body(bodyText)
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response);
            String status = node.path("status").asText("PROCESSING");
            PaymentGatewayRefundResult result = new PaymentGatewayRefundResult();
            result.setPlatformRefundNo(node.path("refund_id").asText(null));
            result.setNotifyContent(node.path("user_received_account").asText("Wechat refund accepted"));
            result.setRefundedTime(parseWechatTime(node.path("success_time").asText(null)));
            if ("SUCCESS".equals(status)) {
                result.setStatus("SUCCESS");
            } else if ("ABNORMAL".equals(status)) {
                result.setStatus("MANUAL_REQUIRED");
            } else if ("CLOSED".equals(status)) {
                result.setStatus("FAILED");
            } else {
                result.setStatus("PROCESSING");
            }
            return result;
        } catch (Exception ex) {
            return PaymentGatewayRefundResult.manualRequired("Wechat refund failed: " + ex.getMessage());
        }
    }

    public WechatPaymentNotifyResult parsePaymentNotify(String body, Map<String, String> headers) {
        String plaintext = verifyAndDecrypt(body, headers);
        try {
            JsonNode node = objectMapper.readTree(plaintext);
            WechatPaymentNotifyResult result = new WechatPaymentNotifyResult();
            result.setOutTradeNo(node.path("out_trade_no").asText(null));
            result.setTransactionId(node.path("transaction_id").asText(null));
            result.setTradeState(node.path("trade_state").asText(null));
            result.setSuccessTime(parseWechatTime(node.path("success_time").asText(null)));
            result.setRawPlaintext(plaintext);
            return result;
        } catch (Exception ex) {
            throw new BizException(4723, "Invalid Wechat payment notification");
        }
    }

    public WechatRefundNotifyResult parseRefundNotify(String body, Map<String, String> headers) {
        String plaintext = verifyAndDecrypt(body, headers);
        try {
            JsonNode node = objectMapper.readTree(plaintext);
            WechatRefundNotifyResult result = new WechatRefundNotifyResult();
            result.setRefundNo(node.path("out_refund_no").asText(null));
            result.setRefundId(node.path("refund_id").asText(null));
            result.setRefundStatus(node.path("refund_status").asText(null));
            result.setSuccessTime(parseWechatTime(node.path("success_time").asText(null)));
            result.setRawPlaintext(plaintext);
            return result;
        } catch (Exception ex) {
            throw new BizException(4723, "Invalid Wechat refund notification");
        }
    }

    private PaymentCreateVo baseCreateVo(PaymentOrder order) {
        PaymentCreateVo vo = new PaymentCreateVo();
        vo.setPaymentNo(order.getPaymentNo());
        vo.setBusinessType(order.getBusinessType());
        vo.setBusinessId(order.getBusinessId());
        vo.setPayChannel(order.getPayChannel());
        vo.setPayMode(order.getPayMode());
        vo.setAmount(order.getAmount());
        vo.setStatus(order.getStatus());
        vo.setStatusLabel("待支付");
        vo.setSubject(order.getSubject());
        vo.setExpireTime(order.getExpireTime());
        return vo;
    }

    private String verifyAndDecrypt(String body, Map<String, String> headers) {
        ensureRefundConfigured();
        String timestamp = header(headers, "Wechatpay-Timestamp");
        String nonce = header(headers, "Wechatpay-Nonce");
        String signature = header(headers, "Wechatpay-Signature");
        if (!hasText(timestamp) || !hasText(nonce) || !hasText(signature)) {
            throw new BizException(4722, "Wechat notification signature headers are missing");
        }
        try {
            Signature verifier = Signature.getInstance("SHA256withRSA");
            verifier.initVerify(loadPublicKey(wechat().getPlatformPublicKey()));
            verifier.update((timestamp + "\n" + nonce + "\n" + body + "\n").getBytes(StandardCharsets.UTF_8));
            if (!verifier.verify(Base64.getDecoder().decode(signature))) {
                throw new BizException(4722, "Wechat notification signature is invalid");
            }

            JsonNode resource = objectMapper.readTree(body).path("resource");
            byte[] cipherBytes = Base64.getDecoder().decode(resource.path("ciphertext").asText());
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(
                    Cipher.DECRYPT_MODE,
                    new SecretKeySpec(wechat().getApiV3Key().getBytes(StandardCharsets.UTF_8), "AES"),
                    new GCMParameterSpec(128, resource.path("nonce").asText().getBytes(StandardCharsets.UTF_8))
            );
            String associatedData = resource.path("associated_data").asText(null);
            if (associatedData != null) {
                cipher.updateAAD(associatedData.getBytes(StandardCharsets.UTF_8));
            }
            return new String(cipher.doFinal(cipherBytes), StandardCharsets.UTF_8);
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(4722, "Wechat notification decrypt failed: " + ex.getMessage());
        }
    }

    private String authorization(String method, String path, String body) throws Exception {
        long timestamp = System.currentTimeMillis() / 1000;
        String nonce = UUID.randomUUID().toString().replace("-", "");
        String message = method + "\n" + path + "\n" + timestamp + "\n" + nonce + "\n" + body + "\n";
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(loadPrivateKey(wechat().getMerchantPrivateKey()));
        signature.update(message.getBytes(StandardCharsets.UTF_8));
        String signed = Base64.getEncoder().encodeToString(signature.sign());
        return "WECHATPAY2-SHA256-RSA2048 mchid=\"" + wechat().getMchId()
                + "\",nonce_str=\"" + nonce
                + "\",signature=\"" + signed
                + "\",timestamp=\"" + timestamp
                + "\",serial_no=\"" + wechat().getMerchantSerialNo() + "\"";
    }

    private RestClient restClient() {
        return restClientBuilder.baseUrl(wechat().getGatewayUrl()).build();
    }

    private PrivateKey loadPrivateKey(String privateKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(normalizeKey(privateKey));
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(keyBytes));
    }

    private PublicKey loadPublicKey(String publicKey) throws Exception {
        byte[] keyBytes = Base64.getDecoder().decode(normalizeKey(publicKey));
        return KeyFactory.getInstance("RSA").generatePublic(new X509EncodedKeySpec(keyBytes));
    }

    private String normalizeKey(String key) {
        return key.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replace("-----BEGIN PUBLIC KEY-----", "")
                .replace("-----END PUBLIC KEY-----", "")
                .replaceAll("\\s+", "");
    }

    private String header(Map<String, String> headers, String name) {
        if (headers == null) {
            return null;
        }
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                return entry.getValue();
            }
        }
        return null;
    }

    private LocalDateTime parseWechatTime(String value) {
        if (!hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return OffsetDateTime.parse(value).toLocalDateTime();
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private int toCents(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0).intValueExact();
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private void ensureConfigured() {
        PaymentProperties.Wechat wechat = wechat();
        if (!hasText(wechat.getGatewayUrl())
                || !hasText(wechat.getAppId())
                || !hasText(wechat.getMchId())
                || !hasText(wechat.getMerchantSerialNo())
                || !hasText(wechat.getMerchantPrivateKey())
                || !hasText(wechat.getNotifyUrl())) {
            throw new BizException(4721, "Wechat Native gateway is not configured");
        }
    }

    private void ensureRefundConfigured() {
        ensureConfigured();
        PaymentProperties.Wechat wechat = wechat();
        if (!hasText(wechat.getApiV3Key()) || !hasText(wechat.getPlatformPublicKey())) {
            throw new BizException(4721, "Wechat APIv3 key or platform public key is not configured");
        }
    }

    private PaymentProperties.Wechat wechat() {
        return paymentProperties.getWechat();
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
