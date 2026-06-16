package com.bfsm.controller;

import com.bfsm.common.ApiResponse;
import com.bfsm.dto.PaymentCreateReq;
import com.bfsm.dto.RechargeReq;
import com.bfsm.entity.PaymentRefund;
import com.bfsm.service.PaymentService;
import com.bfsm.vo.PaymentCapabilityVo;
import com.bfsm.vo.PaymentCreateVo;
import com.bfsm.vo.PaymentStatusVo;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/payments")
@PreAuthorize("hasAnyRole('USER','ADMIN')")
public class PaymentController {
    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/product-orders/{orderId}")
    public ApiResponse<PaymentCreateVo> createProductOrderPayment(@PathVariable Long orderId,
                                                                  @Valid @RequestBody PaymentCreateReq req) {
        return ApiResponse.ok(paymentService.createProductOrderPayment(orderId, req));
    }

    @PostMapping("/appointments/{appointmentId}")
    public ApiResponse<PaymentCreateVo> createAppointmentPayment(@PathVariable Long appointmentId,
                                                                 @Valid @RequestBody PaymentCreateReq req) {
        return ApiResponse.ok(paymentService.createAppointmentPayment(appointmentId, req));
    }

    @PostMapping("/wallet-recharges")
    public ApiResponse<PaymentCreateVo> createWalletRechargePayment(@Valid @RequestBody RechargeReq req) {
        return ApiResponse.ok(paymentService.createWalletRechargePayment(req));
    }

    @GetMapping("/{paymentNo}")
    public ApiResponse<PaymentStatusVo> status(@PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.status(paymentNo));
    }

    @GetMapping("/capabilities")
    public ApiResponse<PaymentCapabilityVo> capabilities() {
        return ApiResponse.ok(paymentService.capabilities());
    }

    @PostMapping("/{paymentNo}/complete")
    public ApiResponse<PaymentStatusVo> complete(@PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.completePayment(paymentNo));
    }

    @PostMapping("/{paymentNo}/cancel")
    public ApiResponse<PaymentStatusVo> cancel(@PathVariable String paymentNo) {
        return ApiResponse.ok(paymentService.cancelPayment(paymentNo));
    }

    @PostMapping("/refunds/{refundNo}/manual-wallet-complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PaymentRefund> completeManualRefund(@PathVariable String refundNo,
                                                           @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        return ApiResponse.ok(paymentService.completeManualRefundToWallet(refundNo, remark));
    }

    @PostMapping("/refunds/{refundNo}/mark-complete")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PaymentRefund> markManualRefundComplete(@PathVariable String refundNo,
                                                               @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        return ApiResponse.ok(paymentService.markManualRefundComplete(refundNo, remark));
    }

    @PostMapping("/refunds/{refundNo}/mark-failed")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiResponse<PaymentRefund> markManualRefundFailed(@PathVariable String refundNo,
                                                             @RequestBody(required = false) Map<String, String> body) {
        String remark = body == null ? null : body.get("remark");
        return ApiResponse.ok(paymentService.markManualRefundFailed(refundNo, remark));
    }
}
