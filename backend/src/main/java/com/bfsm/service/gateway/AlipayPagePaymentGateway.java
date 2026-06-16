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

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.Signature;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Component
public class AlipayPagePaymentGateway implements PaymentGateway {
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final PaymentProperties paymentProperties;
    private final ObjectMapper objectMapper;

    public AlipayPagePaymentGateway(PaymentProperties paymentProperties, ObjectMapper objectMapper) {
        this.paymentProperties = paymentProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean supports(String mode) {
        return "ALIPAY_PAGE".equalsIgnoreCase(mode);
    }

    @Override
    public PaymentCreateVo create(PaymentOrder order) {
        ensureConfigured();
        try {
            Map<String, String> params = buildCommonParams("alipay.trade.page.pay");
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", order.getOutTradeNo());
            bizContent.put("total_amount", order.getAmount().toPlainString());
            bizContent.put("subject", order.getSubject());
            bizContent.put("body", order.getBodyText());
            bizContent.put("product_code", "FAST_INSTANT_TRADE_PAY");
            if (order.getExpireTime() != null) {
                bizContent.put("time_expire", order.getExpireTime().format(ALIPAY_TIME));
            }
            params.put("biz_content", objectMapper.writeValueAsString(bizContent));
            if (hasText(paymentProperties.getAlipay().getNotifyUrl())) {
                params.put("notify_url", paymentProperties.getAlipay().getNotifyUrl());
            }
            String returnUrl = buildReturnUrl(order);
            if (hasText(returnUrl)) {
                params.put("return_url", returnUrl);
            }
            params.put("sign", sign(params));

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
            vo.setCheckoutType("REDIRECT_FORM");
            vo.setPayUrl(paymentProperties.getAlipay().getGatewayUrl());
            vo.setQrCodeText(order.getOutTradeNo());
            vo.setExpireTime(order.getExpireTime());
            vo.setFormHtml(buildAutoSubmitForm(params));
            return vo;
        } catch (BizException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new BizException(4712, "Failed to create Alipay payment: " + ex.getMessage());
        }
    }

    @Override
    public PaymentGatewayStatus close(PaymentOrder order) {
        ensureConfigured();
        try {
            Map<String, String> params = buildCommonParams("alipay.trade.close");
            params.put("biz_content", objectMapper.writeValueAsString(Map.of("out_trade_no", order.getOutTradeNo())));
            params.put("sign", sign(params));
            RestClient.builder()
                    .baseUrl(paymentProperties.getAlipay().getGatewayUrl())
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toFormBody(params))
                    .retrieve()
                    .body(String.class);
        } catch (Exception ignored) {
            // Local close still protects the BFSM order lifecycle when a remote close request is unavailable.
        }
        PaymentGatewayStatus status = new PaymentGatewayStatus();
        status.setStatus("CLOSED");
        status.setNotifyContent("Alipay payment closed");
        return status;
    }

    @Override
    public PaymentGatewayRefundResult refund(PaymentOrder order, PaymentRefund refund) {
        ensureConfigured();
        try {
            Map<String, String> params = buildCommonParams("alipay.trade.refund");
            Map<String, Object> bizContent = new LinkedHashMap<>();
            bizContent.put("out_trade_no", order.getOutTradeNo());
            bizContent.put("refund_amount", refund.getAmount().toPlainString());
            bizContent.put("refund_reason", refund.getReason());
            bizContent.put("out_request_no", refund.getRefundNo());
            params.put("biz_content", objectMapper.writeValueAsString(bizContent));
            params.put("sign", sign(params));
            String response = RestClient.builder()
                    .baseUrl(paymentProperties.getAlipay().getGatewayUrl())
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toFormBody(params))
                    .retrieve()
                    .body(String.class);
            JsonNode node = objectMapper.readTree(response).path("alipay_trade_refund_response");
            if ("10000".equals(node.path("code").asText())) {
                return PaymentGatewayRefundResult.success(
                        node.path("trade_no").asText(order.getPlatformTradeNo()),
                        node.path("msg").asText("Alipay refund accepted")
                );
            }
            return PaymentGatewayRefundResult.manualRequired(
                    node.path("sub_msg").asText(node.path("msg").asText("Alipay refund failed"))
            );
        } catch (Exception ex) {
            return PaymentGatewayRefundResult.manualRequired("Alipay refund failed: " + ex.getMessage());
        }
    }

    @Override
    public PaymentGatewayStatus query(PaymentOrder order) {
        ensureConfigured();
        try {
            Map<String, String> params = buildCommonParams("alipay.trade.query");
            params.put("biz_content", objectMapper.writeValueAsString(Map.of("out_trade_no", order.getOutTradeNo())));
            params.put("sign", sign(params));
            String response = RestClient.builder()
                    .baseUrl(paymentProperties.getAlipay().getGatewayUrl())
                    .build()
                    .post()
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .body(toFormBody(params))
                    .retrieve()
                    .body(String.class);

            if (!hasText(response)) {
                return null;
            }
            JsonNode root = objectMapper.readTree(response);
            JsonNode node = root.path("alipay_trade_query_response");
            if (node.isMissingNode() || !node.hasNonNull("code")) {
                return null;
            }
            String code = node.path("code").asText();
            if (!"10000".equals(code)) {
                return null;
            }

            PaymentGatewayStatus status = new PaymentGatewayStatus();
            status.setPlatformTradeNo(node.path("trade_no").asText(null));
            status.setNotifyContent(node.path("sub_msg").asText(node.path("msg").asText("")));
            String tradeStatus = node.path("trade_status").asText("");
            if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
                status.setStatus("SUCCESS");
                status.setPaidTime(parseAlipayTime(node.path("send_pay_date").asText("")));
            } else if ("TRADE_CLOSED".equals(tradeStatus)) {
                status.setStatus("CLOSED");
            } else {
                status.setStatus(order.getStatus());
            }
            return status;
        } catch (Exception ex) {
            return null;
        }
    }

    public boolean verifyNotify(Map<String, String> params) {
        ensureConfigured();
        String sign = params.get("sign");
        if (!hasText(sign)) {
            return false;
        }
        try {
            Map<String, String> contentMap = new TreeMap<>(params);
            contentMap.remove("sign");
            contentMap.remove("sign_type");
            Signature signature = Signature.getInstance("SHA256withRSA");
            signature.initVerify(loadPublicKey(paymentProperties.getAlipay().getAlipayPublicKey()));
            signature.update(joinToSignContent(contentMap).getBytes(StandardCharsets.UTF_8));
            return signature.verify(Base64.getDecoder().decode(sign));
        } catch (Exception ex) {
            return false;
        }
    }

    private Map<String, String> buildCommonParams(String method) {
        Map<String, String> params = new TreeMap<>();
        params.put("app_id", paymentProperties.getAlipay().getAppId());
        params.put("method", method);
        params.put("format", "JSON");
        params.put("charset", paymentProperties.getAlipay().getCharset());
        params.put("sign_type", paymentProperties.getAlipay().getSignType());
        params.put("timestamp", LocalDateTime.now().format(ALIPAY_TIME));
        params.put("version", "1.0");
        return params;
    }

    private String sign(Map<String, String> params) throws Exception {
        Signature signature = Signature.getInstance("SHA256withRSA");
        signature.initSign(loadPrivateKey(paymentProperties.getAlipay().getMerchantPrivateKey()));
        signature.update(joinToSignContent(params).getBytes(StandardCharsets.UTF_8));
        return Base64.getEncoder().encodeToString(signature.sign());
    }

    private String joinToSignContent(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> hasText(entry.getKey()) && hasText(entry.getValue()) && !"sign".equals(entry.getKey()))
                .map(entry -> entry.getKey() + "=" + entry.getValue())
                .collect(Collectors.joining("&"));
    }

    private String buildAutoSubmitForm(Map<String, String> params) {
        String inputs = params.entrySet().stream()
                .map(entry -> "<input type=\"hidden\" name=\"" + escapeHtml(entry.getKey()) + "\" value=\"" + escapeHtml(entry.getValue()) + "\"/>")
                .collect(Collectors.joining());
        return """
                <html>
                <body onload="document.forms[0].submit()">
                  <form method="post" action="%s">
                    %s
                  </form>
                </body>
                </html>
                """.formatted(escapeHtml(paymentProperties.getAlipay().getGatewayUrl()), inputs);
    }

    private String buildReturnUrl(PaymentOrder order) {
        String base = paymentProperties.getAlipay().getReturnUrl();
        if (!hasText(base)) {
            return null;
        }
        StringBuilder builder = new StringBuilder(base);
        builder.append(base.contains("?") ? "&" : "?");
        builder.append("paymentNo=").append(urlEncode(order.getPaymentNo()));
        builder.append("&businessType=").append(urlEncode(order.getBusinessType()));
        builder.append("&businessId=").append(urlEncode(String.valueOf(order.getBusinessId())));
        builder.append("&redirect=").append(urlEncode(resolveBusinessRedirect(order)));
        return builder.toString();
    }

    private String resolveBusinessRedirect(PaymentOrder order) {
        if ("PRODUCT_ORDER".equals(order.getBusinessType())) {
            return "/shop/orders/" + order.getBusinessId();
        }
        if ("APPOINTMENT".equals(order.getBusinessType())) {
            return "/appointments";
        }
        if ("WALLET_RECHARGE".equals(order.getBusinessType())) {
            return "/wallet";
        }
        return "/";
    }

    private String toFormBody(Map<String, String> params) {
        return params.entrySet().stream()
                .filter(entry -> hasText(entry.getValue()))
                .map(entry -> urlEncode(entry.getKey()) + "=" + urlEncode(entry.getValue()))
                .collect(Collectors.joining("&"));
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

    private LocalDateTime parseAlipayTime(String value) {
        if (!hasText(value)) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, ALIPAY_TIME);
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }

    private void ensureConfigured() {
        PaymentProperties.Alipay alipay = paymentProperties.getAlipay();
        if (!hasText(alipay.getAppId())
                || !hasText(alipay.getMerchantPrivateKey())
                || !hasText(alipay.getAlipayPublicKey())
                || !hasText(alipay.getGatewayUrl())) {
            throw new BizException(4713, "Alipay gateway is not configured");
        }
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value.replace("&", "&amp;")
                .replace("\"", "&quot;")
                .replace("<", "&lt;")
                .replace(">", "&gt;");
    }

    private String urlEncode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
