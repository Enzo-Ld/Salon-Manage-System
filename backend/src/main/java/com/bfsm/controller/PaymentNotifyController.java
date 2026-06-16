package com.bfsm.controller;

import com.bfsm.service.PaymentService;
import com.bfsm.service.gateway.AlipayPagePaymentGateway;
import com.bfsm.service.gateway.WechatNativePaymentGateway;
import com.bfsm.service.gateway.WechatPaymentNotifyResult;
import com.bfsm.service.gateway.WechatRefundNotifyResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/payments")
public class PaymentNotifyController {
    private static final DateTimeFormatter ALIPAY_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final AlipayPagePaymentGateway alipayPagePaymentGateway;
    private final WechatNativePaymentGateway wechatNativePaymentGateway;
    private final PaymentService paymentService;

    public PaymentNotifyController(AlipayPagePaymentGateway alipayPagePaymentGateway,
                                   WechatNativePaymentGateway wechatNativePaymentGateway,
                                   PaymentService paymentService) {
        this.alipayPagePaymentGateway = alipayPagePaymentGateway;
        this.wechatNativePaymentGateway = wechatNativePaymentGateway;
        this.paymentService = paymentService;
    }

    @PostMapping("/alipay/notify")
    public String alipayNotify(HttpServletRequest request) {
        Map<String, String> params = extractParams(request);
        if (!alipayPagePaymentGateway.verifyNotify(params)) {
            return "failure";
        }

        String outTradeNo = params.get("out_trade_no");
        String tradeStatus = params.get("trade_status");
        if ("TRADE_SUCCESS".equals(tradeStatus) || "TRADE_FINISHED".equals(tradeStatus)) {
            paymentService.handleGatewaySuccess(
                    outTradeNo,
                    params.get("trade_no"),
                    parseAlipayTime(params.get("gmt_payment")),
                    "支付宝回调已确认支付成功"
            );
            return "success";
        }
        if ("TRADE_CLOSED".equals(tradeStatus)) {
            paymentService.handleGatewayClosed(outTradeNo, "支付宝交易已关闭");
        }
        return "success";
    }

    @PostMapping("/wechat/notify")
    public ResponseEntity<Map<String, String>> wechatNotify(@RequestBody String body,
                                                           @RequestHeader Map<String, String> headers) {
        try {
            WechatPaymentNotifyResult notify = wechatNativePaymentGateway.parsePaymentNotify(body, headers);
            if ("SUCCESS".equals(notify.getTradeState())) {
                paymentService.handleGatewaySuccess(
                        notify.getOutTradeNo(),
                        notify.getTransactionId(),
                        notify.getSuccessTime(),
                        notify.getRawPlaintext()
                );
            } else if ("CLOSED".equals(notify.getTradeState()) || "REVOKED".equals(notify.getTradeState())) {
                paymentService.handleGatewayClosed(notify.getOutTradeNo(), notify.getRawPlaintext());
            }
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "FAIL", "message", ex.getMessage()));
        }
    }

    @PostMapping("/wechat/refund-notify")
    public ResponseEntity<Map<String, String>> wechatRefundNotify(@RequestBody String body,
                                                                 @RequestHeader Map<String, String> headers) {
        try {
            WechatRefundNotifyResult notify = wechatNativePaymentGateway.parseRefundNotify(body, headers);
            if ("SUCCESS".equals(notify.getRefundStatus())) {
                paymentService.handleGatewayRefundSuccess(
                        notify.getRefundNo(),
                        notify.getRefundId(),
                        notify.getSuccessTime(),
                        notify.getRawPlaintext()
                );
            } else if ("ABNORMAL".equals(notify.getRefundStatus()) || "CLOSED".equals(notify.getRefundStatus())) {
                paymentService.handleGatewayRefundFailed(notify.getRefundNo(), notify.getRawPlaintext());
            }
            return ResponseEntity.ok(Map.of("code", "SUCCESS", "message", "成功"));
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("code", "FAIL", "message", ex.getMessage()));
        }
    }

    private Map<String, String> extractParams(HttpServletRequest request) {
        Map<String, String> result = new HashMap<>();
        request.getParameterMap().forEach((key, values) -> {
            if (values != null && values.length > 0) {
                result.put(key, values[0]);
            }
        });
        return result;
    }

    private LocalDateTime parseAlipayTime(String value) {
        if (value == null || value.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(value, ALIPAY_TIME);
        } catch (Exception ex) {
            return LocalDateTime.now();
        }
    }
}
